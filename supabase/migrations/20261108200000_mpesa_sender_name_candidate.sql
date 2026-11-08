-- Preserve the sender's full display name as a parsed candidate.
-- This is informational evidence only and does not alter payment verification.
alter table public.payment_notification_candidates
  add column if not exists sender_name_candidate text;

alter table public.payment_notification_candidates
  add constraint payment_notification_sender_name_length
  check (sender_name_candidate is null or char_length(sender_name_candidate) <= 120);
