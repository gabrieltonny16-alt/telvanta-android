-- Require a named manager attestation and exact offer/payment-path checks
-- before a provider menu recipe can be enabled for automatic fulfilment.

alter table public.payment_offer_rules
  add column if not exists menu_flow_verified_by uuid references auth.users(id) on delete restrict,
  add column if not exists menu_flow_verified_note text,
  add column if not exists menu_flow_version integer not null default 1
    check (menu_flow_version > 0);

create or replace function private.validate_payment_offer_rule_recipe()
returns trigger
language plpgsql
set search_path = public, private, pg_temp
as $$
declare
  v_offer_name text;
  v_validity text;
  v_step jsonb;
  v_action text;
  v_match text;
  v_value text;
  v_offer_step boolean := false;
  v_airtime_step boolean := false;
  v_option_number integer;
begin
  if tg_op = 'UPDATE' and (
    new.menu_entry_code is distinct from old.menu_entry_code
    or new.menu_steps is distinct from old.menu_steps
    or new.offer_id is distinct from old.offer_id
    or new.amount_minor is distinct from old.amount_minor
  ) then
    if new.is_enabled then
      raise exception 'Save a changed recipe disabled first, then verify the new menu flow before enabling it.';
    end if;
    new.menu_flow_version := old.menu_flow_version + 1;
    new.menu_flow_verified_at := null;
    new.menu_flow_verified_by := null;
    new.menu_flow_verified_note := null;
  end if;

  if not new.is_enabled then
    return new;
  end if;

  if auth.uid() is null
     or new.menu_flow_verified_by is distinct from auth.uid()
     or not private.has_business_role(new.business_id, array['owner','business_admin']) then
    raise exception 'Only an authenticated business owner or administrator can verify and enable a menu recipe.';
  end if;

  if new.menu_flow_verified_at is null
     or length(trim(coalesce(new.menu_flow_verified_note, ''))) < 12 then
    raise exception 'A verification timestamp and manager test note are required before enabling this recipe.';
  end if;

  select o.name, o.validity_label
    into v_offer_name, v_validity
  from public.offers o
  where o.business_id = new.business_id
    and o.id = new.offer_id
    and o.is_active = true;

  if v_offer_name is null or coalesce(trim(v_validity), '') = '' then
    raise exception 'The selected active offer must have an exact name and validity label.';
  end if;

  for v_step in select value from jsonb_array_elements(new.menu_steps)
  loop
    if jsonb_typeof(v_step) <> 'object' then
      raise exception 'Every recipe step must be a JSON object.';
    end if;

    v_action := lower(trim(coalesce(v_step->>'action', '')));
    v_match := trim(coalesce(v_step->>'match_text', ''));
    v_value := trim(coalesce(v_step->>'value', ''));

    if v_action not in ('reply', 'tap_text', 'manual_pin') or v_match = '' then
      raise exception 'Every recipe step needs a screen phrase and a supported action.';
    end if;

    if v_step ?| array['pin','sim_pin','pin_value','password','secret'] then
      raise exception 'PINs, passwords and secrets must never be stored in menu recipes.';
    end if;

    if v_action = 'manual_pin' and v_value <> '' then
      raise exception 'A manual PIN step must not contain a value.';
    end if;

    if v_action = 'reply' and v_value <> '{recipient_phone}' and v_value <> '{payer_phone}' then
      if v_value !~ '^[0-9]{1,2}$' then
        raise exception 'Numeric USSD replies must be menu option numbers from 1 to 30; phone replies must use a phone placeholder.';
      end if;
      v_option_number := v_value::integer;
      if v_option_number < 1 or v_option_number > 30 then
        raise exception 'Numeric USSD replies must be menu option numbers from 1 to 30.';
      end if;
    end if;

    if position(lower(v_offer_name) in lower(v_match)) > 0
       and position(lower(v_validity) in lower(v_match)) > 0
       and v_action in ('reply','tap_text') then
      if v_action = 'tap_text'
         and not (
           position(lower(v_offer_name) in lower(v_value)) > 0
           and position(lower(v_validity) in lower(v_value)) > 0
         ) then
        raise exception 'The offer tap value must contain the exact offer name and validity.';
      end if;
      v_offer_step := true;
    end if;

    if position('buy with airtime' in lower(v_match)) > 0
       and v_action in ('reply','tap_text') then
      if v_action = 'tap_text' and lower(v_value) <> 'buy with airtime' then
        raise exception 'The Buy with Airtime tap value must match that exact menu option.';
      end if;
      v_airtime_step := true;
    end if;
  end loop;

  if not v_offer_step then
    raise exception 'A separate step must match the exact offer name and validity together.';
  end if;
  if not v_airtime_step then
    raise exception 'A separate step must select Buy with Airtime.';
  end if;

  return new;
end;
$$;

revoke all on function private.validate_payment_offer_rule_recipe()
  from public, anon, authenticated;

drop trigger if exists payment_offer_rule_validate_recipe on public.payment_offer_rules;
create trigger payment_offer_rule_validate_recipe
before insert or update on public.payment_offer_rules
for each row execute function private.validate_payment_offer_rule_recipe();
