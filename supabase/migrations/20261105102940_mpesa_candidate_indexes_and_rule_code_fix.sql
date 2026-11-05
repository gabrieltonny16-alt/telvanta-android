-- Cover notification / offer-rule foreign keys and correct the USSD dial-string regex.
create index if not exists payment_notification_candidates_business_ingress_idx
  on public.payment_notification_candidates (business_id, matched_ingress_event_id);

create index if not exists payment_notification_candidates_source_user_idx
  on public.payment_notification_candidates (source_user_id);

create index if not exists payment_offer_rules_business_offer_idx
  on public.payment_offer_rules (business_id, offer_id);

create index if not exists payment_offer_rules_created_by_idx
  on public.payment_offer_rules (created_by);

alter table public.payment_offer_rules
  drop constraint if exists payment_offer_rules_menu_entry_code_check;

alter table public.payment_offer_rules
  add constraint payment_offer_rules_menu_entry_code_check
  check (menu_entry_code ~ '^[*][0-9*]+#$');
