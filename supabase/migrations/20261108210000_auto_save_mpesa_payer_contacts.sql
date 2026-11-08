-- Automatically add M-PESA payers to the business customer book.
-- Phone number is the unique identity; a different payer name never overwrites
-- an existing contact name. The parsed name remains on the payment candidate.
alter table public.payment_notification_candidates
  add column if not exists sender_contact_status text not null default 'not_processed'
  check (sender_contact_status in ('not_processed', 'saved', 'name_conflict', 'not_eligible'));

create or replace function public.save_mpesa_payer_contact_from_candidate()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
  v_phone_e164 text;
  v_sender_name text;
  v_customer_id uuid;
begin
  v_sender_name := nullif(
    left(regexp_replace(btrim(coalesce(new.sender_name_candidate, '')), '[[:space:]]+', ' ', 'g'), 120),
    ''
  );

  if new.candidate_status <> 'REVIEW_READY'
     or new.receipt_candidate is null
     or new.amount_minor is null
     or new.sender_phone_candidate is null
     or v_sender_name is null
     or new.sender_phone_candidate !~ '^254[17][0-9]{8}
    update public.payment_notification_candidates
       set sender_contact_status = 'not_eligible'
     where id = new.id;
    return new;
  end if;

  -- Notification parser stores Kenyan MSISDNs as 2547XXXXXXXX/2541XXXXXXXX.
  -- Customer records use E.164, e.g. +2547XXXXXXXX.
  v_phone_e164 := '+' || new.sender_phone_candidate;

  insert into public.customers (
    business_id, phone_e164, display_name, created_by
  ) values (
    new.business_id, v_phone_e164, v_sender_name, new.source_user_id
  )
  on conflict (business_id, phone_e164)
  do update set
    display_name = coalesce(
      nullif(btrim(public.customers.display_name), ''),
      excluded.display_name
    ),
    updated_at = now()
  where public.customers.display_name is null
     or btrim(public.customers.display_name) = ''
     or lower(btrim(public.customers.display_name)) = lower(btrim(excluded.display_name))
  returning id into v_customer_id;

  update public.payment_notification_candidates
     set sender_contact_status = case
       when v_customer_id is null then 'name_conflict'
       else 'saved'
     end
   where id = new.id;

  return new;
end;
$$;

drop trigger if exists trg_save_mpesa_payer_contact on public.payment_notification_candidates;
create trigger trg_save_mpesa_payer_contact
after insert on public.payment_notification_candidates
for each row
execute function public.save_mpesa_payer_contact_from_candidate();

comment on function public.save_mpesa_payer_contact_from_candidate() is
  'Creates or reuses a business customer contact from an inserted M-PESA payer candidate; preserves conflicting existing names and never verifies payment.';
 then
    update public.payment_notification_candidates
       set sender_contact_status = 'not_eligible'
     where id = new.id;
    return new;
  end if;

  -- Notification parser stores Kenyan MSISDNs as 2547XXXXXXXX/2541XXXXXXXX.
  -- Customer records use E.164, e.g. +2547XXXXXXXX.
  v_phone_e164 := '+' || new.sender_phone_candidate;

  insert into public.customers (
    business_id, phone_e164, display_name, created_by
  ) values (
    new.business_id, v_phone_e164, v_sender_name, new.source_user_id
  )
  on conflict (business_id, phone_e164)
  do update set
    display_name = coalesce(
      nullif(btrim(public.customers.display_name), ''),
      excluded.display_name
    ),
    updated_at = now()
  where public.customers.display_name is null
     or btrim(public.customers.display_name) = ''
     or lower(btrim(public.customers.display_name)) = lower(btrim(excluded.display_name))
  returning id into v_customer_id;

  update public.payment_notification_candidates
     set sender_contact_status = case
       when v_customer_id is null then 'name_conflict'
       else 'saved'
     end
   where id = new.id;

  return new;
end;
$$;

drop trigger if exists trg_save_mpesa_payer_contact on public.payment_notification_candidates;
create trigger trg_save_mpesa_payer_contact
after insert on public.payment_notification_candidates
for each row
execute function public.save_mpesa_payer_contact_from_candidate();

comment on function public.save_mpesa_payer_contact_from_candidate() is
  'Creates or reuses a business customer contact from an inserted M-PESA payer candidate; preserves conflicting existing names and never verifies payment.';
