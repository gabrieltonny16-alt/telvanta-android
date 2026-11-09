-- BingwaPulse fulfilment engine: manager-controlled automation, atomic claims, and audited outcomes.
-- Safe by default: fulfilment is disabled and paused until a business manager enables it.
alter table public.businesses
  add column if not exists fulfilment_enabled boolean not null default false,
  add column if not exists fulfilment_paused boolean not null default true,
  add column if not exists fulfilment_require_manual_pin boolean not null default true;
alter table public.devices add constraint devices_business_id_id_unique unique (business_id,id);

create table if not exists public.order_fulfilment_attempts (
  id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.businesses(id) on delete cascade,
  order_id uuid not null references public.orders(id) on delete cascade,
  device_id uuid not null references public.devices(id) on delete restrict,
  attempt_no integer not null check (attempt_no > 0),
  state text not null check (state in ('CLAIMED','USSD_SENT','DELIVERED','FAILED','UNKNOWN')),
  menu_step integer not null default 0 check (menu_step >= 0),
  screen_text text, provider_reference text, error_code text, error_detail text,
  started_at timestamptz not null default now(), finished_at timestamptz,
  created_at timestamptz not null default now(),
  unique(order_id,attempt_no),
  foreign key (business_id,order_id) references public.orders(business_id,id) on delete cascade,
  foreign key (business_id,device_id) references public.devices(business_id,id) on delete restrict
);
create index if not exists order_fulfilment_attempts_business_created_idx on public.order_fulfilment_attempts(business_id,created_at desc);
create index if not exists order_fulfilment_attempts_order_idx on public.order_fulfilment_attempts(order_id,attempt_no desc);
alter table public.order_fulfilment_attempts enable row level security;
create policy "managers can view fulfilment attempts" on public.order_fulfilment_attempts for select to authenticated
using (private.has_business_role(business_id,array['owner','business_admin','agent_manager']));
grant select on public.order_fulfilment_attempts to authenticated;
revoke all on public.order_fulfilment_attempts from anon;

