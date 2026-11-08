# BingwaPulse Android

**Your Bundles. Your Business. Automated.**

Native Android workspace for telecom distribution operations. This repository is the Android client in the BingwaPulse product family.

## Current implementation
- Kotlin + Jetpack Compose.
- Device-local SQLite storage for offers, customers, orders and unverified transaction notes.
- Durable sync outbox with stable operation IDs, server revisions and remote change application.
- HTTPS-only API login and synchronization client.
- API bearer session encrypted with Android Keystore.
- Internet permission is enabled; cleartext HTTP traffic is blocked.

## Build
Open this repository in Android Studio, allow Gradle sync, then run the app on an Android device or emulator. GitHub Actions builds a debug APK on pushes and pull requests.

## Connect to the shared API
Open **Settings**, enter the deployed HTTPS API URL, sign in with the same BingwaPulse account used on Web, then tap **Sync now**. See the [shared API guide](../telvanta-connect/api/README.md) in the Connect repository for endpoint and deployment details.

## Product components
- **Android:** daily business operations and offline-first records.
- **Connect:** device trust, pairing and shared API synchronization service.
- **Web:** browser workspace with account and sync controls.

## Safety boundaries
- Never claim payment or delivery without authoritative evidence.
- Never retry uncertain fulfilment automatically.
- Never collect or store M-PESA PINs.
- Transaction notes remain unverified.
- Never commit API keys, credentials, customer data, or payment secrets.
