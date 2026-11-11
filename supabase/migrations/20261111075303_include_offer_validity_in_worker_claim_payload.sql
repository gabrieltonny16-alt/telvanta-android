-- Include the exact offer validity in the worker payload so Android can
-- verify the live provider screen before it selects the offer.
create or replace function public.claim_next_fulfilment_order(p_business_id uuid, p_device_id uuid)
returns jsonb
language plpgsql
set search_path = ''
as $function$
declare
  v_job jsonb;
  v_validity_label text;
begin
  v_job := private.claim_next_fulfilment_order(p_business_id, p_device_id);
  if v_job is null then
    return null;
  end if;

  select off.validity_label into v_validity_label
  from public.offers off
  where off.business_id = p_business_id
    and off.id = (v_job->>'offer_id')::uuid;

  return v_job || jsonb_build_object('validity_label', v_validity_label);
end;
$function$;

revoke all on function public.claim_next_fulfilment_order(uuid, uuid) from public, anon;
grant execute on function public.claim_next_fulfilment_order(uuid, uuid) to authenticated;