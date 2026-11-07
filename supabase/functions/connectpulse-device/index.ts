import { createClient } from "npm:@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: cors });
const url = Deno.env.get("SUPABASE_URL");
const secret = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
if (!url || !secret) throw new Error("ConnectPulse service configuration is missing.");
const db = createClient(url, secret, { auth: { persistSession: false, autoRefreshToken: false } });

function hex(bytes: Uint8Array): string {
  return Array.from(bytes).map(b => b.toString(16).padStart(2, "0")).join("");
}
async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return hex(new Uint8Array(digest));
}
function makeCredential(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return hex(bytes);
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "Method not allowed." }, 405);
  try {
    const raw = await req.text();
    if (raw.length > 4000) return json({ error: "Request is too large." }, 413);
    let body: any;
    try { body = JSON.parse(raw); } catch { return json({ error: "Invalid request body." }, 400); }

    const action = String(body.action ?? "");
    const deviceId = String(body.device_id ?? "");
    const credential = String(body.credential ?? "");
    if (action === "pair") {
      const code = String(body.code ?? "").toUpperCase();
      const label = String(body.device_label ?? "").trim();
      if (!/^[A-Z0-9]{10}$/.test(code)) return json({ error: "Enter the 10-character pairing code from BingwaPulse." }, 400);
      if (label.length < 2 || label.length > 100) return json({ error: "Enter a device name between 2 and 100 characters." }, 400);
      const token = makeCredential();
      const { data, error } = await db.rpc("claim_device_pairing_code", {
        p_code_hash: await sha256(code),
        p_device_label: label,
        p_credential_hash: await sha256(token),
      });
      if (error || !data?.length) return json({ error: "Pairing code is invalid, expired, or already used." }, 409);
      const row = data[0];
      return json({ device_id: row.device_id, status: "pending_pairing", credential: token }, 201);
    }

    if (!/^[0-9a-f-]{36}$/i.test(deviceId) || !/^[0-9a-f]{64}$/.test(credential)) {
      return json({ error: "This device is not registered. Pair it again from BingwaPulse." }, 400);
    }
    const credentialHash = await sha256(credential);

    if (action === "status") {
      const { data, error } = await db.rpc("connectpulse_device_status", {
        p_device_id: deviceId,
        p_credential_hash: credentialHash,
      });
      if (error || !data?.length) return json({ error: "Device access could not be confirmed." }, 403);
      return json({ device_id: deviceId, ...data[0] });
    }

    if (action === "heartbeat") {
      const battery = body.battery_percent == null ? null : Number(body.battery_percent);
      if (battery != null && (!Number.isInteger(battery) || battery < 0 || battery > 100))
        return json({ error: "Battery value is invalid." }, 400);
      const { data, error } = await db.rpc("record_connectpulse_heartbeat", {
        p_device_id: deviceId,
        p_credential_hash: credentialHash,
        p_worker_state: "unknown",
        p_battery_percent: battery,
        p_network_state: String(body.network_state ?? "unknown").slice(0, 80),
        p_app_version: String(body.app_version ?? "").slice(0, 40),
      });
      if (error) return json({ error: "Heartbeat was not accepted. Check whether this device is approved." }, 403);
      return json({ device_id: deviceId, status: data, received_at: new Date().toISOString() });
    }

    return json({ error: "Unknown device action." }, 400);
  } catch (error) {
    console.error("ConnectPulse device request failed:", error);
    return json({ error: "ConnectPulse is temporarily unavailable." }, 500);
  }
});
