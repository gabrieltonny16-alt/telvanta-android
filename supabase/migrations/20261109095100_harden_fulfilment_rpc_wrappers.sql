-- Keep privileged fulfilment implementation out of the exposed public schema.
alter function public.claim_next_fulfilment_order(uuid,uuid) set schema private;
alter function public.record_fulfilment_attempt_result(uuid,uuid,uuid,integer,text,integer,text,text,text,text) set schema private;
alter function public.set_business_fulfilment_controls(uuid,boolean,boolean,boolean) set schema private;
revoke all on function private.claim_next_fulfilment_order(uuid,uuid) from public,anon;
revoke all on function private.record_fulfilment_attempt_result(uuid,uuid,uuid,integer,text,integer,text,text,text,text) from public,anon;
revoke all on function private.set_business_fulfilment_controls(uuid,boolean,boolean,boolean) from public,anon;
grant execute on function private.claim_next_fulfilment_order(uuid,uuid) to authenticated;
grant execute on function private.record_fulfilment_attempt_result(uuid,uuid,uuid,integer,text,integer,text,text,text,text) to authenticated;
grant execute on function private.set_business_fulfilment_controls(uuid,boolean,boolean,boolean) to authenticated;
create function public.claim_next_fulfilment_order(p_business_id uuid,p_device_id uuid)
returns jsonb language sql security invoker set search_path = '' as $$ select private.claim_next_fulfilment_order(p_business_id,p_device_id); $$;
create function public.record_fulfilment_attempt_result(
 p_business_id uuid,p_order_id uuid,p_device_id uuid,p_attempt_no integer,p_state text,
 p_menu_step integer default 0,p_screen_text text default null,p_provider_reference text default null,
 p_error_code text default null,p_error_detail text default null
) returns void language sql security invoker set search_path = '' as $$
 select private.record_fulfilment_attempt_result(p_business_id,p_order_id,p_device_id,p_attempt_no,p_state,p_menu_step,p_screen_text,p_provider_reference,p_error_code,p_error_detail);
$$;
create function public.set_business_fulfilment_controls(p_business_id uuid,p_enabled boolean,p_paused boolean,p_require_manual_pin boolean default true)
returns void language sql security invoker set search_path = '' as $$ select private.set_business_fulfilment_controls(p_business_id,p_enabled,p_paused,p_require_manual_pin); $$;
revoke all on function public.claim_next_fulfilment_order(uuid,uuid) from public,anon;
revoke all on function public.record_fulfilment_attempt_result(uuid,uuid,uuid,integer,text,integer,text,text,text,text) from public,anon;
revoke all on function public.set_business_fulfilment_controls(uuid,boolean,boolean,boolean) from public,anon;
grant execute on function public.claim_next_fulfilment_order(uuid,uuid) to authenticated;
grant execute on function public.record_fulfilment_attempt_result(uuid,uuid,uuid,integer,text,integer,text,text,text,text) to authenticated;
grant execute on function public.set_business_fulfilment_controls(uuid,boolean,boolean,boolean) to authenticated;
