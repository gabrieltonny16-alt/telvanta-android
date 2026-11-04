-- BingwaPulse core foundation
-- Applied to Supabase project tuuhucisljlaaovtnbeu (Postgres 17).
create table public.businesses (
 id uuid primary key default gen_random_uuid(),
 name text not null check(length(trim(name)) between 2 and 120),
 slug text not null unique check(slug ~ '^[a-z0-9][a-z0-9-]{2,48}$'),
 currency text not null default 'KES' check(currency='KES'),
 timezone text not null default 'Africa/Nairobi',
 created_by uuid not null references auth.users(id) on delete restrict,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now()
);
create table public.business_memberships (
 id uuid primary key default gen_random_uuid(),
 business_id uuid not null references public.businesses(id) on delete cascade,
 user_id uuid not null references auth.users(id) on delete cascade,
 role text not null check(role in ('owner','business_admin','agent_manager','agent')),
 is_active boolean not null default true,
 created_at timestamptz not null default now(),
 unique(business_id,user_id)
);
create index business_memberships_user_idx on public.business_memberships(user_id,is_active);
create table public.profiles (
 id uuid primary key references auth.users(id) on delete cascade,
 full_name text, phone_e164 text,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now()
);
create table public.providers (
 id uuid primary key default gen_random_uuid(),
 business_id uuid not null references public.businesses(id) on delete cascade,
 name text not null,
 adapter_key text not null default 'manual' check(adapter_key in ('manual','ussd_assisted','approved_api')),
 status text not null default 'inactive' check(status in ('active','inactive','paused')),
 created_at timestamptz not null default now(),
 unique(business_id,name)
);
create index providers_business_idx on public.providers(business_id);
create table public.offers (
 id uuid primary key default gen_random_uuid(),
 business_id uuid not null references public.businesses(id) on delete cascade,
 provider_id uuid not null references public.providers(id) on delete restrict,
 external_offer_ref text not null, code text, name text not null,
 category text not null check(category in ('data','sms','minutes','combo')),
 description text,
 provider_cost_kes integer not null check(provider_cost_kes>=0),
 is_active boolean not null default true,
 valid_from timestamptz, valid_until timestamptz,
 source_observed_at timestamptz, stale_after timestamptz,
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 unique(business_id,provider_id,external_offer_ref),
 check(valid_until is null or valid_from is null or valid_until>valid_from)
);
create index offers_business_active_idx on public.offers(business_id,is_active);
create table public.agent_offers (
 id uuid primary key default gen_random_uuid(),
 business_id uuid not null references public.businesses(id) on delete cascade,
 offer_id uuid not null references public.offers(id) on delete cascade,
 agent_user_id uuid not null references auth.users(id) on delete cascade,
 selling_price_kes integer not null check(selling_price_kes>=0),
 is_published boolean not null default false,
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 unique(offer_id,agent_user_id)
);
create index agent_offers_business_agent_idx on public.agent_offers(business_id,agent_user_id);
create table public.customers (
 id uuid primary key default gen_random_uuid(),
 business_id uuid not null references public.businesses(id) on delete cascade,
 phone_e164 text not null, display_name text, notes text,
 created_by uuid references auth.users(id) on delete set null,
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 unique(business_id,phone_e164)
);
create index customers_business_idx on public.customers(business_id);
create table public.devices (
 id uuid primary key default gen_random_uuid(),
 business_id uuid not null references public.businesses(id) on delete cascade,
 assigned_user_id uuid references auth.users(id) on delete set null,
 device_label text not null, platform text not null default 'android' check(platform='android'),
 status text not null default 'pending_pairing' check(status in ('pending_pairing','online','offline','paused','revoked')),
 last_seen_at timestamptz, paired_at timestamptz, revoked_at timestamptz,
 created_at timestamptz not null default now()
);
create index devices_business_status_idx on public.devices(business_id,status);
create table public.site_links (
 id uuid primary key default gen_random_uuid(),
 business_id uuid not null references public.businesses(id) on delete cascade,
 agent_user_id uuid not null references auth.users(id) on delete cascade,
 slug text not null unique check(slug ~ '^[a-z0-9][a-z0-9-]{2,48}$'),
 is_active boolean not null default false, display_name text,
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 unique(business_id,agent_user_id)
);
create table public.orders (
 id uuid primary key default gen_random_uuid(),
 business_id uuid not null references public.businesses(id) on delete restrict,
 agent_user_id uuid references auth.users(id) on delete set null,
 customer_id uuid references public.customers(id) on delete set null,
 offer_id uuid references public.offers(id) on delete set null,
 device_id uuid references public.devices(id) on delete set null,
 channel text not null default 'android' check(channel in ('android','sitelink','management_web')),
 recipient_phone text not null, payer_phone text,
 status text not null default 'AWAITING_PAYMENT' check(status in (
 'AWAITING_PAYMENT','RECEIVED','VALIDATED','PAID','QUEUED','CLAIMED','USSD_SENT',
 'DELIVERED','FAILED','UNKNOWN','REVIEW_REQUIRED','RECONCILIATION_REQUIRED','CANCELLED')),
 currency text not null default 'KES' check(currency='KES'),
 provider_cost_kes integer not null default 0 check(provider_cost_kes>=0),
 selling_price_kes integer not null check(selling_price_kes>=0),
 customer_price_kes integer not null check(customer_price_kes>=0),
 idempotency_key text not null,
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 unique(business_id,idempotency_key)
);
create index orders_business_status_created_idx on public.orders(business_id,status,created_at desc);
create index orders_agent_created_idx on public.orders(agent_user_id,created_at desc);
create table public.payments (
 id uuid primary key default gen_random_uuid(),
 business_id uuid not null references public.businesses(id) on delete restrict,
 order_id uuid references public.orders(id) on delete set null,
 receipt_code text not null, amount_kes integer not null check(amount_kes>0),
 payer_phone text, recipient_phone text,
 evidence_source text not null check(evidence_source in ('mpesa_notification','manual_review','provider_callback')),
 verification_status text not null default 'unverified' check(verification_status in ('unverified','matched','verified','rejected','ambiguous')),
 received_at timestamptz, created_at timestamptz not null default now(),
 unique(business_id,receipt_code)
);
create index payments_business_created_idx on public.payments(business_id,created_at desc);
create table public.order_events (
 id uuid primary key default gen_random_uuid(),
 business_id uuid not null references public.businesses(id) on delete cascade,
 order_id uuid not null references public.orders(id) on delete cascade,
 from_status text, to_status text not null,
 actor_user_id uuid references auth.users(id) on delete set null,
 event_source text not null, details jsonb not null default '{}'::jsonb,
 created_at timestamptz not null default now()
);
create index order_events_order_created_idx on public.order_events(order_id,created_at);
create table public.dispatch_outbox (
 id uuid primary key default gen_random_uuid(),
 business_id uuid not null references public.businesses(id) on delete cascade,
 order_id uuid not null references public.orders(id) on delete cascade,
 device_id uuid references public.devices(id) on delete set null,
 event_type text not null check(event_type in ('ORDER_QUEUED','ORDER_RECONCILE','DEVICE_RECOVERY')),
 payload jsonb not null default '{}'::jsonb,
 status text not null default 'pending' check(status in ('pending','processing','sent','failed','cancelled')),
 attempt_count integer not null default 0 check(attempt_count>=0),
 next_attempt_at timestamptz not null default now(), lease_expires_at timestamptz,
 last_error text, created_at timestamptz not null default now(), sent_at timestamptz
);
create index dispatch_outbox_ready_idx on public.dispatch_outbox(status,next_attempt_at) where status in ('pending','failed');
create table public.device_heartbeats (
 id uuid primary key default gen_random_uuid(),
 business_id uuid not null references public.businesses(id) on delete cascade,
 device_id uuid not null references public.devices(id) on delete cascade,
 worker_state text not null check(worker_state in ('idle','working','waiting_ussd','recovering','stopped','unknown')),
 app_version text, battery_percent smallint check(battery_percent between 0 and 100),
 network_state text, observed_at timestamptz not null default now()
);
create index device_heartbeats_device_observed_idx on public.device_heartbeats(device_id,observed_at desc);
create table public.audit_logs (
 id uuid primary key default gen_random_uuid(),
 business_id uuid not null references public.businesses(id) on delete cascade,
 actor_user_id uuid references auth.users(id) on delete set null,
 action text not null, entity_type text not null, entity_id uuid,
 details jsonb not null default '{}'::jsonb, created_at timestamptz not null default now()
);
create index audit_logs_business_created_idx on public.audit_logs(business_id,created_at desc);

