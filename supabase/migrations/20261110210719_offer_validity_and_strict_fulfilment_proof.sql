-- Require an explicit offer validity label, a screen match for the exact offer + validity,
-- and a Buy with Airtime action before an offer rule can claim an order.
alter table public.offers add column if not exists validity_label text;

create or replace function private.guard_fulfilment_order_claim()
returns trigger language plpgsql security definer set search_path = ''
as $function$
declare v_offer public.offers%rowtype; v_provider public.providers%rowtype;
v_rule public.payment_offer_rules%rowtype; v_has_exact_offer_step boolean; v_has_airtime_step boolean;
begin
 if new.status not in ('QUEUED','CLAIMED') then return new; end if;
 select off.* into v_offer from public.offers off where off.business_id=new.business_id and off.id=new.offer_id;
 if not found or not v_offer.is_active or (v_offer.valid_from is not null and v_offer.valid_from>now())
 or (v_offer.valid_until is not null and v_offer.valid_until<=now())
 or (v_offer.stale_after is not null and v_offer.stale_after<=now())
 or nullif(trim(v_offer.validity_label),'') is null then
  raise exception 'Offer is inactive, expired, stale, or missing its exact validity label' using errcode='23514';
 end if;
 select pr.* into v_provider from public.providers pr where pr.business_id=new.business_id
 and pr.id=v_offer.provider_id and pr.status='active' and pr.adapter_key='ussd_assisted';
 if not found then raise exception 'Offer provider is not active for USSD fulfilment' using errcode='23514'; end if;
 if not exists(select 1 from public.payments pay where pay.business_id=new.business_id
 and pay.order_id=new.id and pay.verification_status='verified') then
  raise exception 'A verified M-PESA payment is required before fulfilment' using errcode='23514';
 end if;
 select r.* into v_rule from public.payment_offer_rules r where r.business_id=new.business_id
 and r.offer_id=new.offer_id and r.amount_minor=new.customer_price_kes::bigint*100
 and r.is_enabled and r.menu_flow_verified_at is not null and jsonb_array_length(r.menu_steps)>0
 order by r.updated_at desc limit 1;
 if not found then raise exception 'No enabled, verified menu rule matches this exact offer and customer amount' using errcode='23514'; end if;
 select
 exists(select 1 from jsonb_array_elements(v_rule.menu_steps) step
  where position(lower(v_offer.name) in lower(coalesce(step->>'match_text','')))>0
  and position(lower(v_offer.validity_label) in lower(coalesce(step->>'match_text','')))>0),
 exists(select 1 from jsonb_array_elements(v_rule.menu_steps) step
  where lower(coalesce(step->>'match_text','')) like '%buy with airtime%'
  and lower(coalesce(step->>'action','')) in ('reply','tap_text'))
 into v_has_exact_offer_step,v_has_airtime_step;
 if not v_has_exact_offer_step then raise exception 'Menu recipe must match the exact offer name and validity shown by the provider' using errcode='23514'; end if;
 if not v_has_airtime_step then raise exception 'Menu recipe must explicitly select Buy with Airtime' using errcode='23514'; end if;
 return new;
end;
$function$;
revoke all on function private.guard_fulfilment_order_claim() from public,anon,authenticated;
drop trigger if exists a_guard_fulfilment_order_claim on public.orders;
create trigger a_guard_fulfilment_order_claim before update of status on public.orders
for each row when (new.status in ('QUEUED','CLAIMED'))
execute function private.guard_fulfilment_order_claim();

create or replace function private.record_fulfilment_attempt_result(
 p_business_id uuid,p_order_id uuid,p_device_id uuid,p_attempt_no integer,p_state text,
 p_menu_step integer default 0,p_screen_text text default null,p_provider_reference text default null,
 p_error_code text default null,p_error_detail text default null
)
returns void language plpgsql security definer set search_path=''
as $function$
declare
 v_user_id uuid:=auth.uid(); v_order public.orders%rowtype; v_offer_name text;
 v_success_text text:=coalesce(p_screen_text,'');
begin
 if v_user_id is null then raise exception 'Authentication required' using errcode='42501'; end if;
 if p_state not in ('USSD_SENT','DELIVERED','FAILED','UNKNOWN') then raise exception 'Unsupported fulfilment state' using errcode='22023'; end if;
 if p_menu_step<0 then raise exception 'Invalid menu step' using errcode='22023'; end if;
 select o.* into v_order from public.orders o
 join public.devices d on d.id=o.device_id and d.business_id=o.business_id
 where o.id=p_order_id and o.business_id=p_business_id and o.device_id=p_device_id
 and d.assigned_user_id=v_user_id and d.status='online' for update of o;
 if not found then raise exception 'Order is not assigned to this authorised worker device' using errcode='42501'; end if;
 select off.name into v_offer_name from public.offers off where off.business_id=p_business_id and off.id=v_order.offer_id;
 if p_state='DELIVERED' then
  if v_success_text !~* '(transaction[[:space:]]+(was[[:space:]]+)?successful|purchase[[:space:]]+(was[[:space:]]+)?successful|successfully[[:space:]]+purchased|bundle[[:space:]]+(has[[:space:]]+been[[:space:]]+)?activated)'
  or position(lower(v_offer_name) in lower(v_success_text))=0 then
   raise exception 'Delivery requires explicit carrier success language naming the exact purchased offer' using errcode='23514';
  end if;
 end if;
 if v_order.status not in ('CLAIMED','USSD_SENT') then raise exception 'Order is not in active fulfilment' using errcode='23514'; end if;
 update public.order_fulfilment_attempts set state=p_state,menu_step=p_menu_step,
 screen_text=left(coalesce(p_screen_text,''),2500),provider_reference=nullif(left(coalesce(p_provider_reference,''),80),''),
 error_code=nullif(left(coalesce(p_error_code,''),80),''),error_detail=nullif(left(coalesce(p_error_detail,''),500),''),
 finished_at=case when p_state in ('DELIVERED','FAILED','UNKNOWN') then now() else null end
 where business_id=p_business_id and order_id=p_order_id and device_id=p_device_id and attempt_no=p_attempt_no
 and state in ('CLAIMED','USSD_SENT');
 if not found then raise exception 'Attempt is missing or already finalised' using errcode='23514'; end if;
 update public.orders set status=p_state where id=p_order_id and business_id=p_business_id;
 insert into public.order_events(business_id,order_id,from_status,to_status,actor_user_id,event_source,details)
 values(p_business_id,p_order_id,v_order.status,p_state,v_user_id,'android_fulfilment_worker',
 jsonb_build_object('device_id',p_device_id,'attempt_no',p_attempt_no,'menu_step',p_menu_step,
 'provider_reference',p_provider_reference,'error_code',p_error_code));
end;
$function$;
revoke all on function private.record_fulfilment_attempt_result(uuid,uuid,uuid,integer,text,integer,text,text,text,text) from public,anon;
grant execute on function private.record_fulfilment_attempt_result(uuid,uuid,uuid,integer,text,integer,text,text,text,text) to authenticated;
