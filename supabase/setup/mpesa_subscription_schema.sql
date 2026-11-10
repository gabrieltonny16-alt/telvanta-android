-- BingwaPulse M-PESA Buy Goods subscription support.
-- This additive setup is already applied to Supabase project tuuhucisljlaaovtnbeu.
-- Apply once to a fresh environment before deploying mpesa-subscriptions.

begin;

create table if not exists public.mpesa_subscription_attempts (
  id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.businesses(id) on delete cascade,
  requested_by uuid not null references auth.users(id),
  plan_code text not null references public.platform_subscription_plans(code),
  expected_amount_kes integer not null check (expected_amount_kes > 0),
  payer_phone text not null check (payer_phone ~ '^254[17][0-9]{8}$'),
  status text not null default 'initiating'
    check (status in ('initiating','pending','completed','failed','expired')),
  merchant_request_id text,
  checkout_request_id text unique,
  mpesa_receipt text unique,
  result_code integer,
  result_description text,
  activated_subscription_id uuid references public.business_platform_subscriptions(id),
  expires_at timestamptz not null default (now() + interval '10 minutes'),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists mpesa_subscription_attempts_business_created_idx
  on public.mpesa_subscription_attempts(business_id, created_at desc);

alter table public.mpesa_subscription_attempts enable row level security;
revoke all on public.mpesa_subscription_attempts from anon, authenticated;
grant all on public.mpesa_subscription_attempts to service_role;

alter table public.business_platform_subscriptions
  drop constraint if exists business_platform_subscriptions_source_check;
alter table public.business_platform_subscriptions
  add constraint business_platform_subscriptions_source_check
  check (source = any (array['manual_grant','payment','promotion','mpesa']::text[]));

create or replace function public.activate_mpesa_subscription(
  p_attempt_id uuid,
  p_result_code integer,
  p_amount_kes integer,
  p_payer_phone text,
  p_receipt text,
  p_checkout_request_id text,
  p_result_description text default null
) returns uuid
language plpgsql
set search_path = public
as $$
declare
  v_attempt public.mpesa_subscription_attempts%rowtype;
  v_duration integer;
  v_subscription_id uuid;
begin
  select * into v_attempt
  from public.mpesa_subscription_attempts
  where id = p_attempt_id
  for update;

  if not found then raise exception 'M-PESA subscription attempt not found'; end if;
  if v_attempt.status = 'completed' then return v_attempt.activated_subscription_id; end if;

  if v_attempt.status <> 'pending'
     or v_attempt.checkout_request_id is distinct from p_checkout_request_id
     or p_result_code <> 0
     or p_amount_kes is distinct from v_attempt.expected_amount_kes
     or p_payer_phone is distinct from v_attempt.payer_phone
     or coalesce(trim(p_receipt),'') = ''
     or v_attempt.expires_at < now() then
    update public.mpesa_subscription_attempts
      set status = 'failed',
          result_code = p_result_code,
          result_description = left(coalesce(p_result_description,'Payment verification mismatch'), 500),
          updated_at = now()
      where id = p_attempt_id;
    raise exception 'M-PESA callback did not match the expected payment';
  end if;

  select duration_seconds into v_duration
  from public.platform_subscription_plans
  where code = v_attempt.plan_code and is_active = true;
  if v_duration is null or v_duration <= 0 then
    raise exception 'Subscription plan is no longer active';
  end if;

  insert into public.business_platform_subscriptions
    (business_id, plan_code, status, starts_at, ends_at, source, external_reference, created_by)
  values
    (v_attempt.business_id, v_attempt.plan_code, 'active', now(),
     now() + make_interval(secs => v_duration), 'mpesa', p_receipt, v_attempt.requested_by)
  returning id into v_subscription_id;

  update public.mpesa_subscription_attempts
    set status = 'completed',
        result_code = p_result_code,
        result_description = left(coalesce(p_result_description,'Payment verified'), 500),
        mpesa_receipt = p_receipt,
        activated_subscription_id = v_subscription_id,
        updated_at = now()
    where id = p_attempt_id;

  return v_subscription_id;
end;
$$;

revoke all on function public.activate_mpesa_subscription(uuid,integer,integer,text,text,text,text)
  from public, anon, authenticated;
grant execute on function public.activate_mpesa_subscription(uuid,integer,integer,text,text,text,text)
  to service_role;

commit;
