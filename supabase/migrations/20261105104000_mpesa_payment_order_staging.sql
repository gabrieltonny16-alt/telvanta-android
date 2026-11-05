-- Stage a tenant-safe payment/order record only after an exact C2B receipt + amount
-- and a complete Kenyan payer number are available. Staged orders remain REVIEW_REQUIRED.
-- This migration does not mark payments verified, queue orders, or execute USSD.

create or replace function private.stage_mpesa_payment_and_order(
  p_business_id uuid,
  p_receipt text
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_event public.payment_ingress_events%rowtype;
  v_payment public.payments%rowtype;
  v_rule record;
  v_customer_id uuid;
  v_order_id uuid;
  v_inserted_order_id uuid;
  v_order_status text;
  v_amount_kes integer;
  v_evidence_status text;
  v_existing_offer_id uuid;
  v_existing_recipient text;
  v_existing_customer_price integer;
begin
  perform pg_advisory_xact_lock(
    hashtextextended(p_business_id::text || ':' || p_receipt, 0)
  );

  select e.* into v_event
  from public.payment_ingress_events e
  where e.business_id = p_business_id
    and e.transaction_ref = p_receipt
  for update;

  if not found or v_event.provider <> 'safaricom_c2b' then
    return;
  end if;

  if v_event.verification_status in ('ambiguous','rejected') then
    update public.payments
      set verification_status = v_event.verification_status
      where business_id = p_business_id and receipt_code = p_receipt;

    update public.orders o
      set status = 'RECONCILIATION_REQUIRED'
      where o.business_id = p_business_id
        and o.id = v_event.matched_order_id
        and o.status in ('REVIEW_REQUIRED','PAID');
    return;
  end if;

  -- The legacy payments table stores whole KES only. Keep sub-KES receipts in
  -- payment_ingress_events for manual review rather than rounding them.
  if v_event.amount_minor <= 0 or v_event.amount_minor % 100 <> 0 then
    return;
  end if;

  v_amount_kes := (v_event.amount_minor / 100)::integer;

  if v_event.payer_phone is not null
     and v_event.payer_phone !~ '^254[17][0-9]{8}$' then
    return;
  end if;

  select case when exists (
    select 1
    from public.payment_notification_candidates c
    where c.business_id = p_business_id
      and c.matched_ingress_event_id = v_event.id
      and c.match_status = 'matched'
  ) then 'matched' else 'unverified' end
  into v_evidence_status;

  insert into public.payments (
    business_id, order_id, receipt_code, amount_kes, payer_phone,
    recipient_phone, evidence_source, verification_status, received_at
  ) values (
    p_business_id, null, p_receipt, v_amount_kes, v_event.payer_phone,
    v_event.payer_phone, 'provider_callback', v_evidence_status, v_event.occurred_at
  )
  on conflict (business_id, receipt_code) do nothing;

  select p.* into v_payment
  from public.payments p
  where p.business_id = p_business_id
    and p.receipt_code = p_receipt
  for update;

  if not found then
    return;
  end if;

  if v_payment.amount_kes <> v_amount_kes
     or (
       v_payment.payer_phone is not null
       and v_event.payer_phone is not null
       and v_payment.payer_phone <> v_event.payer_phone
     ) then
    update public.payment_ingress_events
      set verification_status = 'ambiguous'
      where business_id = p_business_id and id = v_event.id
        and verification_status <> 'verified';
    update public.payments
      set verification_status = 'ambiguous'
      where business_id = p_business_id and id = v_payment.id;
    return;
  end if;

  update public.payments
    set payer_phone = coalesce(payer_phone, v_event.payer_phone),
        recipient_phone = coalesce(recipient_phone, v_event.payer_phone),
        verification_status = case
          when verification_status = 'verified' then 'verified'
          when v_evidence_status = 'matched' then 'matched'
          else verification_status
        end
    where business_id = p_business_id and id = v_payment.id;

  -- No sender number means no recipient can be determined. Keep the payment
  -- unallocated and do not fabricate a phone number or order.
  if v_event.payer_phone is null then
    return;
  end if;

  select r.offer_id, r.amount_minor, o.provider_cost_kes
    into v_rule
  from public.payment_offer_rules r
  join public.offers o
    on o.business_id = r.business_id and o.id = r.offer_id
  where r.business_id = p_business_id
    and r.amount_minor = v_event.amount_minor
    and r.is_enabled = true
    and r.menu_flow_verified_at is not null
    and jsonb_array_length(r.menu_steps) > 0
    and o.is_active = true
    and (o.valid_from is null or o.valid_from <= now())
    and (o.valid_until is null or o.valid_until > now())
    and (o.stale_after is null or o.stale_after > now())
  limit 1;

  if not found then
    -- No enabled, current and uniquely-priced offer rule: retain an unmatched
    -- payment record, but do not guess which bundle the customer intended.
    return;
  end if;

  insert into public.customers (business_id, phone_e164)
  values (p_business_id, v_event.payer_phone)
  on conflict (business_id, phone_e164)
  do update set updated_at = now()
  returning id into v_customer_id;

  v_order_status := 'REVIEW_REQUIRED';

  insert into public.orders (
    business_id, customer_id, offer_id, channel,
    recipient_phone, payer_phone, status, currency,
    provider_cost_kes, selling_price_kes, customer_price_kes, idempotency_key
  ) values (
    p_business_id, v_customer_id, v_rule.offer_id, 'android',
    v_event.payer_phone, v_event.payer_phone, v_order_status, 'KES',
    v_rule.provider_cost_kes, v_amount_kes, v_amount_kes,
    'mpesa:' || p_receipt
  )
  on conflict (business_id, idempotency_key) do nothing
  returning id into v_inserted_order_id;

  v_order_id := v_inserted_order_id;
  if v_order_id is null then
    select o.id, o.offer_id, o.recipient_phone, o.customer_price_kes
      into v_order_id, v_existing_offer_id, v_existing_recipient, v_existing_customer_price
    from public.orders o
    where o.business_id = p_business_id
      and o.idempotency_key = 'mpesa:' || p_receipt
    for update;

    if v_order_id is null
       or v_existing_offer_id is distinct from v_rule.offer_id
       or v_existing_recipient is distinct from v_event.payer_phone
       or v_existing_customer_price is distinct from v_amount_kes then
      update public.payment_ingress_events
        set verification_status = 'ambiguous'
        where business_id = p_business_id and id = v_event.id
          and verification_status <> 'verified';
      update public.payments
        set verification_status = 'ambiguous'
        where business_id = p_business_id and id = v_payment.id;
      return;
    end if;
  else
    insert into public.order_events (
      business_id, order_id, from_status, to_status,
      actor_user_id, event_source, details
    ) values (
      p_business_id, v_order_id, null, v_order_status,
      null, 'mpesa_match',
      jsonb_build_object('receipt', p_receipt, 'offer_id', v_rule.offer_id)
    );
  end if;

  if v_payment.order_id is not null and v_payment.order_id <> v_order_id then
    update public.payment_ingress_events
      set verification_status = 'ambiguous'
      where business_id = p_business_id and id = v_event.id
        and verification_status <> 'verified';
    update public.payments
      set verification_status = 'ambiguous'
      where business_id = p_business_id and id = v_payment.id;
    update public.orders
      set status = 'RECONCILIATION_REQUIRED'
      where business_id = p_business_id
        and id in (v_payment.order_id, v_order_id)
        and status in ('REVIEW_REQUIRED','PAID');
    return;
  end if;

  update public.payments
    set order_id = coalesce(order_id, v_order_id)
    where business_id = p_business_id and id = v_payment.id;

  update public.payment_ingress_events
    set matched_order_id = coalesce(matched_order_id, v_order_id)
    where business_id = p_business_id and id = v_event.id;
end;
$$;

revoke all on function private.stage_mpesa_payment_and_order(uuid,text)
  from public, anon, authenticated;

-- Reconciliation: keep multiple receipt candidates so conflicting full numbers
-- are visible. A transaction-scoped advisory lock serializes callback/candidate
-- inserts for the same business+receipt to prevent missed matches.
create or replace function private.reconcile_payment_notification_candidate()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_business_id uuid;
  v_receipt text;
  v_amount_minor bigint;
  v_event_id uuid;
  v_event_phone text;
  v_candidate_phone text;
  v_candidate_phone_count integer;
begin
  if tg_table_name = 'payment_notification_candidates' then
    v_business_id := new.business_id;
    v_receipt := new.receipt_candidate;
    v_amount_minor := new.amount_minor;
  else
    v_business_id := new.business_id;
    v_receipt := new.transaction_ref;
    v_amount_minor := new.amount_minor;
  end if;

  if v_receipt is null or v_amount_minor is null then
    return null;
  end if;

  perform pg_advisory_xact_lock(
    hashtextextended(v_business_id::text || ':' || v_receipt, 0)
  );

  select e.id, e.payer_phone
    into v_event_id, v_event_phone
  from public.payment_ingress_events e
  where e.business_id = v_business_id
    and e.transaction_ref = v_receipt
    and e.amount_minor = v_amount_minor
  limit 1;

  if v_event_id is null then
    return null;
  end if;

  select count(distinct c.sender_phone_candidate),
         min(c.sender_phone_candidate)
    into v_candidate_phone_count, v_candidate_phone
  from public.payment_notification_candidates c
  where c.business_id = v_business_id
    and c.receipt_candidate = v_receipt
    and c.amount_minor = v_amount_minor
    and c.sender_phone_candidate is not null;

  if v_candidate_phone_count > 1
     or (
       v_event_phone is not null
       and v_candidate_phone is not null
       and v_event_phone <> v_candidate_phone
     ) then
    update public.payment_ingress_events
      set verification_status = 'ambiguous'
      where business_id = v_business_id
        and id = v_event_id
        and verification_status <> 'verified';

    update public.payment_notification_candidates
      set match_status = 'mismatch',
          matched_ingress_event_id = v_event_id
      where business_id = v_business_id
        and receipt_candidate = v_receipt
        and amount_minor = v_amount_minor;

    return null;
  end if;

  if v_event_phone is null and v_candidate_phone is null then
    return null;
  end if;

  update public.payment_notification_candidates
    set match_status = 'matched',
        matched_ingress_event_id = v_event_id
    where business_id = v_business_id
      and receipt_candidate = v_receipt
      and amount_minor = v_amount_minor;

  update public.payment_ingress_events
    set payer_phone = coalesce(payer_phone, v_candidate_phone)
    where business_id = v_business_id
      and id = v_event_id;

  return null;
end;
$$;

revoke all on function private.reconcile_payment_notification_candidate()
  from public, anon, authenticated;

-- Run order/payment staging when a trusted C2B callback is received or when
-- the local notification supplies the complete sender number. The order is
-- REVIEW_REQUIRED only; it is never QUEUED by this function.
create or replace function private.handle_mpesa_payment_ingress_stage()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  perform private.stage_mpesa_payment_and_order(new.business_id, new.transaction_ref);
  return null;
end;
$$;
revoke all on function private.handle_mpesa_payment_ingress_stage()
  from public, anon, authenticated;

drop trigger if exists payment_ingress_event_stage_order on public.payment_ingress_events;
create trigger payment_ingress_event_stage_order
after insert or update of payer_phone, verification_status
on public.payment_ingress_events
for each row
execute function private.handle_mpesa_payment_ingress_stage();

-- If an owner later enables a verified, unique amount-to-offer rule, retry
-- staging matching C2B receipts already recorded for that business.
create or replace function private.handle_mpesa_offer_rule_stage()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_receipt record;
begin
  if new.is_enabled <> true or new.menu_flow_verified_at is null then
    return null;
  end if;

  for v_receipt in
    select e.transaction_ref
    from public.payment_ingress_events e
    where e.business_id = new.business_id
      and e.amount_minor = new.amount_minor
      and e.provider = 'safaricom_c2b'
      and e.verification_status not in ('ambiguous','rejected')
  loop
    perform private.stage_mpesa_payment_and_order(new.business_id, v_receipt.transaction_ref);
  end loop;

  return null;
end;
$$;
revoke all on function private.handle_mpesa_offer_rule_stage()
  from public, anon, authenticated;

drop trigger if exists payment_offer_rule_stage_existing_payments on public.payment_offer_rules;
create trigger payment_offer_rule_stage_existing_payments
after insert or update of is_enabled, menu_flow_verified_at, offer_id, amount_minor
on public.payment_offer_rules
for each row
execute function private.handle_mpesa_offer_rule_stage();
