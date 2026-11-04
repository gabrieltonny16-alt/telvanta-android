# BingwaPulse Ecosystem

**Power Every Connection.**

BingwaPulse is an independent, multi-agent telecom business platform for Android, management web, SiteLink customer storefronts and the ConnectPulse companion.

## Current implementation

- Existing Kotlin / Jetpack Compose Android app and ConnectPulse Android module.
- Supabase project created and connected to the Android build using a public publishable key.
- Email/password sign-in and account creation entry point.
- First-business onboarding with owner membership bootstrap.
- Session data encrypted with Android Keystore AES-GCM.
- Supabase backend foundation with 15 RLS-protected tables for businesses, team roles, providers, offers, customers, devices, orders, payments, audit and dispatch.
- Realtime publication configured for orders, order events, offers, agent offers, devices and SiteLink configuration.
- Transactional order status events and a deduplicated dispatch outbox event when an order becomes QUEUED.

## Build

GitHub Actions builds both Android applications on pushes to `feature/vecta-branding`. Check the latest workflow run before treating an APK as ready.

## Important operational boundaries

- A payment notification is evidence, not proof of verified payment.
- The dispatch outbox is durable but FCM delivery is not yet configured.
- Worker claim/acknowledgement and reconciliation still need implementation.
- USSD screen observation is not automated bundle purchasing.
- No live payment collection, STK Push, paid messaging or automatic fulfilment is enabled.
- Management website, public SiteLink storefront and ConnectPulse recovery are not yet complete.
- No service-role or secret API key may be placed in Android or browser code.
- Do not retry an order whose fulfilment state is UNKNOWN without reconciliation.

See [Backend Foundation](docs/BACKEND_FOUNDATION.md) for the actual database migrations, security checks and remaining work.
