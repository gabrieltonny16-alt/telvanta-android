# BingwaPulse Product Audit & Implementation Checklist

Status: initial audit of the current `feature/automatic-offer-fulfilment` branch, combined with the first screen-review notes. This is not a claim that every screen has been inspected; additional screenshots and workflows will extend this checklist.

## Priority 0 — Operational correctness and safety

- [ ] Trusted M-PESA C2B callback: validate token, registered Till, transaction type, receipt, amount and timestamp; test valid/invalid callbacks, duplicate receipts and replay handling.
- [ ] Payment-to-order matching: exact amount and available payer identity; preserve the customer's selected recipient; hold ambiguous/masked-number cases for reconciliation; never infer an offer from price alone.
- [ ] Order lifecycle: enforce legal state transitions and idempotency from payment verification through queue, claim, USSD, delivery/failure/unknown.
- [ ] Automatic Signature fulfilment: use the imported entry code; match exact offer name AND validity; choose Buy with Airtime; verify provider success for the intended recipient and offer.
- [ ] USSD safety: no PIN capture/storage/transmission; manual PIN pause; timeout becomes UNKNOWN; no automatic retry after unknown; close the session after a definitive result.
- [ ] Agent/device controls: pair/approve/revoke, online/offline state, one active worker claim, pause/kill switch and audit history.
- [ ] Customer messaging: send success only after verified delivery; send clear pending/failed/reconciliation messages without claiming success.
- [ ] Preserve the existing Sambaza subscription flow and regression-test it separately.

## Priority 1 — Business data and recovery

- [ ] Separate local preview records from authenticated cloud business records and label each clearly.
- [ ] Offline/network recovery: queue only safe local work, avoid duplicate submissions, reconcile after reconnect and show sync state.
- [ ] Transaction ledger: source, receipt/reference, customer, offer, amount, payment verification, fulfilment status and timestamps must agree across screens.
- [ ] Admin reconciliation: inspect ambiguous/unmatched payments and UNKNOWN fulfilment attempts; require an explicit reason before retry.
- [ ] Offer catalogue: preserve Signature source, entry code, exact validity, SIM/provider compatibility, freshness and active status.
- [ ] Multi-business and role-based access: enforce business scoping and owner/admin/agent permissions in the backend, not just the UI.
- [ ] Export/backup: make clear whether records are local or cloud-backed; verify export content and recovery.

## Priority 2 — Screen and UX improvements

### App shell and navigation
- [ ] Replace the full-width offline banner with a compact status indicator; show local-preview state only when relevant.
- [ ] Use clear standard navigation icons and consistent icon/label spacing.
- [ ] Establish shared spacing, card/input/button radii, type scale and contrast tokens.
- [ ] Review bottom navigation against Android system gesture/navigation area on small devices.

### Dashboard
- [ ] Put core metrics in a compact, scannable grid near the top.
- [ ] Remove unsupported or placeholder metrics (for example, commission shown as a dash) or label them as unavailable.
- [ ] Distinguish locally recorded totals from verified cloud totals.
- [ ] Add quick actions for a new order, payment inbox, offers and agent status.
- [ ] Add tap-to-hide/show for the actual SIM airtime balance and sensitive financial totals if/when displayed.

### Transactions and Orders
- [ ] Keep an immediate empty state; add a relevant action (create order / open Payment Inbox) rather than a dead-end message.
- [ ] Verify status filters map to the real order/payment state model.
- [ ] Add receipt/order detail view, source, recipient, payer, timestamps and reconciliation action.
- [ ] Prevent local manual status buttons from implying a real payment or verified delivery.
- [ ] Make refresh, search and export states clear.

### Wallet
- [ ] Keep platform tokens/subscription distinct from agent SIM airtime and business earnings.
- [ ] Add visibility toggle only to real sensitive money balances; current Platform Wallet displays token counts, not a KES cash balance.
- [ ] Clearly distinguish available, reserved, consumed and pending token amounts.
- [ ] Ensure every displayed balance is backed by a current API response and has loading/error/stale states.

