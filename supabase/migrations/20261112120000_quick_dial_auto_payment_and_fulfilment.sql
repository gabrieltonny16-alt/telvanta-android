-- Quick Dial creates a recipient-specific pending order before opening the carrier dialler.
-- A complete, unique M-PESA notification can then match that order and mark it PAID.
-- Ambiguous or unmatched notifications remain in the existing review/reconciliation inbox.

alter table public.businesses
  alter column fulfilment_enabled set default true,
  alter column fulfilment_paused set default false;

update public.businesses
set fulfilment_enabled = true,
    fulfilment_paused = false,
    updated_at = now()
where fulfilment_enabled = false or fulfilment_paused = true;

create or replace function public.create_quick_dial_order(
  p_business_id uuid,
  p_offer_id uuid,
  p_recipient_phone text,
  p_amount_kes integer
) returns jsonb
language plpgsql
security definer
set search_path = public, private, pg_temp
as $$
declare
  v_user_id uuid := auth.uid();
  v_offer public.offers%rowtype;
  v_customer_id uuid;
  v_rule public.payment_offer_rules%rowtype;
  v_order_id uuid;
  v_amount_minor bigint;
begin
  if v_user_id is null then
    raise exception 'Authentication required' using errcode='42501';
  end if;
  if not private.has_business_role(
    p_business_id, array['owner','business_admin','agent_manager','agent']
  ) then
    raise exception 'Not authorised for this business' using errcode='42501';
  end if;
  if p_recipient_phone !~ '^\\+254[17][0-9]{8}$' then
    raise exception 'Enter a valid Kenyan recipient number' using errcode='22023';
  end if;
  if p_amount_kes < 1 then
    raise exception 'Offer amount must be positive' using errcode='22023';
  end if;
  v_amount_minor := p_amount_kes::bigint * 100;

  select o.* into v_offer
  from public.offers o
  join public.providers p on p.business_id=o.business_id and p.id=o.provider_id
  where o.business_id=p_business_id and o.id=p_offer_id
    and o.is_active and p.status='active' and p.adapter_key='ussd_assisted'
    and (o.valid_from is null or o.valid_from <= now())
    and (o.valid_until is null or o.valid_until > now())
    and (o.stale_after is null or o.stale_after > now())
  for share;
  if not found then
    raise exception 'Selected offer is not active or its provider is unavailable' using errcode='P0001';
  end if;

  select r.* into v_rule
  from public.payment_offer_rules r
  where r.business_id=p_business_id and r.offer_id=p_offer_id
    and r.amount_minor=v_amount_minor and r.is_enabled
    and r.menu_flow_verified_at is not null
    and jsonb_array_length(r.menu_steps)>0;
  if not found then
    raise exception 'This offer has no enabled, verified payment and USSD rule for that exact price' using errcode='P0001';
  end if;

  insert into public.customers(business_id,phone_e164,created_by)
  values(p_business_id,p_recipient_phone,v_user_id)
  on conflict(business_id,phone_e164) do update set updated_at=now()
  returning id into v_customer_id;

  insert into public.orders(
    business_id,agent_user_id,customer_id,offer_id,channel,recipient_phone,payer_phone,
    status,currency,provider_cost_kes,selling_price_kes,customer_price_kes,idempotency_key
  ) values(
    p_business_id,v_user_id,v_customer_id,p_offer_id,'android',p_recipient_phone,null,
    'AWAITING_PAYMENT','KES',v_offer.provider_cost_kes,p_amount_kes,p_amount_kes,
    'quickdial:' || gen_random_uuid()::text
  ) returning id into v_order_id;

  insert into public.order_events(
    business_id,order_id,from_status,to_status,actor_user_id,event_source,details
  ) values(
    p_business_id,v_order_id,null,'AWAITING_PAYMENT',v_user_id,'quick_dial',
    jsonb_build_object('offer_id',p_offer_id,'recipient_phone',p_recipient_phone,'amount_kes',p_amount_kes)
  );

  insert into public.audit_logs(
    business_id,actor_user_id,action,entity_type,entity_id,details
  ) values(
    p_business_id,v_user_id,'QUICK_DIAL_ORDER_CREATED','order',v_order_id,
    jsonb_build_object('offer_id',p_offer_id,'amount_kes',p_amount_kes)
  );

  return jsonb_build_object('order_id',v_order_id,'amount_kes',p_amount_kes,'status','AWAITING_PAYMENT');
end;
$$;

revoke all on function public.create_quick_dial_order(uuid,uuid,text,integer) from public,anon;
grant execute on function public.create_quick_dial_order(uuid,uuid,text,integer) to authenticated;

create or replace function private.auto_match_mpesa_notification_to_quick_dial_order()
returns trigger
language plpgsql
security definer
set search_path = public, private, pg_temp
as $$
declare
  v_candidate public.payment_notification_candidates%rowtype;
  v_payment public.payments%rowtype;
  v_target_order public.orders%rowtype;
  v_match_count integer;
