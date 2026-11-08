# BingwaPulse Android

**Your Bundles. Your Business. Automated.**

Native Android workspace for telecom distribution operations. This repository is the Android client in the BingwaPulse product family.

## Current implementation
- Kotlin + Jetpack Compose.
- Device-local SQLite storage for offers, customers, orders and transaction notes.
- Basic create, edit, delete and search workflows.
- Explicitly unverified payment records; no M-PESA PIN collection.
- No live payments, STK Push, USSD execution, SMS sending, automatic fulfilment, or remote sync.

## Build
Open this repository in Android Studio, allow Gradle sync, then run the app on an Android device or emulator. GitHub Actions builds a debug APK on pushes and pull requests.

## Product components
- **Android:** daily business operations and offline-first records.
- **Connect:** authenticated nearby device pairing and controlled synchronization.
- **Web:** browser-based workspace, initially local/offline until a secured backend exists.

See [the feature register](docs/FULL_FEATURE_REGISTER.md) and [integration contract](docs/INTEGRATION_CONTRACT.md).

## Safety boundaries
- Never claim payment or delivery without authoritative evidence.
- Never retry an uncertain fulfilment automatically.
- Never collect or store M-PESA PINs.
- Test/local mode must not make live purchases or send real messages.
- Never commit API keys, credentials, customer data, or payment secrets.
