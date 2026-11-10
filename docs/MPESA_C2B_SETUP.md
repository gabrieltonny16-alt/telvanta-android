# Safaricom C2B Buy Goods callback setup

## What is deployed

The Supabase Edge Function `mpesa-c2b-callback` is deployed. It accepts Safaricom C2B validation and confirmation callbacks through separate `mode` values. JWT verification is disabled because Safaricom does not send a Supabase user JWT; the endpoint instead requires a private callback token and checks that the incoming shortcode is an active Till registered to the business.

This is the **C2B confirmation** path for customers paying the Till from their own M-PESA menu. It does not send an STK Push and does not replace the separate Sambaza subscription flow.

## One-time setup

1. In BingwaPulse, open **Payment Inbox** and save the exact Buy Goods Till number for the business.
2. In Supabase Dashboard, open **Project Settings → Edge Functions → Secrets** and set `MPESA_C2B_CALLBACK_TOKEN` to a long, random secret (at least 32 random bytes). Do not put the secret in GitHub, app source, or customer-visible messages.
3. Replace `YOUR_SECRET_TOKEN` in both URLs below with that exact secret.
4. Register the Validation URL and Confirmation URL against the Till in the Safaricom Daraja C2B URL registration flow. Use the correct sandbox or production environment and the same shortcode saved in BingwaPulse.
5. Safaricom must confirm the URL registration before the callback can be relied on.

Validation URL:

```text
https://tuuhucisljlaaovtnbeu.supabase.co/functions/v1/mpesa-c2b-callback?mode=validate&token=YOUR_SECRET_TOKEN
```

Confirmation URL:

```text
https://tuuhucisljlaaovtnbeu.supabase.co/functions/v1/mpesa-c2b-callback?mode=confirm&token=YOUR_SECRET_TOKEN
```

## Matching and safety

- Only a callback with the private token and an active registered Till is accepted as trusted C2B evidence.
- The receipt reference, amount, shortcode and timestamp are validated and deduplicated.
- Safaricom C2B can return a full, masked or hashed MSISDN depending on version/configuration. BingwaPulse only treats a complete Kenyan mobile number as a usable payer phone; masked/hashed values are retained as raw evidence, never mistaken for a phone number.
- The callback first attempts to match a single recent SiteLink `AWAITING_PAYMENT` order by exact amount and, when available, exact payer phone. It preserves the recipient number selected at checkout.
- If the callback does not expose a usable payer number, only one matching pending SiteLink order in the time window can be matched. Multiple same-amount orders are left for reconciliation; BingwaPulse does not guess.
- If no pre-created SiteLink order exists and the payer number is unavailable, the verified receipt remains unallocated.
- Android notification text alone remains unverified and cannot automatically mark an order paid.
- After a trusted match, the order moves to `PAID`; the worker still requires a current offer, verified menu recipe, authorized online device and fulfilment controls before it can claim the order.

## Current activation status

The callback function and database matching logic are deployed. It is **not yet active for real Till payments** until the callback token is saved, the correct Till is saved in Payment Inbox, and Safaricom confirms the C2B URL registration. No live payment has been initiated as part of this setup.