### Profile / Account & Session
- [ ] Replace stale copy that says authentication is not implemented if a real authenticated session exists.
- [ ] Show signed-in identity, business/workspace, role, session state and cloud-sync state.
- [ ] Make sign-out actually clear the session, or remove/disable the action with an accurate explanation.
- [ ] Gate cloud backup, device pairing and admin actions with explicit sign-in/role fallbacks.

### Offers / Signature / Quick Dial / SiteLink
- [ ] Replace repeated long technical disclaimers on each offer with compact Unverified/Custom/Expired status labels and one detail explanation.
- [ ] Keep exact offer name, validity, price, SIM/provider and entry code visible where operationally needed.
- [ ] Remove or clearly label preview-only SiteLink checkout until public customer checkout, trusted payment and fulfilment are connected.
- [ ] Quick Dial must not be confused with automatic purchase fulfilment; show whether an action is preview, manual dial or automated order processing.
- [ ] Add clear empty states and actionable routes between My Offers, Signature imports and SiteLink.

### Other screens
- [ ] Audit Bulk Messenger consent, recipient selection, rate limits and send-result tracking.
- [ ] Audit Auto-Reply/EngageBot authorization, rules, delivery status and opt-out handling.
- [ ] Audit Settings/Device Activation permissions and accessibility-service status.
- [ ] Audit Statistics/Analytics for correct date boundaries, verified-only metrics and honest unavailable states.
- [ ] Audit Help, About, Updates and every menu destination for placeholder/dead actions.

## Findings confirmed in this first code pass

1. The app shell currently renders an offline-ready banner on every selected screen and a second LOCAL PREVIEW MODE card when using a local workspace. This matches the reported vertical-space issue.
2. Bottom navigation uses text glyphs (`▦`, `↔`, `▣`, `●`) rather than recognizable Home/Receipt/Wallet/Profile icons.
3. Transactions already has a no-records/filter-empty state, but it has no direct CTA. The transaction screen is based on local `transactionHistory()`; cloud reconciliation and cross-device completeness still need verification.
4. Dashboard shows three metrics in a single horizontal row, not a 2x2 grid. It also displays a commission dash with a note that commission tracking is not connected.
5. Dashboard shows SIM airtime balance as `KES —` until a manual refresh; there is no visibility toggle. Airtime used today is derived from locally recorded delivered orders, not verified commission/earnings.
6. Platform Wallet currently displays subscription and token balances, not a cash/Till balance. A money-balance visibility toggle is therefore not applicable to that screen yet.
7. Account & Session screen copy says authentication/session management are not implemented and its Sign out button is unavailable. This conflicts with the repository's AuthActivity and SecureSessionStore paths; the screen must be reconciled with the actual session implementation.
8. SiteLink explicitly identifies checkout as a preview and not live. Do not market it as a live storefront until hosted checkout and trusted payment are actually connected.
9. Quick Dial explicitly says it does not automatically call, submit USSD choices or purchase bundles. Keep that distinction clear while the separate automatic fulfilment engine is completed.
10. Current cloud project was previously inspected as empty of business/provider/offer/device/order/payment records. Workspace, Till, offer sync and device pairing remain real onboarding/setup blockers.

## Verification gates

- Android unit tests and both app builds must pass on the current head.
- Backend callback and SQL matching need repeatable automated tests; Android CI alone does not validate them.
- Test offline/reconnect, duplicate receipt, ambiguous payment, exact offer/validity matching, insufficient airtime, timeout/UNKNOWN and kill switch.
- Test on the target Android device and real SIM with controlled test transactions before declaring end-to-end operation.
- No live customer order or real-money test is to be represented as tested unless it actually happened.

## Audit status vocabulary

- **Implemented**: code exists and is connected.
- **Build-tested**: CI confirms compilation/tests, but not carrier behavior.
- **Backend-tested**: database/function behavior has repeatable tests.
- **Device-tested**: tested on an actual Android device/SIM.
- **Operational**: the complete payment → exact offer fulfilment → verified delivery → customer update journey has passed controlled end-to-end tests.

Do not mark a checklist item complete based only on UI text, a specification, or a successful APK build.
