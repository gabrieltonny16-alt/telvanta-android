create or replace function private.retry_fulfilment_order_after_reconciliation(p_business_id uuid,p_order_id uuid,p_manager_reason text)
returns void language plpgsql security definer set search_path = '' as $$
declare v_user uuid:=auth.uid(); v_order public.orders%rowtype;
begin
 if v_user is null or not exists(select 1 from public.business_memberships m where m.business_id=p_business_id and m.user_id=v_user and m.is_active and m.role in ('owner','business_admin')) then
  raise exception 'Only a business owner or admin can reconcile and retry fulfilment' using errcode='42501'; end if;
 if length(trim(coalesce(p_manager_reason,'')))<10 then raise exception 'Enter a reconciliation note of at least 10 characters' using errcode='22023'; end if;
 select o.* into v_order from public.orders o where o.id=p_order_id and o.business_id=p_business_id and o.status in ('UNKNOWN','FAILED') for update;
 if not found then raise exception 'Only UNKNOWN or FAILED orders can be reconciled for retry' using errcode='23514'; end if;
 if not exists(select 1 from public.payments p where p.business_id=p_business_id and p.order_id=p_order_id and p.verification_status='verified') then
  raise exception 'A verified payment is required before retry' using errcode='23514'; end if;
 if exists(select 1 from public.order_fulfilment_attempts a where a.order_id=p_order_id and a.state in ('CLAIMED','USSD_SENT')) then
  raise exception 'An active fulfilment attempt still exists' using errcode='23514'; end if;
 update public.orders set device_id=null,status='PAID' where id=p_order_id and business_id=p_business_id;
 insert into public.audit_logs(business_id,actor_user_id,action,entity_type,entity_id,details)
 values(p_business_id,v_user,'fulfilment.reconciled_retry','order',p_order_id,
  jsonb_build_object('manager_reason',left(trim(p_manager_reason),500),'previous_status',v_order.status));
end; $$;
revoke all on function private.retry_fulfilment_order_after_reconciliation(uuid,uuid,text) from public,anon;
grant execute on function private.retry_fulfilment_order_after_reconciliation(uuid,uuid,text) to authenticated;
create function public.retry_fulfilment_order_after_reconciliation(p_business_id uuid,p_order_id uuid,p_manager_reason text)
returns void language sql security invoker set search_path = '' as $$ select private.retry_fulfilment_order_after_reconciliation(p_business_id,p_order_id,p_manager_reason); $$;
revoke all on function public.retry_fulfilment_order_after_reconciliation(uuid,uuid,text) from public,anon;
grant execute on function public.retry_fulfilment_order_after_reconciliation(uuid,uuid,text) to authenticated;