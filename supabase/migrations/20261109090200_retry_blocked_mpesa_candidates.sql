-- Allow managers to retry an automatically blocked candidate after fixing Till/offer configuration.
-- The staging trigger still creates REVIEW_REQUIRED orders only; it never verifies or dispatches.

drop trigger if exists payment_notification_candidate_stage_order
  on public.payment_notification_candidates;

create trigger payment_notification_candidate_stage_order
after insert or update of staging_status
on public.payment_notification_candidates
for each row execute function private.stage_mpesa_notification_candidate();

create or replace function public.retry_mpesa_notification_candidate(
  p_business_id uuid,
  p_candidate_id uuid
)
returns void
language plpgsql
security definer
set search_path = public, private, pg_temp
as $$
declare
  v_candidate public.payment_notification_candidates%rowtype;
begin
  if not private.has_business_role(
    p_business_id, array['owner','business_admin','agent_manager']
  ) then
    raise exception 'Only an authorized business manager can retry payment staging'
      using errcode = '42501';
  end if;

  select c.* into v_candidate
  from public.payment_notification_candidates c
  where c.id = p_candidate_id
    and c.business_id = p_business_id
  for update;

  if not found or v_candidate.staging_status <> 'blocked' then
    raise exception 'Only blocked payment candidates can be retried'
      using errcode = 'P0001';
  end if;

  update public.payment_notification_candidates
    set staging_status = 'not_staged',
        staging_reason = null,
        staged_payment_id = null,
        staged_order_id = null
  where id = p_candidate_id
    and business_id = p_business_id;
end;
$$;

revoke all on function public.retry_mpesa_notification_candidate(uuid,uuid)
  from public, anon;
grant execute on function public.retry_mpesa_notification_candidate(uuid,uuid)
  to authenticated;
