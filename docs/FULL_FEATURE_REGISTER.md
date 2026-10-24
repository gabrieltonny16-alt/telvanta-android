# Telvanta Full Feature Register

**Product:** Telvanta — independent, offline-first telecom distribution platform  
**Tagline:** Your Bundles. Your Business. Automated.  
**Purpose:** Preserve the complete agreed scope so development does not depend on the owner remembering every earlier idea.

This register is the requirements source of truth. Update its status as features are implemented and tested. A visible screen/menu item is not considered implemented unless the action works end-to-end. A successful APK build proves compilation only, not on-device functionality.

## Status key
- **BUILT — BUILD VERIFIED:** code exists and CI compiled it; on-device behavior still needs testing.
- **PARTIAL:** some functionality exists, but workflow or safeguards are missing.
- **MISSING:** not implemented in the current Android repository.
- **BLOCKED / NEEDS APPROVAL:** depends on external provider access, device permissions, security review, or explicit owner approval.

## Current verified baseline (2026-10-24)
- **BUILT — BUILD VERIFIED:** native Kotlin/Jetpack Compose Android app, local SQLite persistence, launcher icon.
- **BUILT — BUILD VERIFIED:** local offers CRUD, customers CRUD/search, local orders, unverified transaction records, Settings, dashboard counters.
- **NOT YET DEVICE-VERIFIED:** all screens and persistence on a real Android phone.
- **MISSING:** multi-agent isolation, robust queue/reconciliation, signature catalogue import/refresh, automation, messaging rules, SIM tools, Connect pairing, remote sync, authentication/roles, audit log, full backup/export, and live provider integrations.

## 1. Main navigation and core business
- [x] Dashboard and local counts — built; on-device verification pending.
- [x] Offers screen — basic local CRUD built; verification pending.
- [x] Orders screen — basic local records built; verification pending.
- [x] Customers screen — basic local CRUD/search built; verification pending.
- [x] Transactions screen — local unverified records built; verification pending.
- [ ] Reliable dashboard refresh after changes and accurate status summaries.
- [ ] Order detail view, order history, filtering, search, sorting, and safe correction/audit trail.
- [ ] Export/backup/restore and recovery from device loss.
- [ ] Offline-first operation with explicit queued/synced/failed states.
- [ ] Accessibility, small-screen layout, input validation, error states, and on-device regression tests.

## 2. Agent accounts, roles, and data separation
- [ ] Owner/admin/operator roles with least-privilege permissions.
- [ ] Multiple authorized agents supported from the start.
- [ ] Each agent has separate customers, offers, selling prices, orders, transactions, settings, and ledger.
- [ ] No cross-agent balance, customer, order, or transaction leakage.
- [ ] Agent activation/revocation, session protection, account recovery, and audit trail.
- [ ] Clear owner-level overview without exposing data beyond granted permissions.
- [ ] Secure backend identity and access rules before any multi-device/remote operation.

## 3. Catalogue and offers
- [ ] Data, minutes, SMS, and combo categories; do not present airtime top-up as a bundle product.
- [ ] Signature Offers / FREE SIGNATURE catalogue import, retaining original price, source, verification status, and import time.
- [ ] Agent-defined selling price separate from source/original price.
- [ ] Offer refresh/download when internet is available; cached catalogue remains usable offline.
- [ ] Verified promotional/Tunukiwa offers only; preserve eligibility, expiry, and repeat limits when confirmed.
- [ ] Clearly distinguish verified, unverified, expired, unavailable, and manually entered offers.
- [ ] Never invent an offer or silently substitute a different bundle.
- [ ] Airtime-cost fulfilment only when that exact offer and exact airtime price are verified as eligible; otherwise block and explain.
- [ ] Offer search/filter, enable/disable, availability checks, import history, and safe duplicate handling.
- [ ] Rule for “already recommended” offers: alternative can be offered only after the previous attempt is confirmed failed (not pending/unknown) and the agent has authorized the alternative.

## 4. Orders, transactions, and ledger
- [ ] Separate payer number from recipient number; support “Buy for this number” and “Buy for another number”.
- [ ] State machine covering at least PAID → QUEUED → CLAIMED → USSD_SENT → DELIVERED / FAILED / UNKNOWN, with safe transitions.
- [ ] Payment and delivery evidence stored separately.
- [ ] Unique payment receipt handling scoped to agent and provider; duplicates processed once.
- [ ] Atomic job claiming and safe offline queues.
- [ ] Agent ledger, balances/settlement records where applicable, fees/margins, reconciliation, and export.
- [ ] M-PESA reversals, disputes, mismatches, duplicate receipts, and suspicious activity flagged for review.
- [ ] Never mark payment as verified or a bundle delivered based only on manual entry or a request being sent.
- [ ] Unknown fulfilment state must be reconciled before retry; no blind retries.
- [ ] Emergency kill switch blocks new purchases/fulfilment while allowing safe status checks and reconciliation.

## 5. M-PESA and payment-message processing
- [ ] Separate workflows for M-PESA messages, Till/Buy Goods messages, PayBill messages, and online-store payment messages.
- [ ] Parse candidate messages locally with configurable sender/format rules; show a preview and require validation before recording.
- [ ] Authorized sender allowlist and ability to add/remove senders.
- [ ] Receipt/reference deduplication and amount, payer, recipient, timestamp, agent, and order matching.
- [ ] Unmatched or ambiguous messages go to a review queue; no auto-credit on uncertain matches.
- [ ] Optional official Daraja integration only after credentials, callback security, sandbox testing, and explicit owner approval.
- [ ] Never ask for, capture, or store an M-PESA PIN.
- [ ] Live collection, STK Push, and payment actions remain disabled until separately approved.

