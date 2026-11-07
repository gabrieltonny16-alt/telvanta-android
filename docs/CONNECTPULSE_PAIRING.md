# ConnectPulse device pairing

## Pairing flow
1. An authenticated BingwaPulse owner, business admin or agent manager creates a 10-character one-time pairing code.
2. The code hash is stored in the business database with a 10-minute expiry. The raw code is shown only in BingwaPulse.
3. ConnectPulse submits the code and a newly generated device secret to the connectpulse-device Edge Function.
4. The database atomically consumes the code, creates a pending device record and stores only a hash of the device secret in the private schema.
5. A manager reviews the pending device in BingwaPulse and approves it.
6. ConnectPulse stores the raw device secret encrypted with Android Keystore and schedules a periodic heartbeat using WorkManager.

## Heartbeat
The companion sends its device ID, secret, app version, battery percentage and network type. The Edge Function hashes the secret and calls a database function that accepts heartbeats only for an approved device. Paused, pending and revoked devices do not report as online.

## Security boundaries
- Pairing codes expire and can be consumed once.
- Device secrets are generated on the companion and stored encrypted locally.
- The database stores only a SHA-256 hash of each device secret in the private schema.
- The pairing and heartbeat database functions are executable only by the Supabase service role, not by anonymous or signed-in browser clients.
- ConnectPulse does not store or request M-PESA PINs, trigger USSD purchases, or verify payments.
- This stage reports ConnectPulse's own app, battery and network state. It does not yet inspect the BingwaPulse worker's internal execution state or remotely restart a powered-off device.
