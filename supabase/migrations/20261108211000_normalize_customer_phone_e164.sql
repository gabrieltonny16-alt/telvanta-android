-- Canonicalize customer phone numbers before the business/phone unique key is checked.
-- Kenyan local 07XXXXXXXX / 01XXXXXXXX and 254XXXXXXXXX forms become +254XXXXXXXXX.
create or replace function public.normalize_customer_phone_e164()
returns trigger
language plpgsql
set search_path = pg_catalog, public
as $$
declare
  v_digits text;
begin
  v_digits := regexp_replace(coalesce(new.phone_e164, ''), '[^0-9]', '', 'g');

  if length(v_digits) = 10 and left(v_digits, 1) = '0' then
    new.phone_e164 := '+254' || substring(v_digits from 2);
  elsif length(v_digits) = 12 and left(v_digits, 3) = '254' then
    new.phone_e164 := '+' || v_digits;
  elsif length(v_digits) = 9 and left(v_digits, 1) in ('1', '7') then
    new.phone_e164 := '+254' || v_digits;
  elsif length(v_digits) between 8 and 15 and left(v_digits, 1) <> '0' then
    new.phone_e164 := '+' || v_digits;
  else
    raise exception 'Enter a valid phone number, including its country code.';
  end if;

  return new;
end;
$$;

drop trigger if exists trg_normalize_customer_phone_e164 on public.customers;
create trigger trg_normalize_customer_phone_e164
before insert or update of phone_e164 on public.customers
for each row
execute function public.normalize_customer_phone_e164();

-- Normalize existing records too, so the unique key compares canonical numbers.
update public.customers
set phone_e164 = phone_e164
where phone_e164 is not null;
