-- Turn complete local M-PESA notification candidates into reviewable payment/order records.
-- IMPORTANT: a notification is evidence only. This migration never verifies a payment,
-- queues an order, or executes USSD. A manager must reconcile it against the Till record.

alter table public.payment_notification_candidates
  add column if not exists staging_status text not null default 'not_staged',
  add column if not exists staging_reason text,
  add column if not exists staged_payment_id uuid references public.payments(id) on delete restrict,
  add column if not exists staged_order_id uuid references public.orders(id) on delete restrict;

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.payment_notification_candidates'::regclass
      and conname = 'payment_notification_candidates_staging_status_check'
  ) then
    alter table public.payment_notification_candidates
      add constraint payment_notification_candidates_staging_status_check
      check (staging_status in ('not_staged','staged_for_review','blocked','verified','rejected'));
  end if;
end;
$$;

create index if not exists payment_notification_candidates_staging_review_idx
  on public.payment_notification_candidates(business_id, notification_posted_at desc)
  where staging_status in ('staged_for_review','blocked');

create unique index if not exists payment_notification_candidates_staged_order_once
  on public.payment_notification_candidates(staged_order_id)
  where staged_order_id is not null;

create or replace function private.stage_mpesa_notification_candidate()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_active_till_count integer;
  v_rule record;
  v_customer_id uuid;
  v_payment public.payments%rowtype;
  v_order public.orders%rowtype;
  v_order_id uuid;
  v_payment_id uuid;
  v_amount_kes integer;
  v_inserted_order_id uuid;