create or replace function public.claim_next_fulfilment_order(p_business_id uuid,p_device_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
 v_user_id uuid:=auth.uid(); v_device public.devices%rowtype; v_order public.orders%rowtype;
 v_rule public.payment_offer_rules%rowtype; v_offer public.offers%rowtype; v_provider public.providers%rowtype; v_attempt_no integer;
begin
 if v_user_id is null then raise exception 'Authentication required' using errcode='42501'; end if;
 select d.* into v_device from public.devices d where d.id=p_device_id and d.business_id=p_business_id
  and d.assigned_user_id=v_user_id and d.status='online' for update;
 if not found then raise exception 'Device is not approved, online, and assigned to this worker' using errcode='42501'; end if;
 if not exists(select 1 from public.business_memberships m where m.business_id=p_business_id and m.user_id=v_user_id
  and m.is_active and m.role in ('owner','business_admin','agent_manager')) then
  raise exception 'Worker is not authorised for this business' using errcode='42501'; end if;
 if not exists(select 1 from public.businesses b where b.id=p_business_id and b.fulfilment_enabled and not b.fulfilment_paused) then return null; end if;
 select o.* into v_order from public.orders o
 join public.payments pay on pay.business_id=o.business_id and pay.order_id=o.id and pay.verification_status='verified'
 join public.offers off on off.business_id=o.business_id and off.id=o.offer_id and off.is_active
 join public.providers pr on pr.business_id=off.business_id and pr.id=off.provider_id and pr.status='active' and pr.adapter_key='ussd_assisted'
 join public.payment_offer_rules r on r.business_id=o.business_id and r.offer_id=off.id
  and r.amount_minor=(o.customer_price_kes::bigint*100) and r.is_enabled and r.menu_flow_verified_at is not null
  and jsonb_array_length(r.menu_steps)>0
 where o.business_id=p_business_id and o.status='PAID' and o.device_id is null
 order by o.created_at asc for update of o skip locked limit 1;
 if not found then return null; end if;
 select r.* into v_rule from public.payment_offer_rules r where r.business_id=v_order.business_id and r.offer_id=v_order.offer_id
  and r.is_enabled and r.menu_flow_verified_at is not null limit 1;
 select off.* into v_offer from public.offers off where off.business_id=v_order.business_id and off.id=v_order.offer_id;
 select pr.* into v_provider from public.providers pr where pr.business_id=v_offer.business_id and pr.id=v_offer.provider_id;
 select coalesce(max(a.attempt_no),0)+1 into v_attempt_no from public.order_fulfilment_attempts a where a.order_id=v_order.id;
 update public.orders set device_id=p_device_id,status='QUEUED' where id=v_order.id and business_id=p_business_id and status='PAID';
 update public.orders set status='CLAIMED' where id=v_order.id and business_id=p_business_id and status='QUEUED';
 insert into public.order_fulfilment_attempts(business_id,order_id,device_id,attempt_no,state)
  values(p_business_id,v_order.id,p_device_id,v_attempt_no,'CLAIMED');
 update public.dispatch_outbox set status='sent',sent_at=now(),lease_expires_at=null
  where order_id=v_order.id and event_type='ORDER_QUEUED' and status in ('pending','processing');
 return jsonb_build_object('order_id',v_order.id,'business_id',v_order.business_id,'device_id',p_device_id,
  'attempt_no',v_attempt_no,'recipient_phone',v_order.recipient_phone,'payer_phone',v_order.payer_phone,
  'customer_price_kes',v_order.customer_price_kes,'offer_id',v_offer.id,'offer_name',v_offer.name,
  'offer_category',v_offer.category,'provider_id',v_provider.id,'provider_name',v_provider.name,
  'ussd_code',v_rule.menu_entry_code,'menu_steps',v_rule.menu_steps,
  'require_manual_pin',(select b.fulfilment_require_manual_pin from public.businesses b where b.id=p_business_id));
end; $$;
revoke all on function public.claim_next_fulfilment_order(uuid,uuid) from public,anon;
grant execute on function public.claim_next_fulfilment_order(uuid,uuid) to authenticated;

create or replace function public.record_fulfilment_attempt_result(
 p_business_id uuid,p_order_id uuid,p_device_id uuid,p_attempt_no integer,p_state text,
 p_menu_step integer default 0,p_screen_text text default null,p_provider_reference text default null,
 p_error_code text default null,p_error_detail text default null
) returns void language plpgsql security definer set search_path = '' as $$
declare v_user_id uuid:=auth.uid(); v_order public.orders%rowtype; v_success_text text:=coalesce(p_screen_text,'');
begin
 if v_user_id is null then raise exception 'Authentication required' using errcode='42501'; end if;
 if p_state not in ('USSD_SENT','DELIVERED','FAILED','UNKNOWN') then raise exception 'Unsupported fulfilment state' using errcode='22023'; end if;
 if p_menu_step<0 then raise exception 'Invalid menu step' using errcode='22023'; end if;
 if p_state='DELIVERED' and v_success_text !~* '(transaction.*successful|purchase.*successful|successfully purchased|bundle.*activated|transaction id|bundle.*sent)' then
  raise exception 'Delivery requires a carrier success confirmation' using errcode='23514'; end if;
 select o.* into v_order from public.orders o join public.devices d on d.id=o.device_id and d.business_id=o.business_id
 where o.id=p_order_id and o.business_id=p_business_id and o.device_id=p_device_id and d.assigned_user_id=v_user_id and d.status='online'
 for update of o;
 if not found then raise exception 'Order is not assigned to this authorised worker device' using errcode='42501'; end if;
 if v_order.status not in ('CLAIMED','USSD_SENT') then raise exception 'Order is not in active fulfilment' using errcode='23514'; end if;
 update public.order_fulfilment_attempts set state=p_state,menu_step=p_menu_step,screen_text=left(coalesce(p_screen_text,''),2500),
  provider_reference=nullif(left(coalesce(p_provider_reference,''),80),''),error_code=nullif(left(coalesce(p_error_code,''),80),''),
  error_detail=nullif(left(coalesce(p_error_detail,''),500),''),
  finished_at=case when p_state in ('DELIVERED','FAILED','UNKNOWN') then now() else null end
 where business_id=p_business_id and order_id=p_order_id and device_id=p_device_id and attempt_no=p_attempt_no and state in ('CLAIMED','USSD_SENT');
 if not found then raise exception 'Attempt is missing or already finalised' using errcode='23514'; end if;
 update public.orders set status=p_state where id=p_order_id and business_id=p_business_id;
 insert into public.order_events(business_id,order_id,from_status,to_status,actor_user_id,event_source,details)
 values(p_business_id,p_order_id,v_order.status,p_state,v_user_id,'android_fulfilment_worker',
  jsonb_build_object('device_id',p_device_id,'attempt_no',p_attempt_no,'menu_step',p_menu_step,'provider_reference',p_provider_reference,'error_code',p_error_code));
end; $$;
revoke all on function public.record_fulfilment_attempt_result(uuid,uuid,uuid,integer,text,integer,text,text,text,text) from public,anon;
grant execute on function public.record_fulfilment_attempt_result(uuid,uuid,uuid,integer,text,integer,text,text,text,text) to authenticated;

create or replace function public.set_business_fulfilment_controls(p_business_id uuid,p_enabled boolean,p_paused boolean,p_require_manual_pin boolean default true)
returns void language plpgsql security definer set search_path = '' as $$
begin
 if auth.uid() is null or not exists(select 1 from public.business_memberships m where m.business_id=p_business_id
  and m.user_id=auth.uid() and m.is_active and m.role in ('owner','business_admin')) then
  raise exception 'Only a business owner or admin can change fulfilment controls' using errcode='42501'; end if;
 update public.businesses set fulfilment_enabled=p_enabled,fulfilment_paused=p_paused,
  fulfilment_require_manual_pin=p_require_manual_pin,updated_at=now() where id=p_business_id;
end; $$;
revoke all on function public.set_business_fulfilment_controls(uuid,boolean,boolean,boolean) from public,anon;
grant execute on function public.set_business_fulfilment_controls(uuid,boolean,boolean,boolean) to authenticated;