## 6. Messaging, AutoReply, and customer engagement
- [ ] AutoReply Messages: editable templates, placeholders, per-template enable/disable, preview, and event triggers.
- [ ] Events/templates for offer already recommended or limit reached, app paused, blacklisted customer, failed request, unavailable offer, confirmed successful delivery, payment received, and order processing.
- [ ] Replies must reflect authoritative state; never claim payment/delivery success without evidence.
- [ ] Consent, opt-out, sender identity, rate limits, daily caps, retry policy, quiet hours, and message logs.
- [ ] Bulk SMS tool with recipient selection, preview, test mode, cost estimate/limits, consent checks, and kill switch.
- [ ] SMS send action requires Android runtime permissions and user approval; no silent sending.
- [ ] Auto-save contacts, configurable contact-name tag, duplicate detection, and safe contact edits.
- [ ] Blacklist management and enforced suppression across automation and bulk sends.
- [ ] EngageBot enable/disable, configuration, safe event handling, logs, and no invented answers about payment or delivery.
- [ ] WhatsApp/Telegram or other channels only through supported, authorized integrations; do not bypass platform safeguards.

## 7. SIM, USSD, and device automation
- [ ] SIM Setup: detect available SIMs/slot, carrier, user-selected SIM, permissions, readiness, and clear unsupported-device states.
- [ ] Quick dial and direct dialing with explicit user action.
- [ ] Assisted USSD/menu navigation only where Android/device/carrier permits; clearly show what action is being performed.
- [ ] At most one active USSD session per SIM; prevent competing jobs and stale session reuse.
- [ ] Safe timeout handling: if execution may have started, mark UNKNOWN and reconcile before retry.
- [ ] Insufficient airtime pauses fulfilment and alerts the operator; never silently switch to another payment method.
- [ ] No hidden accessibility abuse, PIN capture, background purchases, or unapproved live execution.
- [ ] Auto-renewal is opt-in, displays exact offer/price/recipient/rule, supports pause/cancel, and respects limits.
- [ ] Signature and Connect tools have clear independent settings and truthful status indicators.

## 8. Telvanta Connect and connectivity
- [ ] Companion app is for pairing, support, and monitoring; core Telvanta business functions must not depend on it.
- [ ] Authenticated nearby pairing using supported Bluetooth and/or local Wi-Fi/hotspot transport.
- [ ] Clear pairing consent, device identity, revoke/disconnect, encryption, and access scopes.
- [ ] Automatic reconnect and conflict-safe synchronization; queue local changes while offline.
- [ ] Remote internet sync/monitoring when configured; nearby-only transport cannot connect distant devices without internet.
- [ ] Connection status, last sync, queued changes, failures, and offline readiness visible to the user.
- [ ] No duplicate orders or payments after reconnect; conflict resolution and audit log.
- [ ] No sensitive records shared until pairing is authenticated and authorized.

## 9. Portal, web, and store workflows
- [ ] PORTAL and SITELINK entries resolve to defined, useful functions rather than dead menu items.
- [ ] Online-store order intake, payment matching, recipient/offer validation, and status updates.
- [ ] Authenticated web dashboard for owner/agents with strict tenant isolation.
- [ ] Optional secure remote sync; no secrets or customer data committed to public source control.
- [ ] Customer-facing offer catalogue and order status only after access controls and provider fulfilment are verified.
- [ ] Clear online/offline indicator and no false promise of instant delivery.

## 10. General tools and controls
- [ ] Settings for workspace, agent, SIM, connectivity, message rules, offer rules, and security.
- [ ] Updates: show app version, update availability, release notes, and safe install path.
- [ ] Logout/session lock where accounts are implemented.
- [ ] Authorized phone/device connection management.
- [ ] Activity/audit log, error diagnostics, data export, retention controls, and privacy notice.
- [ ] Global emergency stop and separate switches for payment, SMS, USSD, and automatic fulfilment.
- [ ] Test/sandbox mode visibly separated from live mode.
- [ ] Feature status page: working, offline, permission missing, provider unavailable, or disabled by owner.

## 11. External dependencies and release gates
- [ ] Verify provider-authorized method for each exact bundle/offer. Do not assume a public API or direct Till-funded fulfilment exists.
- [ ] Use official APIs or permitted device-assisted flows only; document restrictions and user consent.
- [ ] Obtain required permissions and test on the target Android version/device/SIM configuration.
- [ ] No paid hosting, paid APIs, live messaging, or live payments without explicit owner approval.
- [ ] Protect repository privacy; never commit API keys, credentials, payment secrets, customer data, or real message dumps.
- [ ] Automated build plus unit/integration tests, then real-device acceptance checks.
- [ ] Do not call a feature complete until its happy path, failure path, duplicate handling, offline behavior, and permission-denied behavior have been checked.

## 12. Release acceptance checklist
For every feature:
1. The feature is reachable in the app.
2. Its main action changes/stores the right data.
3. Data survives app restart when intended.
4. Invalid input and duplicate actions are handled safely.
5. Offline, permission-denied, timeout, and provider-failure states are clear.
6. No false payment/delivery success is shown.
7. Automated build/test evidence is linked.
8. On-device test status is recorded separately.

## Change log
- 2026-10-24: Initial full register consolidated from prior Telvanta planning conversations and current Android repository review.
