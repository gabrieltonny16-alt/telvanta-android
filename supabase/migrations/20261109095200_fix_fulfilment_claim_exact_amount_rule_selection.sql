create or replace function private.claim_next_fulfilment_order(p_business_id uuid,p_device_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
 v_user_id uuid:=auth.uid(); v_device public.devices%rowtype; v_order public.orders%rowtype;
 v_rule public.payment_offer_rules%rowtype; v_offer public.offers%rowtype; v_provider public.providers%rowtype; v_attempt_no integer;
begin
 if v_user_id is null then raise exception 'Authentication required' using errcode='42501'; end if;
 select d.* into v_device from public.devices d where d.id=p_device_id and d.business_id=p_business_id and d.assigned_user_id=v_user_id and d.status='online' for update;
 if not found then raise exception 'Device is not approved, online, and assigned to this worker' using errcode='42501'; end if;
 if not exists(select 1 from public.business_memberships m where m.business_id=p_business_id and m.user_id=v_user_id and m.is_active and m.role in ('owner','business_admin','agent_manager')) then raise exception 'Worker is not authorised for this business' using errcode='42501'; end if;
 if not exists(select 1 from public.businesses b where b.id=p_business_id and b.fulfilment_enabled and not b.fulfilment_paused) then return null; end if;
 select o.* into v_order from public.orders o
 join public.payments pay on pay.business_id=o.business_id and pay.order_id=o.id and pay.verification_status='verified'
 join public.offers off on off.business_id=o.business_id and off.id=o.offer_id and off.is_active
 join public.providers pr on pr.business_id=off.business_id and pr.id=off.provider_id and pr.status='active' and pr.adapter_key='ussd_assisted'
 join public.payment_offer_rules r on r.business_id=o.business_id and r.offer_id=off.id and r.amount_minor=(o.customer_price_kes::bigint*100) and r.is_enabled and r.menu_flow_verified_at is not null and jsonb_array_length(r.menu_steps)>0
 where o.business_id=p_business_id and o.status='PAID' and o.device_id is null
 order by o.created_at asc for update of o skip locked limit 1;
 if not found then return null; end if;
 select r.* into v_rule from public.payment_offer_rules r where r.business_id=v_order.business_id and r.offer_id=v_order.offer_id and r.amount_minor=(v_order.customer_price_kes::bigint*100) and r.is_enabled and r.menu_flow_verified_at is not null limit 1;
 select off.* into v_offer from public.offers off where off.business_id=v_order.business_id and off.id=v_order.offer_id;
 select pr.* into v_provider from public.providers pr where pr.business_id=v_offer.business_id and pr.id=v_offer.provider_id;
 select coalesce(max(a.attempt_no),0)+1 into v_attempt_no from public.order_fulfilment_attempts a where a.order_id=v_order.id;
 update public.orders set device_id=p_device_id,status='QUEUED' where id=v_order.id and business_id=p_business_id and status='PAID';
 update public.orders set status='CLAIMED' where id=v_order.id and business_id=p_business_id and status='QUEUED';
 insert into public.order_fulfilment_attempts(business_id,order_id,device_id,attempt_no,state) values(p_business_id,v_order.id,p_device_id,v_attempt_no,'CLAIMED');
 update public.dispatch_outbox set status='sent',sent_at=now(),lease_expires_at=null where order_id=v_order.id and event_type='ORDER_QUEUED' and status in ('pending','processing');
 return jsonb_build_object('order_id',v_order.id,'business_id',v_order.business_id,'device_id',p_device_id,'attempt_no',v_attempt_no,'recipient_phone',v_order.recipient_phone,'payer_phone',v_order.payer_phone,'customer_price_kes',v_order.customer_price_kes,'offer_id',v_offer.id,'offer_name',v_offer.name,'offer_category',v_offer.category,'provider_id',v_provider.id,'provider_name',v_provider.name,'ussd_code',v_rule.menu_entry_code,'menu_steps',v_rule.menu_steps,'require_manual_pin',(select b.fulfilment_require_manual_pin from public.businesses b where b.id=p_business_id));
end; $$;