create or replace function public.is_business_member(p_business_id uuid) returns boolean
language sql stable security definer set search_path=public,pg_temp as $$
 select exists(select 1 from public.business_memberships m where m.business_id=p_business_id and m.user_id=(select auth.uid()) and m.is_active=true);
$$;
create or replace function public.has_business_role(p_business_id uuid,p_roles text[]) returns boolean
language sql stable security definer set search_path=public,pg_temp as $$
 select exists(select 1 from public.business_memberships m where m.business_id=p_business_id and m.user_id=(select auth.uid()) and m.is_active=true and m.role=any(p_roles));
$$;
revoke all on function public.is_business_member(uuid) from public,anon;
revoke all on function public.has_business_role(uuid,text[]) from public,anon;
grant execute on function public.is_business_member(uuid) to authenticated;
grant execute on function public.has_business_role(uuid,text[]) to authenticated;
create or replace function public.create_business(p_name text,p_slug text) returns uuid
language plpgsql security definer set search_path=public,pg_temp as $$
declare v_id uuid; v_slug text:=lower(trim(p_slug));
begin
 if (select auth.uid()) is null then raise exception 'Authentication required' using errcode='42501'; end if;
 if length(trim(p_name))<2 or length(trim(p_name))>120 then raise exception 'Business name must be between 2 and 120 characters'; end if;
 if v_slug !~ '^[a-z0-9][a-z0-9-]{2,48}$' then raise exception 'Slug must be 3-49 lowercase letters, numbers or hyphens'; end if;
 insert into public.businesses(name,slug,created_by) values(trim(p_name),v_slug,(select auth.uid())) returning id into v_id;
 insert into public.business_memberships(business_id,user_id,role) values(v_id,(select auth.uid()),'owner');
 insert into public.audit_logs(business_id,actor_user_id,action,entity_type,entity_id) values(v_id,(select auth.uid()),'business.created','business',v_id);
 return v_id;
