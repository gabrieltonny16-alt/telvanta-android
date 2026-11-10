-- Match trusted Till receipts to the existing SiteLink order and preserve its recipient.
-- If multiple same-price orders are eligible, leave the payment for reconciliation.
CREATE OR REPLACE FUNCTION private.stage_mpesa_payment_and_order(p_business_id uuid, p_receipt text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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
  v_order_count integer;
  v_all_amount_order_count integer;
  v_order_recipient text;
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

  select case
    when v_event.source_kind = 'daraja_callback'
      and v_event.verification_status = 'verified' then 'verified'
    when exists (
      select 1
      from public.payment_notification_candidates c
      where c.business_id = p_business_id
        and c.matched_ingress_event_id = v_event.id
        and c.match_status = 'matched'
    ) then 'matched'
    else 'unverified'
  end
  into v_evidence_status;

  insert into public.payments (
    business_id, order_id, receipt_code, amount_kes, payer_phone,
    recipient_phone, evidence_source, verification_status, received_at
  ) values (
    p_business_id, null, p_receipt, v_amount_kes, v_event.payer_phone,
    null, 'provider_callback', v_evidence_status, v_event.occurred_at
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
        verification_status = case
          when verification_status = 'verified' then 'verified'
          when v_evidence_status = 'matched' then 'matched'
          else verification_status
        end
    where business_id = p_business_id and id = v_payment.id;

  -- SiteLink creates the order before the customer pays. Match the trusted
  -- Till receipt to one recent awaiting order by exact amount and, when the
  -- callback exposes it, exact payer phone. Never replace the chosen recipient.
  if v_payment.order_id is not null then
    return;
  end if;

  select
    count(*) filter (where v_event.payer_phone is null or o.payer_phone = v_event.payer_phone),
    count(*)
  into v_order_count, v_all_amount_order_count
  from public.orders o
  where o.business_id = p_business_id
    and o.channel = 'sitelink'
    and o.status = 'AWAITING_PAYMENT'
    and o.customer_price_kes = v_amount_kes
    and o.created_at >= v_event.occurred_at - interval '30 minutes'
    and o.created_at <= v_event.occurred_at + interval '5 minutes';

  if v_order_count = 1 then
    select o.id, o.offer_id, o.recipient_phone, o.customer_price_kes
      into v_order_id, v_existing_offer_id, v_order_recipient, v_existing_customer_price
    from public.orders o
    where o.business_id = p_business_id
      and o.channel = 'sitelink'
      and o.status = 'AWAITING_PAYMENT'
      and o.customer_price_kes = v_amount_kes
      and o.created_at >= v_event.occurred_at - interval '30 minutes'
      and o.created_at <= v_event.occurred_at + interval '5 minutes'
      and (v_event.payer_phone is null or o.payer_phone = v_event.payer_phone)
    order by o.created_at desc
    limit 1
    for update;

    if v_payment.order_id is not null and v_payment.order_id <> v_order_id then
      update public.payments set verification_status = 'ambiguous'
        where business_id = p_business_id and id = v_payment.id;
      return;
    end if;

    if v_event.matched_order_id is not null and v_event.matched_order_id <> v_order_id then
      update public.payments set verification_status = 'ambiguous'
        where business_id = p_business_id and id = v_payment.id;
      return;
    end if;

    v_order_status := case when v_evidence_status = 'verified' then 'PAID' else 'REVIEW_REQUIRED' end;

    update public.payments
      set order_id = v_order_id,
          payer_phone = coalesce(payer_phone, v_event.payer_phone),
          recipient_phone = v_order_recipient,
          verification_status = case
            when verification_status = 'verified' then 'verified'
            else v_evidence_status
          end
      where business_id = p_business_id and id = v_payment.id;

    update public.orders
      set status = v_order_status
      where business_id = p_business_id
        and id = v_order_id
        and status = 'AWAITING_PAYMENT';

    if found then
      insert into public.order_events (
        business_id, order_id, from_status, to_status,
        actor_user_id, event_source, details
      ) values (
        p_business_id, v_order_id, 'AWAITING_PAYMENT', v_order_status,
        null, 'mpesa_c2b_sitelink_match',
        jsonb_build_object('receipt', p_receipt, 'offer_id', v_existing_offer_id)
      );
    end if;

    update public.payment_ingress_events
      set matched_order_id = coalesce(matched_order_id, v_order_id)
      where business_id = p_business_id and id = v_event.id;
    return;
  end if;

  if v_all_amount_order_count > 0 then
    -- Same-price pending orders exist but the payer did not match (or more than
    -- one order is eligible). Keep the payment for manager reconciliation.
    update public.payments set verification_status = 'ambiguous'
      where business_id = p_business_id and id = v_payment.id;
    return;
  end if;

  -- Without a pre-created SiteLink order, a masked/hashed MSISDN cannot safely
  -- identify a recipient. Leave the verified receipt unallocated.
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

  v_order_status := case
    when v_event.source_kind = 'daraja_callback'
      and v_event.verification_status = 'verified' then 'PAID'
    else 'REVIEW_REQUIRED'
  end;

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
    set order_id = coalesce(order_id, v_order_id),
        recipient_phone = coalesce(recipient_phone, v_event.payer_phone)
    where business_id = p_business_id and id = v_payment.id;

  update public.payment_ingress_events
    set matched_order_id = coalesce(matched_order_id, v_order_id)
    where business_id = p_business_id and id = v_event.id;
end;
$function$
