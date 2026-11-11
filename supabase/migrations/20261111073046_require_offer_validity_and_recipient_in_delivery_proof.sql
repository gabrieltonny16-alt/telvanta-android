create or replace function private.record_fulfilment_attempt_result(
 p_business_id uuid,p_order_id uuid,p_device_id uuid,p_attempt_no integer,p_state text,
 p_menu_step integer default 0,p_screen_text text default null,p_provider_reference text default null,
 p_error_code text default null,p_error_detail text default null
)
returns void language plpgsql security definer set search_path=''
as $function$
declare
 v_user_id uuid:=auth.uid(); v_order public.orders%rowtype;
 v_offer_name text; v_validity_label text;
 v_recipient_digits text; v_screen_digits text;
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
 select off.name, off.validity_label into v_offer_name, v_validity_label
 from public.offers off where off.business_id=p_business_id and off.id=v_order.offer_id;
 v_recipient_digits:=regexp_replace(coalesce(v_order.recipient_phone,''),'[^0-9]','','g');
 v_screen_digits:=regexp_replace(v_success_text,'[^0-9]','','g');
 if p_state='DELIVERED' then
  if v_success_text !~* '(transaction[[:space:]]+(was[[:space:]]+)?successful|purchase[[:space:]]+(was[[:space:]]+)?successful|successfully[[:space:]]+purchased|bundle[[:space:]]+(has[[:space:]]+been[[:space:]]+)?activated)'
  or nullif(trim(v_offer_name),'') is null
  or position(lower(trim(v_offer_name)) in lower(v_success_text))=0
  or nullif(trim(v_validity_label),'') is null
  or position(lower(trim(v_validity_label)) in lower(v_success_text))=0
  or length(v_recipient_digits)<9
  or position(right(v_recipient_digits,9) in v_screen_digits)=0 then
   raise exception 'Delivery requires carrier success evidence containing the exact offer, validity and intended recipient' using errcode='23514';
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
