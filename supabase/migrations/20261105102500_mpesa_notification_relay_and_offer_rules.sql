-- Persist Android-observed M-PESA notifications as unverified evidence.
-- A notification never marks a payment verified and never starts fulfilment.
-- Exact amount-to-offer rules are explicit and disabled until a menu flow is verified.

create table public.payment_notification_candidates (
  id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.businesses(id) on delete cascade,
  source_user_id uuid not null default auth.uid() references auth.users(id) on delete restrict,
  source_package text not null,
  notification_title text not null default '',
  raw_notification_text text not null,
  receipt_candidate text,
  amount_minor bigint,
  sender_phone_candidate text,
  notification_hash char(64) not null check (notification_hash ~ '^[0-9a-f]{64}$'),
  candidate_status text not null default 'RAW_NOTIFICATION_ONLY'
    check (candidate_status in ('REVIEW_READY','PARTIAL_CANDIDATE','RAW_NOTIFICATION_ONLY')),
  match_status text not null default 'pending'
    check (match_status in ('pending','matched','mismatch')),
  matched_ingress_event_id uuid,
  notification_posted_at timestamptz not null,
  created_at timestamptz not null default now(),
  check (receipt_candidate is null or receipt_candidate ~ '^[A-Z0-9]{8,16}$'),
  check (amount_minor is null or amount_minor > 0),
  check (sender_phone_candidate is null or sender_phone_candidate ~ '^254[17][0-9]{8}$'),
  unique (business_id, notification_hash),
  unique (business_id, id),
  foreign key (business_id, matched_ingress_event_id)
    references public.payment_ingress_events(business_id, id) on delete restrict
);

create unique index payment_notification_receipt_once_idx
  on public.payment_notification_candidates(business_id, receipt_candidate)
  where receipt_candidate is not null;

create index payment_notification_candidates_review_idx
  on public.payment_notification_candidates(business_id, created_at desc)
  where match_status <> 'matched';

alter table public.payment_notification_candidates enable row level security;

create policy "authorized staff submit own payment notification candidates"
on public.payment_notification_candidates for insert to authenticated
with check (
  source_user_id = (select auth.uid())
  and (select private.has_business_role(
    business_id, array['owner','business_admin','agent_manager','agent']
  ))
);

create policy "business managers read payment notification candidates"
on public.payment_notification_candidates for select to authenticated
using (
  (select private.has_business_role(
    business_id, array['owner','business_admin','agent_manager']
  ))
  or (
    source_user_id = (select auth.uid())
    and (select private.has_business_role(business_id, array['agent']))
  )
);

grant select, insert on public.payment_notification_candidates to authenticated;
revoke all on public.payment_notification_candidates from anon;

-- A C2B callback and a local notification can enrich one another only when
-- the business, exact M-PESA receipt and exact amount agree. The payment stays
-- unverified; a conflicting full phone number moves the callback to review.
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
  v_candidate_id uuid;
  v_candidate_phone text;
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

  select c.id, c.sender_phone_candidate
    into v_candidate_id, v_candidate_phone
  from public.payment_notification_candidates c
  where c.business_id = v_business_id
    and c.receipt_candidate = v_receipt
    and c.amount_minor = v_amount_minor
  limit 1;

  if v_candidate_id is null then
    return null;
  end if;

  if v_event_phone is not null
     and v_candidate_phone is not null
     and v_event_phone <> v_candidate_phone then
    update public.payment_ingress_events
      set verification_status = 'ambiguous'
      where business_id = v_business_id and id = v_event_id
        and verification_status <> 'verified';
    update public.payment_notification_candidates
      set match_status = 'mismatch', matched_ingress_event_id = v_event_id
      where business_id = v_business_id and id = v_candidate_id;
    return null;
  end if;

  update public.payment_ingress_events
    set payer_phone = coalesce(payer_phone, v_candidate_phone)
    where business_id = v_business_id and id = v_event_id;

  update public.payment_notification_candidates
    set match_status = 'matched', matched_ingress_event_id = v_event_id
    where business_id = v_business_id and id = v_candidate_id;

  return null;
end;
$$;
revoke all on function private.reconcile_payment_notification_candidate()
  from public, anon, authenticated;

create trigger payment_notification_candidate_reconcile
after insert on public.payment_notification_candidates
for each row execute function private.reconcile_payment_notification_candidate();

create trigger payment_ingress_event_reconcile_candidate
after insert on public.payment_ingress_events
for each row execute function private.reconcile_payment_notification_candidate();

-- Price alone can be ambiguous. A business must define exactly one explicit
-- offer for a payment amount; the mapping is inactive until its USSD path is
-- captured and reviewed on the actual Safaricom SIM.
alter table public.offers
  add constraint offers_business_id_id_unique unique (business_id, id);

create table public.payment_offer_rules (
  id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.businesses(id) on delete cascade,
  amount_minor bigint not null check (amount_minor > 0 and amount_minor % 100 = 0),
  offer_id uuid not null,
  menu_entry_code text not null check (menu_entry_code ~ '^\\*[0-9*]+#$'),
  menu_steps jsonb not null default '[]'::jsonb
    check (jsonb_typeof(menu_steps) = 'array'),
  menu_flow_verified_at timestamptz,
  is_enabled boolean not null default false,
  created_by uuid not null default auth.uid() references auth.users(id) on delete restrict,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (business_id, amount_minor),
  foreign key (business_id, offer_id)
    references public.offers(business_id, id) on delete restrict,
  check (
    not is_enabled
    or (
      menu_flow_verified_at is not null
      and jsonb_array_length(menu_steps) > 0
    )
  )
);

create index payment_offer_rules_enabled_idx
  on public.payment_offer_rules(business_id, amount_minor)
  where is_enabled;

alter table public.payment_offer_rules enable row level security;

create policy "business managers read payment offer rules"
on public.payment_offer_rules for select to authenticated
using (private.has_business_role(
  business_id, array['owner','business_admin','agent_manager']
));

create policy "owners create payment offer rules"
on public.payment_offer_rules for insert to authenticated
with check (
  created_by = (select auth.uid())
  and private.has_business_role(business_id, array['owner','business_admin'])
);

create policy "owners update payment offer rules"
on public.payment_offer_rules for update to authenticated
using (private.has_business_role(business_id, array['owner','business_admin']))
with check (private.has_business_role(business_id, array['owner','business_admin']));

create policy "owners delete payment offer rules"
on public.payment_offer_rules for delete to authenticated
using (private.has_business_role(business_id, array['owner','business_admin']));

grant select, insert, update, delete on public.payment_offer_rules to authenticated;
revoke all on public.payment_offer_rules from anon;