end; $$;
revoke all on function public.create_business(text,text) from public,anon;
grant execute on function public.create_business(text,text) to authenticated;

alter table public.businesses enable row level security;
alter table public.business_memberships enable row level security;
alter table public.profiles enable row level security;
alter table public.providers enable row level security;
alter table public.offers enable row level security;
alter table public.agent_offers enable row level security;
alter table public.customers enable row level security;
alter table public.devices enable row level security;
alter table public.site_links enable row level security;
alter table public.orders enable row level security;
alter table public.payments enable row level security;
alter table public.order_events enable row level security;
alter table public.dispatch_outbox enable row level security;
alter table public.device_heartbeats enable row level security;
alter table public.audit_logs enable row level security;

create policy "business members can view their businesses" on public.businesses for select to authenticated using(public.is_business_member(id));
create policy "business admins can update their businesses" on public.businesses for update to authenticated using(public.has_business_role(id,array['owner','business_admin'])) with check(public.has_business_role(id,array['owner','business_admin']));
create policy "members can view their own membership or managers can view team" on public.business_memberships for select to authenticated using(user_id=(select auth.uid()) or public.has_business_role(business_id,array['owner','business_admin','agent_manager']));
create policy "users can view own profile" on public.profiles for select to authenticated using(id=(select auth.uid()));
create policy "users can update own profile" on public.profiles for update to authenticated using(id=(select auth.uid())) with check(id=(select auth.uid()));
create policy "members can view providers" on public.providers for select to authenticated using(public.is_business_member(business_id));
create policy "managers manage providers" on public.providers for all to authenticated using(public.has_business_role(business_id,array['owner','business_admin','agent_manager'])) with check(public.has_business_role(business_id,array['owner','business_admin','agent_manager']));
create policy "members can view offers" on public.offers for select to authenticated using(public.is_business_member(business_id));
create policy "managers manage offers" on public.offers for all to authenticated using(public.has_business_role(business_id,array['owner','business_admin','agent_manager'])) with check(public.has_business_role(business_id,array['owner','business_admin','agent_manager']));
create policy "members can view agent offers" on public.agent_offers for select to authenticated using(public.is_business_member(business_id));
create policy "managers manage agent offers" on public.agent_offers for all to authenticated using(public.has_business_role(business_id,array['owner','business_admin','agent_manager'])) with check(public.has_business_role(business_id,array['owner','business_admin','agent_manager']));
create policy "members can view customers" on public.customers for select to authenticated using(public.is_business_member(business_id));
create policy "agents can add customers" on public.customers for insert to authenticated with check(public.has_business_role(business_id,array['owner','business_admin','agent_manager','agent']) and (created_by is null or created_by=(select auth.uid())));
create policy "agents can update customers" on public.customers for update to authenticated using(public.has_business_role(business_id,array['owner','business_admin','agent_manager','agent'])) with check(public.has_business_role(business_id,array['owner','business_admin','agent_manager','agent']));
create policy "members can view devices" on public.devices for select to authenticated using(public.is_business_member(business_id));
create policy "managers manage devices" on public.devices for all to authenticated using(public.has_business_role(business_id,array['owner','business_admin','agent_manager'])) with check(public.has_business_role(business_id,array['owner','business_admin','agent_manager']));
create policy "members can view site links" on public.site_links for select to authenticated using(public.is_business_member(business_id));
create policy "managers manage site links" on public.site_links for all to authenticated using(public.has_business_role(business_id,array['owner','business_admin','agent_manager'])) with check(public.has_business_role(business_id,array['owner','business_admin','agent_manager']));
create policy "members can view orders" on public.orders for select to authenticated using(public.is_business_member(business_id));
create policy "authorized members can create orders" on public.orders for insert to authenticated with check(public.has_business_role(business_id,array['owner','business_admin','agent_manager','agent']) and (agent_user_id is null or agent_user_id=(select auth.uid())));
create policy "managers can view payments" on public.payments for select to authenticated using(public.has_business_role(business_id,array['owner','business_admin','agent_manager']));
create policy "members can view order events" on public.order_events for select to authenticated using(public.is_business_member(business_id));
create policy "managers can view dispatch queue" on public.dispatch_outbox for select to authenticated using(public.has_business_role(business_id,array['owner','business_admin','agent_manager']));
create policy "managers can view device heartbeats" on public.device_heartbeats for select to authenticated using(public.has_business_role(business_id,array['owner','business_admin','agent_manager']));
create policy "managers can view audit logs" on public.audit_logs for select to authenticated using(public.has_business_role(business_id,array['owner','business_admin','agent_manager']));
grant usage on schema public to authenticated;
grant select,insert,update,delete on public.businesses,public.business_memberships,public.profiles,public.providers,public.offers,public.agent_offers,public.customers,public.devices,public.site_links,public.orders,public.payments,public.order_events,public.dispatch_outbox,public.device_heartbeats,public.audit_logs to authenticated;
revoke all on public.businesses,public.business_memberships,public.profiles,public.providers,public.offers,public.agent_offers,public.customers,public.devices,public.site_links,public.orders,public.payments,public.order_events,public.dispatch_outbox,public.device_heartbeats,public.audit_logs from anon;
alter publication supabase_realtime add table public.orders;
alter publication supabase_realtime add table public.order_events;
alter publication supabase_realtime add table public.offers;
alter publication supabase_realtime add table public.agent_offers;
alter publication supabase_realtime add table public.devices;
alter publication supabase_realtime add table public.site_links;
