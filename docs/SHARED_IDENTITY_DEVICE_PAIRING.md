# Shared Identity and Device Pairing Contract

Status: design baseline for implementation. This document does not claim that cloud pairing or synchronization is already active.

## Goal

BingwaPulse Android, Connect Pulse Android, the management website and public SiteLink must use one account/business identity and a shared authorization model. Connect Pulse is a companion client; it does not own business accounts, offers, prices, orders or payment records.

## Identity model

- Account: authenticated person.
- Business: tenant/workspace owned by one account or organization.
- Membership: joins an account to a business with a role and explicit permissions.
- Device: a registered Connect Pulse installation belonging to a business and linked to the enrolling account.
- SIM profile: a locally identified SIM slot/profile associated with a registered device; never expose SIM secrets or credentials to the website.
- Agent offer: an agent-scoped selling price and publication state referencing a provider offer.

Suggested roles (permissions must be enforced server-side):
- Owner: full business administration and device approval/revocation.
- Manager: operational management as explicitly granted by owner.
- Agent: access only to assigned customers, own offer prices, own SiteLink and permitted orders.
- Device: not a human role; receives narrowly scoped device credentials and may submit permitted device events only.

## Pairing flow (approval required)

1. An authenticated owner/manager opens BingwaPulse Android or the management website and chooses Add device.
2. The server creates a random, single-use pairing challenge with a short expiry (target 5 minutes). Store only a hash of the challenge. Never use a permanent PIN or hard-coded shared secret.
3. The authorized user sees the challenge/QR and confirms which business and device are being enrolled.
4. Connect Pulse scans/enters the challenge and submits its installation public key plus a device label. It must not receive broad account credentials.
5. The server verifies expiry, one-time use, business membership and approver permission; the owner/manager explicitly approves the device.
6. The server issues a revocable, device-scoped credential. Store it using Android Keystore-backed protection where supported.
7. Both apps show the registered device identity and status. All pairing, approval, revocation and key rotation events are audited.

## Security requirements

- TLS for all remote requests.
- Pairing challenges: cryptographically random, one-time, short-lived, rate-limited and invalidated after successful use.
- Device credentials: scoped, revocable, rotated; never put in source code, logs, URLs or analytics.
- Server-side authorization on every business and device operation; UI hiding is not authorization.
- Device revocation must stop future authenticated sync.
- Do not sync customer or transaction data to a device unless its permissions explicitly allow it.
- Do not collect phone contents, contacts, SMS or call logs by default.
- No automatic USSD submission, SMS sending, purchases or bundle fulfilment as part of pairing.
- A pairing failure must remain a failure; never display CONNECTED until the server confirms enrollment.

## API contract (logical endpoints; implementation may use equivalent routes)

- POST /v1/device-pairing-challenges — authenticated owner/manager creates challenge.
- POST /v1/device-pairing/claim — Connect Pulse claims a valid challenge with device public key and label.
- POST /v1/device-pairing/{requestId}/approve — authorized owner/manager approves a pending device.
- GET /v1/devices — authorized members list devices visible to them.
- POST /v1/devices/{deviceId}/revoke — authorized owner/manager revokes a device.
- POST /v1/device-events — device submits narrowly scoped status/event records.

Responses must include stable IDs, server timestamps and explicit states such as PENDING_APPROVAL, ACTIVE, EXPIRED, REJECTED, REVOKED. Never infer an active connection from a locally generated code.

## Shared business data boundaries

- BingwaPulse is authoritative for accounts, business memberships, customers, providers, offers, agent prices, SiteLink configuration, orders and transaction evidence.
- SiteLink is a public storefront surface; it may read only deliberately published offer fields and create order requests. It must not read private business data.
- Connect Pulse is authoritative only for local device diagnostics and permitted device events. It is not authoritative for payment verification or fulfilment.
- Local/offline records must be marked as local/pending until reconciled with the server. Do not claim cross-device sync until it is implemented and tested.

## Implementation gates

Before enabling pairing in production:
1. Select and configure a backend and authentication provider.
2. Implement server-side tenant and role checks.
3. Implement challenge issuance, claim, approval, expiry, replay prevention and revocation.
4. Add secure device-key storage and network error handling to Connect Pulse.
5. Add owner/manager device management to BingwaPulse and website.
6. Test cross-account isolation, expired/reused challenges, revoked devices and offline behavior.
7. Keep production pairing disabled until the owner explicitly approves deployment.

## Current repository status

The current Android apps do not yet implement this contract. Connect Pulse's pairing form is a UI placeholder; BingwaPulse data is currently device-local. This document is a specification, not evidence of a working backend or active device sync.
