# BingwaPulse backend foundation

## Connected project
- Supabase project: `BingwaPulse`
- Project reference: `tuuhucisljlaaovtnbeu`
- Region: `eu-west-2` (London)
- Project URL: `https://tuuhucisljlaaovtnbeu.supabase.co`
- Project creation quote: $0/month at creation. Recheck plan/usage before enabling any paid add-on.
- CLI project link: `supabase/config.toml`

The Android app uses only the Supabase publishable key. No service-role or secret key is included in the app.

## Database migrations
1. `20261104134553_bingwapulse_core_foundation.sql` — 15 tenant-aware core tables, role model, RLS, and initial Realtime publication.
2. `20261104134623_harden_core_rls_and_indexes.sql` — moves SECURITY DEFINER helpers into the non-exposed `private` schema, adds owner bootstrap trigger and foreign-key indexes, consolidates manager policies.
3. `20261104134913_atomic_order_dispatch_outbox.sql` — records order state transitions and creates one deduplicated outbox event in the same database transaction when an order becomes QUEUED.

## Core records
Businesses, business memberships, profiles, providers, offers, agent-specific offers/prices, customers, authorized devices, SiteLink configuration, orders, payments, order events, dispatch outbox, device heartbeats and audit logs.

## Security and verification
- RLS is enabled on all 15 public tables.
- No SECURITY DEFINER functions are exposed in the public API schema.
- Tenant membership/role checks use private-schema functions.
- Supabase security advisor returned no findings after hardening.
- Six tables are in the Realtime publication: orders, order events, offers, agent offers, devices and SiteLink configuration.
- Dispatch outbox payload contains only the order ID; it does not include payer/recipient phone numbers or payment details.
- An order cannot enter QUEUED without an assigned device belonging to the same business and not paused/revoked.
- A partial unique index prevents duplicate ORDER_QUEUED outbox events for one order.

## Android account bootstrap
The Android app now has an email/password sign-in and sign-up entry point, a first-business setup step, and encrypted session storage using Android Keystore AES-GCM. Business creation is performed as the authenticated user; a database trigger adds the creator as owner.

## Not yet operational
- FCM credentials/worker push dispatcher are not configured. The outbox is durable but does not yet send pushes.
- Atomic worker claim/acknowledgement and lease expiry are not yet implemented.
- M-PESA notification candidates are not verified against a trusted Safaricom source.
- No live payment collection, STK Push, provider API or automated bundle fulfilment is enabled.
- Public SiteLink and ConnectPulse Edge Functions are deployed. Both use server-side credentials; no service-role key is included in either Android or browser clients.
- The USSD accessibility service still observes screens; it is not an automatic purchasing engine.
- Management website is implemented in web/ with authenticated workspace, overview, orders, offers, customers, payment review, automation readiness and SiteLink management.
- Public SiteLink storefront is implemented in web/sitelink/ and uses the deployed sitelink-storefront Edge Function. It exposes only active, published offers and creates idempotent AWAITING_PAYMENT order requests. It does not collect or verify payments or trigger fulfilment.
- GitHub Pages deployment is configured but the repository Pages site still needs to be enabled in repository Settings before the deployment workflow can publish it.
- ConnectPulse now has one-time pairing challenges, owner approval, device-specific hashed credentials, Android Keystore storage and a 15-minute periodic heartbeat. The companion reports its own app/battery/network state; cross-app worker diagnostics and safe recovery actions are still incomplete.
- Android authentication has been added to source but must pass CI and be exercised with a real account before it is considered verified.

## Safety
No live payments, paid messaging, live fulfilment or automated USSD purchasing is enabled. The database is a foundation; it is not evidence of a working provider integration.