begin
  select c.* into v_candidate
  from public.payment_notification_candidates c
  where c.id=new.id;

  if not found or v_candidate.staging_status <> 'staged_for_review'
     or v_candidate.staged_payment_id is null
     or v_candidate.staged_order_id is null
     or v_candidate.candidate_status <> 'REVIEW_READY'
     or v_candidate.match_status = 'mismatch'
     or v_candidate.receipt_candidate is null
     or v_candidate.amount_minor is null
     or v_candidate.sender_phone_candidate is null
     or v_candidate.amount_minor % 100 <> 0 then
    return null;
  end if;

  -- Match only one recent, unpaid Quick Dial order at the exact amount.
  -- The payer may differ from the recipient; both are stored separately.
  select count(*) into v_match_count
  from public.orders o
  where o.business_id=new.business_id
    and o.channel='android'
    and o.status='AWAITING_PAYMENT'
    and o.customer_price_kes=(v_candidate.amount_minor/100)::integer
    and o.created_at >= coalesce(v_candidate.notification_posted_at,now()) - interval '30 minutes'
    and o.created_at <= coalesce(v_candidate.notification_posted_at,now()) + interval '2 minutes';

  if v_match_count <> 1 then
    return null;
  end if;

  select o.* into v_target_order
  from public.orders o
  where o.business_id=new.business_id
    and o.channel='android'
    and o.status='AWAITING_PAYMENT'
    and o.customer_price_kes=(v_candidate.amount_minor/100)::integer
    and o.created_at >= coalesce(v_candidate.notification_posted_at,now()) - interval '30 minutes'
    and o.created_at <= coalesce(v_candidate.notification_posted_at,now()) + interval '2 minutes'
  for update;

  select p.* into v_payment
  from public.payments p
  where p.id=v_candidate.staged_payment_id
    and p.business_id=new.business_id
  for update;

  if not found or v_payment.receipt_code <> v_candidate.receipt_candidate
     or v_payment.amount_kes <> (v_candidate.amount_minor/100)::integer
     or v_payment.payer_phone is distinct from v_candidate.sender_phone_candidate
     or v_payment.verification_status in ('rejected','ambiguous') then
    return null;
  end if;

  if v_payment.order_id is not null and v_payment.order_id <> v_target_order.id then
    -- The staging trigger's provisional order is not a fulfilment order.
    if v_payment.order_id <> v_candidate.staged_order_id then
      return null;
    end if;
  end if;

  update public.orders
  set status='PAID', updated_at=now()
  where id=v_target_order.id and business_id=new.business_id and status='AWAITING_PAYMENT';

  if not found then return null; end if;

  update public.orders
  set status='CANCELLED', updated_at=now()
  where id=v_candidate.staged_order_id
    and business_id=new.business_id
    and id <> v_target_order.id
    and status='REVIEW_REQUIRED';

  update public.payments
  set order_id=v_target_order.id,
      payer_phone=v_candidate.sender_phone_candidate,
      recipient_phone=v_target_order.recipient_phone,
      verification_status='verified'
  where id=v_payment.id and business_id=new.business_id;

  update public.payment_notification_candidates
  set staging_status='verified',
      staging_reason='M-PESA receipt, amount and payer matched one recent Quick Dial order automatically.',
      staged_order_id=v_target_order.id
  where id=new.id;

  insert into public.order_events(
    business_id,order_id,from_status,to_status,actor_user_id,event_source,details
  ) values(
    new.business_id,v_target_order.id,'AWAITING_PAYMENT','PAID',
    v_candidate.source_user_id,'mpesa_notification_auto_match',
    jsonb_build_object('receipt',v_candidate.receipt_candidate,'payer_phone',v_candidate.sender_phone_candidate,
      'recipient_phone',v_target_order.recipient_phone,'amount_minor',v_candidate.amount_minor)
  );

  insert into public.audit_logs(
    business_id,actor_user_id,action,entity_type,entity_id,details
  ) values(
    new.business_id,v_candidate.source_user_id,'MPESA_PAYMENT_AUTO_MATCHED','order',v_target_order.id,
    jsonb_build_object('candidate_id',new.id,'payment_id',v_payment.id,'receipt',v_candidate.receipt_candidate)
  );

  return null;
end;
$$;

revoke all on function private.auto_match_mpesa_notification_to_quick_dial_order()
  from public,anon,authenticated;

drop trigger if exists zz_payment_notification_quick_dial_auto_match
  on public.payment_notification_candidates;
create trigger zz_payment_notification_quick_dial_auto_match
after insert or update of staging_status on public.payment_notification_candidates
for each row execute function private.auto_match_mpesa_notification_to_quick_dial_order();


-- Manual "verify payment" is not a supported path. Valid notification evidence is
-- matched automatically to a unique Quick Dial order; all other candidates remain held.
alter function public.review_mpesa_notification_candidate(uuid,uuid,text)
  rename to review_mpesa_notification_candidate_legacy;

revoke all on function public.review_mpesa_notification_candidate_legacy(uuid,uuid,text)
  from public,anon,authenticated;

create or replace function public.review_mpesa_notification_candidate(
  p_business_id uuid,
  p_candidate_id uuid,
  p_decision text
) returns table(candidate_id uuid, order_id uuid, payment_status text, order_status text)
language plpgsql
security definer
set search_path = public, private, pg_temp
as $$
begin
  if p_decision <> 'reject' then
    raise exception 'Payments are verified automatically from a valid, uniquely matched Till notification; manual verification is disabled.'
      using errcode='42501';
  end if;
  return query
    select * from public.review_mpesa_notification_candidate_legacy(
      p_business_id,p_candidate_id,'reject'
    );
end;
$$;

revoke all on function public.review_mpesa_notification_candidate(uuid,uuid,text)
  from public,anon;
grant execute on function public.review_mpesa_notification_candidate(uuid,uuid,text)
  to authenticated;
