-- Phase 1: payment ingress deduplication and immutable double-entry ledger.
-- Additive migration; does not mark any payment as verified or dispatch any order.

create table public.payment_ingress_channels (
  id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.businesses(id) on delete cascade,
  shortcode text not null check (shortcode ~ '^[0-9]{4,20}$'),
  channel_type text not null check (channel_type in ('till','paybill')),
  is_active boolean not null default false,
  created_at timestamptz not null default now(),
  unique (shortcode),
  unique (business_id, id)
);
create index payment_ingress_channels_business_idx on public.payment_ingress_channels(business_id);

create table public.payment_ingress_events (
  id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.businesses(id) on delete restrict,
  provider text not null check (provider in ('safaricom_c2b','mpesa_sms_candidate')),
  transaction_ref text not null check (length(transaction_ref) between 8 and 16),
  amount_minor bigint not null check (amount_minor > 0),
  shortcode text not null,
  payer_phone text,
  raw_msisdn text,
  occurred_at timestamptz not null,
  source_kind text not null check (source_kind in ('daraja_callback','sms_candidate')),
  payload_sha256 char(64) not null,
  verification_status text not null default 'unverified'
    check (verification_status in ('unverified','verified','rejected','ambiguous')),
  matched_order_id uuid,
  created_at timestamptz not null default now(),
  unique (business_id, provider, transaction_ref),
  unique (business_id, id),
  foreign key (business_id, matched_order_id)
    references public.orders(business_id, id) on delete restrict
);
create index payment_ingress_events_unmatched_idx
  on public.payment_ingress_events(business_id, occurred_at desc)
  where matched_order_id is null;
create index payment_ingress_events_receipt_idx
  on public.payment_ingress_events(transaction_ref);

-- Every ledger transaction is a batch. Entries are immutable, in integer KES minor units.
create table public.balance_ledger_accounts (
  id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.businesses(id) on delete restrict,
  account_code text not null,
  account_name text not null,
  account_type text not null check (account_type in ('asset','liability','income','expense','equity')),
  owner_user_id uuid,
  created_at timestamptz not null default now(),
  unique (business_id, account_code),
  unique (business_id, id)
);

create table public.balance_ledger_batches (
  id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.businesses(id) on delete restrict,
  order_id uuid,
  source_type text not null check (source_type in ('payment','order','provider_float','agent_commission','adjustment','reversal')),
  source_ref text not null,
  currency text not null default 'KES' check (currency='KES'),
  memo text not null,
  posted_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  unique (business_id, source_type, source_ref),
  unique (business_id, id),
  foreign key (business_id, order_id) references public.orders(business_id, id) on delete restrict
);

create table public.balance_ledger_entries (
  id uuid primary key default gen_random_uuid(),
  business_id uuid not null,
  batch_id uuid not null,
  order_id uuid,
  account_id uuid not null,
  entry_no smallint not null check (entry_no > 0),
  debit_minor bigint not null default 0 check (debit_minor >= 0),
  credit_minor bigint not null default 0 check (credit_minor >= 0),
  created_at timestamptz not null default now(),
  check ((debit_minor > 0 and credit_minor = 0) or (credit_minor > 0 and debit_minor = 0)),
  unique (batch_id, entry_no),
  foreign key (business_id, batch_id) references public.balance_ledger_batches(business_id, id) on delete restrict,
  foreign key (business_id, order_id) references public.orders(business_id, id) on delete restrict,
  foreign key (business_id, account_id) references public.balance_ledger_accounts(business_id, id) on delete restrict
);
create index balance_ledger_entries_account_idx on public.balance_ledger_entries(business_id, account_id, created_at desc);
create index balance_ledger_entries_order_idx on public.balance_ledger_entries(business_id, order_id, created_at desc);

-- Deferred validation allows all lines in a journal batch to be inserted in one transaction.
create or replace function private.assert_ledger_batch_balanced()
returns trigger language plpgsql
set search_path = public, pg_temp
as $$
declare
  v_batch uuid := coalesce(new.batch_id, old.batch_id);
  v_business uuid := coalesce(new.business_id, old.business_id);
  v_count integer;
  v_debits bigint;
  v_credits bigint;
begin
  select count(*), coalesce(sum(debit_minor),0), coalesce(sum(credit_minor),0)
    into v_count, v_debits, v_credits
  from public.balance_ledger_entries
  where business_id=v_business and batch_id=v_batch;
  if v_count < 2 or v_debits <> v_credits then
    raise exception 'Ledger batch % must contain at least two balanced entries (debits %, credits %)', v_batch, v_debits, v_credits
      using errcode='23514';
  end if;
  return null;
end;
$$;
revoke all on function private.assert_ledger_batch_balanced() from public, anon, authenticated;
create constraint trigger balance_ledger_batch_must_balance
after insert or update or delete on public.balance_ledger_entries
deferrable initially deferred
for each row execute function private.assert_ledger_batch_balanced();

-- Posted journal lines cannot be mutated. Corrections must be new reversal batches.
create or replace function private.prevent_ledger_entry_mutation()
returns trigger language plpgsql
set search_path = public, pg_temp
as $$
begin
  raise exception 'Ledger entries are immutable; post a reversal instead' using errcode='55000';
end;
$$;
revoke all on function private.prevent_ledger_entry_mutation() from public, anon, authenticated;
create trigger balance_ledger_entries_immutable
before update or delete on public.balance_ledger_entries
for each row execute function private.prevent_ledger_entry_mutation();

alter table public.payment_ingress_channels enable row level security;
alter table public.payment_ingress_events enable row level security;
alter table public.balance_ledger_accounts enable row level security;
alter table public.balance_ledger_batches enable row level security;
alter table public.balance_ledger_entries enable row level security;

create policy "business managers view payment ingress channels"
on public.payment_ingress_channels for select to authenticated
using (public.has_business_role(business_id,array['owner','business_admin','agent_manager']));
create policy "owners manage payment ingress channels"
on public.payment_ingress_channels for all to authenticated
using (public.has_business_role(business_id,array['owner','business_admin']))
with check (public.has_business_role(business_id,array['owner','business_admin']));
create policy "managers view payment ingress events"
on public.payment_ingress_events for select to authenticated
using (public.has_business_role(business_id,array['owner','business_admin','agent_manager']));
create policy "managers view ledger accounts"
on public.balance_ledger_accounts for select to authenticated
using (public.has_business_role(business_id,array['owner','business_admin','agent_manager']));
create policy "managers view ledger batches"
on public.balance_ledger_batches for select to authenticated
using (public.has_business_role(business_id,array['owner','business_admin','agent_manager']));
create policy "managers view ledger entries"
on public.balance_ledger_entries for select to authenticated
using (public.has_business_role(business_id,array['owner','business_admin','agent_manager']));

grant select on public.payment_ingress_channels, public.payment_ingress_events,
  public.balance_ledger_accounts, public.balance_ledger_batches, public.balance_ledger_entries to authenticated;
revoke all on public.payment_ingress_channels, public.payment_ingress_events,
  public.balance_ledger_accounts, public.balance_ledger_batches, public.balance_ledger_entries from anon;

-- Backend service role is the only writer for ingress and ledger posting.
grant select, insert, update on public.payment_ingress_events to service_role;
grant select on public.payment_ingress_channels to service_role;
grant select, insert on public.balance_ledger_accounts, public.balance_ledger_batches, public.balance_ledger_entries to service_role;
