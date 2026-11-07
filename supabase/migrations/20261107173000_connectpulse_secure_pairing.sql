create schema if not exists private;

alter table public.devices
  add column if not exists credential_issued_at timestamptz;

create table if not exists private.device_credentials (
  device_id uuid primary key references public.devices(id) on delete cascade,
  credential_hash text not null check (credential_hash ~ '^[0-9a-f]{64}$'),
  created_at timestamptz not null default now(),
  revoked_at timestamptz
);

create table public.device_pairing_challenges (
  id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.businesses(id) on delete cascade,
  created_by uuid not null references auth.users(id) on delete cascade,
  device_label text not null check (length(trim(device_label)) between 2 and 100),
  code_hash text not null unique check (code_hash ~ '^[0-9a-f]{64}$'),
  expires_at timestamptz not null,
  consumed_at timestamptz,
  created_at timestamptz not null default now()
);

alter table public.device_pairing_challenges enable row level security;
revoke all on public.device_pairing_challenges from anon, authenticated;
grant insert on public.device_pairing_challenges to authenticated;

create policy "managers create device pairing challenges"
on public.device_pairing_challenges
for insert to authenticated
with check (
  created_by = (select auth.uid())
  and private.has_business_role(business_id, array['owner','business_admin','agent_manager']::text[])
  and expires_at > now()
  and expires_at <= now() + interval '15 minutes'
);

create or replace function public.claim_device_pairing_code(p_code_hash text,p_device_label text,p_credential_hash text)
returns table(device_id uuid,business_id uuid,assigned_user_id uuid)
language plpgsql security definer set search_path=public,private,pg_temp as $$
declare v_challenge public.device_pairing_challenges%rowtype; v_device_id uuid;
begin
  if p_code_hash !~ '^[0-9a-f]{64}$' or p_credential_hash !~ '^[0-9a-f]{64}$'
     or length(trim(p_device_label)) not between 2 and 100 then
    raise exception 'Invalid pairing request' using errcode='22023';
  end if;
  select c.* into v_challenge from public.device_pairing_challenges c
  where c.code_hash=p_code_hash and c.consumed_at is null and c.expires_at>now()
  for update;
  if not found then raise exception 'Pairing code is invalid, expired, or already used' using errcode='P0001'; end if;
  insert into public.devices(business_id,assigned_user_id,device_label,platform,status,credential_issued_at)
  values(v_challenge.business_id,v_challenge.created_by,trim(p_device_label),'android','pending_pairing',now())
  returning id into v_device_id;
  insert into private.device_credentials(device_id,credential_hash) values(v_device_id,p_credential_hash);
  update public.device_pairing_challenges set consumed_at=now() where id=v_challenge.id;
  return query select v_device_id,v_challenge.business_id,v_challenge.created_by;
end; $$;
revoke all on function public.claim_device_pairing_code(text,text,text) from public,anon,authenticated;
grant execute on function public.claim_device_pairing_code(text,text,text) to service_role;

create or replace function public.connectpulse_device_status(p_device_id uuid,p_credential_hash text)
returns table(status text,last_seen_at timestamptz,paired_at timestamptz)
language plpgsql security definer set search_path=public,private,pg_temp as $$
begin
  if p_credential_hash !~ '^[0-9a-f]{64}$' then raise exception 'Invalid device credential' using errcode='22023'; end if;
  return query select d.status,d.last_seen_at,d.paired_at
  from public.devices d join private.device_credentials c on c.device_id=d.id
  where d.id=p_device_id and c.credential_hash=p_credential_hash and c.revoked_at is null;
  if not found then raise exception 'Device credential not recognised' using errcode='42501'; end if;
end; $$;
revoke all on function public.connectpulse_device_status(uuid,text) from public,anon,authenticated;
grant execute on function public.connectpulse_device_status(uuid,text) to service_role;

create or replace function public.record_connectpulse_heartbeat(
  p_device_id uuid,p_credential_hash text,p_worker_state text default 'unknown',
  p_battery_percent smallint default null,p_network_state text default null,p_app_version text default null
)
returns text language plpgsql security definer set search_path=public,private,pg_temp as $$
declare v_device public.devices%rowtype;
begin
  if p_credential_hash !~ '^[0-9a-f]{64}$'
    or p_worker_state not in ('idle','working','waiting_ussd','recovering','stopped','unknown')
    or (p_battery_percent is not null and p_battery_percent not between 0 and 100) then
    raise exception 'Invalid heartbeat payload' using errcode='22023';
  end if;
  select d.* into v_device from public.devices d
  join private.device_credentials c on c.device_id=d.id
  where d.id=p_device_id and c.credential_hash=p_credential_hash and c.revoked_at is null
  for update of d;
  if not found then raise exception 'Device credential not recognised' using errcode='42501'; end if;
  if v_device.status<>'online' then return v_device.status; end if;
  update public.devices set last_seen_at=now() where id=p_device_id;
  insert into public.device_heartbeats(business_id,device_id,worker_state,app_version,battery_percent,network_state)
  values(v_device.business_id,p_device_id,p_worker_state,p_app_version,p_battery_percent,left(coalesce(p_network_state,''),80));
  return 'online';
end; $$;
revoke all on function public.record_connectpulse_heartbeat(uuid,text,text,smallint,text,text) from public,anon,authenticated;
grant execute on function public.record_connectpulse_heartbeat(uuid,text,text,smallint,text,text) to service_role;

comment on table private.device_credentials is 'Private hashed ConnectPulse device credentials. Raw device tokens are never stored.';
comment on table public.device_pairing_challenges is 'Short-lived, one-time pairing challenges created by authenticated business managers.';
