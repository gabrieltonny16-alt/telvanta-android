import { Webhook } from "https://esm.sh/standardwebhooks@1.0.0";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "content-type, webhook-id, webhook-timestamp, webhook-signature",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: cors });

function normalizeKenyanPhone(value: string): string | null {
  const digits = value.replace(/[^0-9]/g, "");
  if (/^254[17][0-9]{8}$/.test(digits)) return "+" + digits;
  if (/^0[17][0-9]{8}$/.test(digits)) return "+254" + digits.slice(1);
  if (/^[17][0-9]{8}$/.test(digits)) return "+254" + digits;
  return null;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "Method not allowed." }, 405);

  const secret = Deno.env.get("SEND_SMS_HOOK_SECRET") ?? "";
  if (!secret.startsWith("v1,whsec_")) {
    return json({ error: "SMS hook signing secret is not configured." }, 503);
  }

  try {
    const payload = await req.text();
    if (payload.length > 16000) return json({ error: "Payload is too large." }, 413);
    const webhook = new Webhook(secret.replace("v1,whsec_", ""));
    const headers = Object.fromEntries(req.headers);
    const { user, sms } = webhook.verify(payload, headers) as {
      user?: { phone?: string };
      sms?: { otp?: string };
    };

    const phone = normalizeKenyanPhone(String(user?.phone ?? ""));
    const otp = String(sms?.otp ?? "");
    if (!phone || !/^[0-9]{6}$/.test(otp)) {
      return json({ error: "The SMS hook payload is invalid." }, 400);
    }

    if (Deno.env.get("SMS_LIVE_ENABLED") !== "true") {
      return json({ error: "Live SMS is locked until the provider test is approved.", code: "SMS_LIVE_DISABLED" }, 503);
    }
    const allowlist = new Set(
      String(Deno.env.get("SMS_TEST_ALLOWLIST") ?? "")
        .split(",").map((item) => normalizeKenyanPhone(item.trim())).filter(Boolean)
    );
    if (!allowlist.has(phone)) {
      return json({ error: "This number is not on the approved live SMS test allowlist." }, 403);
    }

    const username = String(Deno.env.get("AT_USERNAME") ?? "").trim();
    const apiKey = String(Deno.env.get("AT_API_KEY") ?? "").trim();
    if (!username || !apiKey) {
      return json({ error: "Africa's Talking live credentials are not configured." }, 503);
    }

    const form = new URLSearchParams({
      username,
      to: phone,
      message: "Your BingwaPulse verification code is " + otp + ". Do not share this code with anyone.",
    });
    const senderId = String(Deno.env.get("AT_SENDER_ID") ?? "").trim();
    if (senderId) form.set("from", senderId);

    const response = await fetch("https://api.africastalking.com/version1/messaging", {
      method: "POST",
      headers: {
        apiKey,
        Accept: "application/json",
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: form.toString(),
      signal: AbortSignal.timeout(15000),
    });
    const result = await response.json().catch(() => ({}));
    const recipients = result?.SMSMessageData?.Recipients;
    if (!response.ok || !Array.isArray(recipients) || recipients.length !== 1 ||
        Number(recipients[0]?.statusCode) !== 101) {
      console.error("Africa's Talking did not accept the authentication SMS.");
      return json({ error: "The SMS provider did not accept the message." }, 502);
    }

    return json({});
  } catch (error) {
    console.error("Send SMS hook rejected or failed:", error instanceof Error ? error.message : "unknown error");
    return json({ error: "Could not process the authentication SMS request." }, 401);
  }
});
