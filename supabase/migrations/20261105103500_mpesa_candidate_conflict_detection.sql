-- Preserve repeated receipt candidates so conflicting sender numbers can be detected.
-- Serialize matching per business/receipt to close concurrent-insert races.
drop index if exists public.payment_notification_receipt_once_idx;

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

  if v_candidate_phone_count = 0 then
    return null;
  end if;

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

  update public.payment_ingress_events
    set payer_phone = coalesce(payer_phone, v_candidate_phone)
    where business_id = v_business_id
      and id = v_event_id;

  update public.payment_notification_candidates
    set match_status = 'matched',
        matched_ingress_event_id = v_event_id
    where business_id = v_business_id
      and receipt_candidate = v_receipt
      and amount_minor = v_amount_minor;

  return null;
end;
$$;

revoke all on function private.reconcile_payment_notification_candidate()
  from public, anon, authenticated;
