# BingwaPulse Payment Ingress (Phase 1)

Go service for Safaricom C2B confirmation callbacks and authenticated SMS candidates.
It is deliberately an **ingress and deduplication service only**: it never marks an order PAID,
never posts ledger entries, and never dispatches Android USSD work.

## Files

- `cmd/server`: HTTP server and health endpoint.
- `internal/ingress/parser.go`: C2B JSON and common M-PESA SMS parsing.
- `internal/ingress/handler.go`: tenant routing, Redis SET NX lock, PostgreSQL idempotency insert.
- `../../supabase/migrations/20261105100210_payment_ingress_and_double_entry_ledger.sql`: additive tenant-safe schema.

## Callback route

`POST /api/v1/payments/callback`

### Safaricom C2B confirmation JSON

Safaricom C2B callback payload fields used:

```json
{
  "TransactionType": "Customer Merchant Payment",
  "TransID": "QWERTY12345",
  "TransTime": "20261104123045",
  "TransAmount": "19.00",
  "BusinessShortCode": "123456",
  "BillRefNumber": "",
  "MSISDN": "2547*****123"
}
```

The receiving shortcode maps to exactly one tenant in `payment_ingress_channels`.
C2B MSISDN may be masked or hashed depending on callback version. The parser retains the supplied
value for audit but only sets `payer_phone` if it is a complete valid Kenyan number.

### SMS relay

Send the exact SMS body as the request body, with:

- `X-Bingwa-Source: sms`
- `X-Bingwa-SMS-Shortcode: <configured till/paybill>`
- `X-Bingwa-SMS-Signature: <lowercase hex HMAC-SHA256>`

Signature input is `shortcode + newline + exact raw SMS body`, using
`SMS_INGRESS_HMAC_SECRET`. SMS records are always `unverified`; a phone notification alone
is not authoritative payment proof.

## Idempotency

1. Resolve the receiving shortcode to a business.
2. Redis `SET key token NX EX 86400` suppresses hot duplicates.
3. PostgreSQL `UNIQUE (business_id, transaction_ref)` is the durable idempotency boundary.
4. If persistence fails, the handler releases only its own Redis lock using compare-and-delete.
5. A duplicate returns HTTP 200; a new unverified event returns HTTP 202.

Redis is an optimization, not the source of truth. If Redis expires, restarts, or is unavailable,
the PostgreSQL unique constraint still prevents duplicate event rows. The handler has no code path
that starts a purchase or emits an Android dispatch event.

## Local run

1. Apply the additive SQL migration to the intended Supabase/PostgreSQL environment after review.
2. Create one active `payment_ingress_channels` row per registered merchant shortcode.
3. Configure `DATABASE_URL`, `REDIS_CLUSTER_ADDRS`, `REDIS_PASSWORD`, optional `REDIS_USERNAME` / `REDIS_TLS`, `SMS_INGRESS_HMAC_SECRET`, and a high-entropy `DARAJA_CALLBACK_PATH_TOKEN` in a server-side secret manager. The token must be at least 43 URL-safe characters. Keep it out of Android, browsers, screenshots and logs. `DARAJA_ALLOWED_CIDRS` is an optional extra restriction only when Safaricom confirms the ranges.
4. Run `go mod tidy && go test ./... && go vet ./...`.
5. Run `go run ./cmd/server` or build the supplied Dockerfile.
6. Expose the endpoint only through HTTPS. Register the exact URL ending in
   `/api/v1/inbound/<DARAJA_CALLBACK_PATH_TOKEN>` with Safaricom. The high-entropy path token
   authenticates the callback endpoint because the standard C2B callback does not carry an
   application HMAC signature. An optional confirmed CIDR list adds network restriction; the
   handler uses the direct peer IP and does not trust forwarded IP headers.

## Not included yet

- STK Push callback schema and checkout-request-to-tenant correlation.
- Daraja transaction-status reconciliation.
- Automatic payment-to-order matching and the owner review/approval interface.
- Ledger posting service / account bootstrap.
- Android worker dispatch, validated menu navigation and bundle fulfilment.
- STK Push, live payment initiation or live USSD purchases.

These are intentionally separate phases. Do not interpret HTTP 202 as proof that money has
settled or as permission to dial a USSD code.
