# Automatic offer fulfilment — acceptance specification

## Product requirement

Automatic offer fulfilment is the core operational workflow of BingwaPulse. A paid customer order must be fulfilled by the authorized agent device using the provider's saved Signature offer entry code, matching the exact offer identity and validity, then choosing **Buy with Airtime**.

The USSD session may be briefly visible while it runs. It must close when the workflow finishes or safely stops. The system must not leave an agent to navigate routine menu steps manually.

This document records the required behaviour. It is not a claim that the workflow is already implemented or tested.

## Required sequence

1. Capture an M-PESA Buy Goods Till confirmation as payment evidence.
2. Verify the payment server-side and match it to exactly one awaiting order. Match at minimum the receipt/transaction reference, amount, business/Till context and payer/customer details available to the trusted verifier. A notification alone is an unverified candidate and must never queue an order.
3. Confirm the order references an active, current Signature offer and an authorized provider/device.
4. Load the offer's saved USSD entry code and provider-specific menu recipe. Do not invent or reconstruct codes from offer names.
5. Dial the saved entry code through the selected authorized SIM.
6. Navigate the provider menu using configured, screen-matched steps.
7. Match the provider's displayed offer against the order's offer name and validity. Price alone, a partial name, or a similar validity is not an acceptable match.
8. Select **Buy with Airtime** (or the exact provider wording configured for that menu).
9. Complete only the provider's required confirmation steps. Never read, log, transmit, or store an M-PESA PIN. Any credential prompt that is not explicitly supported by a safe, approved flow must stop for manual handling.
10. Verify a provider success/receipt response tied to the intended recipient and offer before marking DELIVERED.
11. Close/dismiss the USSD session after a definitive result. Report the result to the customer only after confirmed delivery.

## Stop conditions and safety

- No verified payment: do not claim or fulfil.
- No exact offer-name + validity match: stop; do not substitute.
- Missing/stale Signature entry code or unverified menu recipe: stop.
- Wrong recipient, unexpected amount, unknown menu, insufficient airtime, provider rejection, or timeout: do not mark delivered.
- UNKNOWN outcomes are quarantined for reconciliation; never automatically retry and risk a duplicate purchase.
- Idempotency and receipt protection must prevent duplicate payment/order/fulfilment processing.
- Respect business/device kill switch, pause, authorization and provider availability.
- Preserve the existing Airtime/Sambaza subscription implementation unchanged. It is a separate flow.
- Do not wire the unused Daraja STK Push subscription backend into this customer-order flow.
- No real-money purchase, live customer message, paid service, or production activation during development without explicit approval.

## Required offer/provider data

Each fulfilment-ready Signature offer must provide:
- stable Signature/source offer reference;
- provider identity;
- exact offer name;
- validity as a normalized value and the provider's displayed label;
- category and data/units where applicable;
- saved USSD entry code;
- provider-specific menu recipe with exact screen match conditions and actions;
- current verification timestamp/status;
- customer selling price kept separate from provider cost.

An offer missing required fields is not fulfilment-ready.

## Required state progression

`AWAITING_PAYMENT -> PAID -> QUEUED -> CLAIMED -> USSD_SENT -> DELIVERED | FAILED | UNKNOWN`

The PAID transition requires trusted payment verification. DELIVERED requires provider-side success evidence. UNKNOWN requires manager reconciliation before another attempt.

## Acceptance tests before production

1. Notification candidate alone never queues an order.
2. One verified Till payment matches one order; duplicate receipt is rejected safely.
3. Correct saved Signature entry code is selected for the order.
4. Exact offer name and validity are both required.
5. A same-name offer with different validity is rejected.
6. A similar-name offer is rejected.
7. The correct **Buy with Airtime** menu action is selected.
8. Provider success for the correct recipient marks DELIVERED.
9. Insufficient airtime/provider rejection marks FAILED.
10. Timeout or ambiguous screen marks UNKNOWN and does not retry.
11. USSD session is dismissed after a definitive result.
12. Existing Sambaza subscription tests remain unchanged and pass.
13. Automated tests and a controlled on-device test pass before any real customer fulfilment is enabled.

## Implementation status — 11 November 2026

Implemented in the current development branch and Supabase project:
- The Android foreground worker calls the database's atomic claim procedure and records fulfilment attempt results. The database already enforces verified-payment, provider, device, offer-rule and business kill-switch gates.
- Local Signature/My Offers records now synchronize to the business cloud catalogue as provider, offer and agent-offer records. The saved Signature entry code and validity label are carried into the cloud offer.
- The offer schema now has a dedicated `validity_label`. A server-side order guard refuses QUEUED/CLAIMED transitions unless the offer is active/current, its exact validity is present, its provider is active for USSD, payment is verified, and the configured recipe contains both an exact offer-name + validity screen match and a **Buy with Airtime** action.
- Delivery-result recording now rejects a generic transaction ID or generic M-PESA message. It requires explicit carrier purchase-success language plus the exact offer name, exact validity label, and intended recipient phone (matched by the final nine digits). The database is authoritative; if the carrier confirmation omits any of these details, the order is not marked DELIVERED and must be reconciled.
- The Android worker claim payload now includes the offer's exact validity label. The accessibility flow requires the exact offer name and validity to appear together on the provider screen, records that identity gate, and blocks Buy with Airtime unless the identity was confirmed first and the exact payment option is visible. A missing validity label or mismatch stops automation. The flow does not read or use a stored PIN; a manual PIN prompt pauses the recipe until the next configured screen appears. A definitive result is saved server-side before the USSD dialog is dismissed.
- Unit tests cover conservative screen classification, including transaction-reference-only and generic M-PESA messages.

Still blocking a fully automatic end-to-end production journey:
- A trusted Safaricom C2B callback endpoint is now deployed, and the database can match verified Till receipts to a pre-created SiteLink order while preserving the selected recipient. It is not yet active for this business until the callback token is configured, the Till is saved in Payment Inbox, and Safaricom confirms both callback URL registrations. Phone notifications alone remain untrusted evidence.
- Signature candidates are user-supplied extracted catalogue entries; their entry codes are saved, but provider menu recipes still need a verified screen-by-screen definition. The app currently requires the exact offer/validity match and Buy with Airtime step to be configured and tested before enabling a rule.
- The provider's live USSD menu behaviour, agent-SIM airtime purchase, recipient delivery confirmation, and session dismissal must pass a controlled device/carrier test. A backend string match alone is not proof of delivery.
- Customer success notifications after delivery are not yet connected.
- The live BingwaPulse project currently has no business, provider, offer, device or customer-order records, so no real order can yet traverse the pipeline.

The development branch and database changes do not enable live fulfilment by themselves. End-to-end acceptance criteria remain unproven until a real business/device/catalogue is configured, trusted payment verification is connected, and the full carrier journey passes on the target Android SIM.