begin
  if new.staging_status <> 'not_staged' then
    return null;
  end if;

  if new.candidate_status <> 'REVIEW_READY'
     or new.match_status = 'mismatch'
     or new.receipt_candidate is null
     or new.amount_minor is null
     or new.sender_phone_candidate is null then
    update public.payment_notification_candidates
      set staging_status = 'blocked',
          staging_reason = 'Notification is incomplete or conflicts with another receipt candidate.'
      where id = new.id;
    return null;
  end if;

  if new.amount_minor % 100 <> 0 then
    update public.payment_notification_candidates
      set staging_status = 'blocked',
          staging_reason = 'Amount is not a whole KES value supported by the offer rules.'
      where id = new.id;
    return null;
  end if;

  -- Only stage automatically when the business has one unambiguous active Till.
  select count(*) into v_active_till_count
  from public.payment_ingress_channels c
  where c.business_id = new.business_id
    and c.channel_type = 'till'
    and c.is_active = true;

  if v_active_till_count <> 1 then
    update public.payment_notification_candidates
      set staging_status = 'blocked',
          staging_reason = 'Configure exactly one active Lipa na M-PESA Till for this business.'
      where id = new.id;
    return null;
  end if;

  -- Require an explicit, enabled, verified amount-to-offer mapping and active provider.
  select r.offer_id, r.amount_minor, o.provider_cost_kes, o.provider_id
    into v_rule
  from public.payment_offer_rules r
  join public.offers o
    on o.business_id = r.business_id and o.id = r.offer_id
  join public.providers p
    on p.business_id = o.business_id and p.id = o.provider_id
  where r.business_id = new.business_id
    and r.amount_minor = new.amount_minor
    and r.is_enabled = true
    and r.menu_flow_verified_at is not null
    and jsonb_array_length(r.menu_steps) > 0
    and o.is_active = true
    and p.status = 'active'
    and (o.valid_from is null or o.valid_from <= now())
    and (o.valid_until is null or o.valid_until > now())
    and (o.stale_after is null or o.stale_after > now())
  limit 1;

  if not found then
    update public.payment_notification_candidates
      set staging_status = 'blocked',
          staging_reason = 'No active provider and verified offer rule matches this exact payment amount.'
      where id = new.id;
    return null;
  end if;

  v_amount_kes := (new.amount_minor / 100)::integer;

  perform pg_advisory_xact_lock(
    hashtextextended(new.business_id::text || ':' || new.receipt_candidate, 0)
  );

  insert into public.customers (business_id, phone_e164, display_name, created_by)
  values (
    new.business_id,
    new.sender_phone_candidate,
    nullif(trim(coalesce(new.sender_name_candidate, '')), ''),
    new.source_user_id
  )
  on conflict (business_id, phone_e164)
  do update set
    display_name = coalesce(public.customers.display_name, excluded.display_name),
    updated_at = now()
  returning id into v_customer_id;

  -- Preserve an existing callback/manual payment record; never upgrade its status here.
  insert into public.payments (
    business_id, order_id, receipt_code, amount_kes, payer_phone,
    recipient_phone, evidence_source, verification_status, received_at
  ) values (
    new.business_id, null, new.receipt_candidate, v_amount_kes,
    new.sender_phone_candidate, new.sender_phone_candidate,
    'mpesa_notification', 'unverified', new.notification_posted_at
  )
  on conflict (business_id, receipt_code) do nothing;

  select p.* into v_payment
  from public.payments p
  where p.business_id = new.business_id
    and p.receipt_code = new.receipt_candidate
  for update;

  if not found
     or v_payment.amount_kes <> v_amount_kes
     or (v_payment.payer_phone is not null
         and v_payment.payer_phone <> new.sender_phone_candidate) then
    update public.payment_notification_candidates
      set staging_status = 'blocked',
          staging_reason = 'Receipt already exists with a different amount or payer. Manual reconciliation required.'
      where id = new.id;
    return null;
  end if;

  v_payment_id := v_payment.id;

  -- Idempotent order creation: a receipt can produce at most one order per business.
  insert into public.orders (
    business_id, customer_id, offer_id, channel, recipient_phone, payer_phone,
    status, currency, provider_cost_kes, selling_price_kes, customer_price_kes,
    idempotency_key
  ) values (
    new.business_id, v_customer_id, v_rule.offer_id, 'android',
    new.sender_phone_candidate, new.sender_phone_candidate,
    'REVIEW_REQUIRED', 'KES', v_rule.provider_cost_kes,
    v_amount_kes, v_amount_kes, 'mpesa:' || new.receipt_candidate
  )
  on conflict (business_id, idempotency_key) do nothing
  returning id into v_inserted_order_id;

  if v_inserted_order_id is not null then
    v_order_id := v_inserted_order_id;
    insert into public.order_events (
      business_id, order_id, from_status, to_status, actor_user_id, event_source, details
    ) values (
      new.business_id, v_order_id, null, 'REVIEW_REQUIRED',
      new.source_user_id, 'mpesa_notification_candidate',
      jsonb_build_object('receipt', new.receipt_candidate, 'amount_minor', new.amount_minor,
                         'offer_id', v_rule.offer_id, 'payment_verified', false)
    );
  else
    select o.* into v_order
    from public.orders o
    where o.business_id = new.business_id
      and o.idempotency_key = 'mpesa:' || new.receipt_candidate
    for update;

    if not found
       or v_order.offer_id is distinct from v_rule.offer_id
       or v_order.recipient_phone is distinct from new.sender_phone_candidate
       or v_order.customer_price_kes is distinct from v_amount_kes then
      update public.payment_notification_candidates
        set staging_status = 'blocked',
            staging_reason = 'An existing order for this receipt does not match the detected offer or recipient.'
        where id = new.id;
      return null;
    end if;
    v_order_id := v_order.id;
  end if;

  if v_payment.order_id is not null and v_payment.order_id <> v_order_id then
    update public.payment_notification_candidates
      set staging_status = 'blocked',
          staging_reason = 'The payment is already linked to a different order. Manual reconciliation required.'
      where id = new.id;
    return null;
  end if;

  update public.payments
    set order_id = coalesce(order_id, v_order_id)
  where id = v_payment_id;

  update public.payment_notification_candidates
    set staging_status = 'staged_for_review',
        staging_reason = 'Order staged. Verify the receipt against the Till transaction record before fulfilment.',
        staged_payment_id = v_payment_id,
        staged_order_id = v_order_id
  where id = new.id;

  insert into public.audit_logs (
    business_id, actor_user_id, action, entity_type, entity_id, details
  ) values (
    new.business_id, new.source_user_id, 'MPESA_PAYMENT_STAGED_FOR_REVIEW',
    'order', v_order_id,
    jsonb_build_object('receipt', new.receipt_candidate, 'payment_verified', false,
                       'source', 'android_notification')
  );

  return null;
end;
$$;

revoke all on function private.stage_mpesa_notification_candidate()
  from public, anon, authenticated;

drop trigger if exists payment_notification_candidate_stage_order
  on public.payment_notification_candidates;

create trigger payment_notification_candidate_stage_order
after insert on public.payment_notification_candidates
for each row execute function private.stage_mpesa_notification_candidate();

