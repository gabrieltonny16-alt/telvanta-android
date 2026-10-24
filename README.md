# Telvanta Android

**Your Bundles. Your Business. Automated.**

Independent, offline-first Android app for telecom distribution workflows.

## Current foundation
- Native Android app using Kotlin and Jetpack Compose.
- Local SQLite database for saved offers, customers, and orders.
- No network permission in the initial app foundation.
- No live payments, STK Push, USSD execution, SMS sending, or automatic fulfilment.
- Offline records are local to this device until secure sync is implemented and tested.

## Build
Open this repository in Android Studio, allow Gradle sync, then run the app on an Android device or emulator. GitHub Actions is configured to build a debug APK on pushes and pull requests.

## Planned sequence
1. Verify the Android build and local persistence.
2. Implement offers, customers, orders, transactions, and settings screens.
3. Add safe offline queue and reconciliation rules.
4. Build Telvanta Connect's authenticated nearby connection.
5. Add optional remote sync and Signature Offer downloads after security review.

## Safety rules
- Never claim delivery without authoritative confirmation.
- Never retry uncertain fulfilment automatically.
- Never collect or store M-PESA PINs.
- Test mode must not make live purchases or send real messages.
- Never put API keys or credentials in source control.
