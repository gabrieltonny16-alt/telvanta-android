-- Keep the exposed RPC wrappers SECURITY INVOKER. Privileged checks stay in private-schema routines.
alter function public.review_mpesa_notification_candidate(uuid,uuid,text) set schema private;
alter function private.review_mpesa_notification_candidate(uuid,uuid,text)
  rename to review_mpesa_notification_candidate_internal;
revoke all on function private.review_mpesa_notification_candidate_internal(uuid,uuid,text)
  from public, anon, authenticated;
grant execute on function private.review_mpesa_notification_candidate_internal(uuid,uuid,text)
  to authenticated;

create function public.review_mpesa_notification_candidate(
  p_business_id uuid,
  p_candidate_id uuid,
  p_decision text
)
returns table(candidate_id uuid, order_id uuid, payment_status text, order_status text)
language sql
security invoker
set search_path = public, private, pg_temp
as $$
  select * from private.review_mpesa_notification_candidate_internal(
    p_business_id, p_candidate_id, p_decision
  );
$$;
revoke all on function public.review_mpesa_notification_candidate(uuid,uuid,text)
  from public, anon;
grant execute on function public.review_mpesa_notification_candidate(uuid,uuid,text)
  to authenticated;

alter function public.retry_mpesa_notification_candidate(uuid,uuid) set schema private;
alter function private.retry_mpesa_notification_candidate(uuid,uuid)
  rename to retry_mpesa_notification_candidate_internal;
revoke all on function private.retry_mpesa_notification_candidate_internal(uuid,uuid)
  from public, anon, authenticated;
grant execute on function private.retry_mpesa_notification_candidate_internal(uuid,uuid)
  to authenticated;

create function public.retry_mpesa_notification_candidate(
  p_business_id uuid,
  p_candidate_id uuid
)
returns void
language sql
security invoker
set search_path = public, private, pg_temp
as $$
  select private.retry_mpesa_notification_candidate_internal(
    p_business_id, p_candidate_id
  );
$$;
revoke all on function public.retry_mpesa_notification_candidate(uuid,uuid)
  from public, anon;
grant execute on function public.retry_mpesa_notification_candidate(uuid,uuid)
  to authenticated;
