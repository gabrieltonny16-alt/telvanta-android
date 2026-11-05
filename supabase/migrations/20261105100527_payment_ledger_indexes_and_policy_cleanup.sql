-- Performance and RLS policy cleanup for payment ingress and ledger.
-- Add covering indexes for composite foreign keys and avoid duplicate SELECT policies.

create index if not exists balance_ledger_batches_business_order_idx
  on public.balance_ledger_batches (business_id, order_id);
create index if not exists balance_ledger_batches_business_provider_idx
  on public.balance_ledger_batches (business_id, provider_id);
create index if not exists balance_ledger_batches_business_agent_idx
  on public.balance_ledger_batches (business_id, agent_user_id);

create index if not exists balance_ledger_entries_business_batch_idx
  on public.balance_ledger_entries (business_id, batch_id);

create index if not exists payment_ingress_events_business_matched_order_idx
  on public.payment_ingress_events (business_id, matched_order_id);

drop policy if exists "owners manage payment ingress channels"
  on public.payment_ingress_channels;

create policy "owners insert payment ingress channels"
  on public.payment_ingress_channels for insert to authenticated
  with check (private.has_business_role(business_id, array['owner','business_admin']));

create policy "owners update payment ingress channels"
  on public.payment_ingress_channels for update to authenticated
  using (private.has_business_role(business_id, array['owner','business_admin']))
  with check (private.has_business_role(business_id, array['owner','business_admin']));

create policy "owners delete payment ingress channels"
  on public.payment_ingress_channels for delete to authenticated
  using (private.has_business_role(business_id, array['owner','business_admin']));
