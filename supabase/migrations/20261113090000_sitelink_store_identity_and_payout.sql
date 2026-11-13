-- SiteLink store identity and payout destination.
-- Existing public links remain valid; new usernames use letters, numbers and underscores.
alter table public.site_links
  drop constraint if exists site_links_slug_check;

alter table public.site_links
  add constraint site_links_slug_check
  check (slug ~ '^[A-Za-z0-9_-]{3,49}$');

create unique index if not exists site_links_slug_ci_unique
  on public.site_links (lower(slug));

alter table public.site_links
  add column if not exists payout_type text not null default 'till'
    check (payout_type in ('till','paybill')),
  add column if not exists till_number text,
  add column if not exists paybill_number text,
  add column if not exists paybill_account_number text;

alter table public.site_links
  add constraint site_links_payout_details_check
  check (
    (payout_type = 'till'
      and till_number ~ '^[0-9]{4,20}$'
      and paybill_number is null
      and paybill_account_number is null)
    or
    (payout_type = 'paybill'
      and paybill_number ~ '^[0-9]{4,20}$'
      and paybill_account_number is not null
      and length(trim(paybill_account_number)) between 1 and 40
      and till_number is null)
  ) not valid;

-- Keep existing rows valid during rollout; new/updated records must satisfy payout details.
alter table public.site_links validate constraint site_links_payout_details_check;
