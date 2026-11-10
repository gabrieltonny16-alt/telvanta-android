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

## Current known implementation gap

The existing Android worker can claim an eligible order, launch its saved USSD code and wait for the accessibility service. The accessibility service executes configured menu steps and records a carrier result. However, the current backend foundation documentation still says payment candidates are unverified, worker claim/acknowledgement is incomplete, and automated bundle purchasing is not enabled. Therefore the end-to-end acceptance criteria above are not yet proven.