-- A manager must independently reconcile the candidate against the Till record.
-- Verification changes the order to PAID only; dispatch/USSD is a separate worker step.
create or replace function public.review_mpesa_notification_candidate(
  p_business_id uuid,
  p_candidate_id uuid,
  p_decision text
)
returns table(candidate_id uuid, order_id uuid, payment_status text, order_status text)
language plpgsql
security definer
set search_path = public, private, pg_temp
as $$
declare
  v_candidate public.payment_notification_candidates%rowtype;
  v_payment public.payments%rowtype;
  v_order public.orders%rowtype;
begin
  if p_decision not in ('verify','reject') then
    raise exception 'Decision must be verify or reject' using errcode = '22023';
  end if;

  if not private.has_business_role(
    p_business_id, array['owner','business_admin','agent_manager']
  ) then
    raise exception 'Only an authorized business manager can review Till payments'
      using errcode = '42501';
  end if;

  select c.* into v_candidate
  from public.payment_notification_candidates c
  where c.id = p_candidate_id
    and c.business_id = p_business_id
  for update;

  if not found or v_candidate.staging_status not in ('staged_for_review','verified','rejected') then
    raise exception 'Candidate is not ready for payment review' using errcode = 'P0001';
  end if;

  if v_candidate.staged_payment_id is null or v_candidate.staged_order_id is null then
    raise exception 'Candidate has no staged payment/order' using errcode = 'P0001';
  end if;

  select p.* into v_payment
  from public.payments p
  where p.id = v_candidate.staged_payment_id
    and p.business_id = p_business_id
  for update;

  select o.* into v_order
  from public.orders o
  where o.id = v_candidate.staged_order_id
    and o.business_id = p_business_id
  for update;

  if not found then
    raise exception 'Staged order was not found' using errcode = 'P0001';
  end if;

  if p_decision = 'verify' then
    if v_candidate.staging_status = 'rejected'
       or v_payment.verification_status in ('rejected','ambiguous')
       or v_order.status in ('RECONCILIATION_REQUIRED','CANCELLED','FAILED','UNKNOWN') then
      raise exception 'This payment/order requires reconciliation and cannot be auto-approved'
        using errcode = 'P0001';
    end if;

    update public.payments
      set verification_status = 'verified'
      where id = v_payment.id;

    if v_order.status = 'REVIEW_REQUIRED' then
      update public.orders set status = 'PAID' where id = v_order.id;
      v_order.status := 'PAID';
    elsif v_order.status <> 'PAID' then
      raise exception 'Order is not in a reviewable payment state' using errcode = 'P0001';
    end if;

    update public.payment_notification_candidates
      set staging_status = 'verified',
          staging_reason = 'Manager verified against the Till transaction record.'
      where id = v_candidate.id;

    insert into public.audit_logs (
      business_id, actor_user_id, action, entity_type, entity_id, details
    ) values (
      p_business_id, auth.uid(), 'MPESA_PAYMENT_VERIFIED_BY_MANAGER',
      'order', v_order.id,
      jsonb_build_object('receipt', v_candidate.receipt_candidate,
                         'candidate_id', v_candidate.id)
    );

    return query select v_candidate.id, v_order.id, 'verified'::text, v_order.status;
  else
    if v_payment.verification_status = 'verified' then
      raise exception 'A verified payment cannot be rejected by this action; use reconciliation'
        using errcode = 'P0001';
    end if;

    update public.payments
      set verification_status = 'rejected'
      where id = v_payment.id;

    if v_order.status = 'REVIEW_REQUIRED' or v_order.status = 'PAID' then
      update public.orders set status = 'RECONCILIATION_REQUIRED' where id = v_order.id;
      v_order.status := 'RECONCILIATION_REQUIRED';
    end if;

    update public.payment_notification_candidates
      set staging_status = 'rejected',
          staging_reason = 'Manager rejected this notification candidate after review.'
      where id = v_candidate.id;

    insert into public.audit_logs (
      business_id, actor_user_id, action, entity_type, entity_id, details
    ) values (
      p_business_id, auth.uid(), 'MPESA_PAYMENT_CANDIDATE_REJECTED',
      'order', v_order.id,
      jsonb_build_object('receipt', v_candidate.receipt_candidate,
                         'candidate_id', v_candidate.id)
    );

    return query select v_candidate.id, v_order.id, 'rejected'::text, v_order.status;
  end if;
end;
$$;

revoke all on function public.review_mpesa_notification_candidate(uuid,uuid,text)
  from public, anon;
grant execute on function public.review_mpesa_notification_candidate(uuid,uuid,text)
  to authenticated;
