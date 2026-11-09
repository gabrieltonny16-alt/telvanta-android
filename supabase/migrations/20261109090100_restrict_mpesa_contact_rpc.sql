-- The contact-save routine is trigger-only. Prevent direct RPC execution by API roles.
revoke all on function public.save_mpesa_payer_contact_from_candidate()
  from public, anon, authenticated;
