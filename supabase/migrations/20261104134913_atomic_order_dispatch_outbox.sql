-- Transactional order event and dispatch outbox.
create unique index if not exists dispatch_outbox_one_order_queued
on public.dispatch_outbox(order_id,event_type)
where event_type='ORDER_QUEUED';

create or replace function private.record_order_transition()
returns trigger language plpgsql security definer
set search_path=public,pg_temp as $$
declare
  v_event_source text:=coalesce(nullif(current_setting('request.jwt.claim.role',true),''),'system');
begin
  if new.status is distinct from old.status then
    new.updated_at:=now();
    if new.status='QUEUED' then
      if new.device_id is null then
        raise exception 'A worker device must be assigned before an order can be queued' using errcode='23514';
      end if;
      if not exists(
        select 1 from public.devices d
        where d.id=new.device_id and d.business_id=new.business_id
          and d.status not in ('paused','revoked')
      ) then
        raise exception 'Assigned device is unavailable or belongs to another business' using errcode='23514';
      end if;
    end if;
    insert into public.order_events(
      business_id,order_id,from_status,to_status,actor_user_id,event_source,details
    ) values(
      new.business_id,new.id,old.status,new.status,(select auth.uid()),v_event_source,
      jsonb_build_object('device_id',new.device_id)
    );
    if new.status='QUEUED' then
      insert into public.dispatch_outbox(business_id,order_id,device_id,event_type,payload)
      values(new.business_id,new.id,new.device_id,'ORDER_QUEUED',jsonb_build_object('order_id',new.id))
      on conflict(order_id,event_type) where event_type='ORDER_QUEUED' do nothing;
    end if;
  end if;
  return new;
end; $$;
revoke all on function private.record_order_transition() from public,anon,authenticated;
drop trigger if exists orders_record_status_transition on public.orders;
create trigger orders_record_status_transition
before update of status on public.orders
for each row execute function private.record_order_transition();
