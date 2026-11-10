import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const json = (body: Record<string, unknown>, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });

function constantTimeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let difference = 0;
  for (let i = 0; i < a.length; i++) difference |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return difference === 0;
}

function parseSafaricomTimestamp(value: unknown): string | null {
  const raw = String(value ?? "").trim();
  if (!/^\\d{14}$/.test(raw)) return null;
  const year = Number(raw.slice(0, 4));
  const month = Number(raw.slice(4, 6));
  const day = Number(raw.slice(6, 8));
  const hour = Number(raw.slice(8, 10));
  const minute = Number(raw.slice(10, 12));
  const second = Number(raw.slice(12, 14));
  const date = new Date(Date.UTC(year, month - 1, day, hour, minute, second));
  if (
    date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 ||
    date.getUTCDate() !== day || date.getUTCHours() !== hour ||
    date.getUTCMinutes() !== minute || date.getUTCSeconds() !== second
  ) return null;
  return date.toISOString();
}

async function sha256(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), (part) => part.toString(16).padStart(2, "0")).join("");
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ error: "POST required" }, 405);

  const expectedToken = Deno.env.get("MPESA_C2B_CALLBACK_TOKEN") ?? "";
  if (!expectedToken) return json({ error: "C2B callback is not configured" }, 503);
  const url = new URL(request.url);
  const suppliedToken = url.searchParams.get("token") ?? "";
  if (!constantTimeEqual(suppliedToken, expectedToken)) return json({ error: "Unauthorized callback" }, 401);

  const mode = url.searchParams.get("mode") ?? "";
  if (mode !== "validate" && mode !== "confirm") {
    return json({ error: "Callback mode must be validate or confirm" }, 400);
  }

  let payload: Record<string, unknown>;
  try {
    const parsed = await request.json();
    payload = (parsed?.Body && typeof parsed.Body === "object" ? parsed.Body : parsed) as Record<string, unknown>;
  } catch {
    return json({ error: "Invalid JSON callback" }, 400);
  }

  const shortcode = String(payload.BusinessShortCode ?? "").trim();
  const transactionType = String(payload.TransactionType ?? "").trim().toLowerCase().replace(/[^a-z]/g, "");
  const receipt = String(payload.TransID ?? "").trim().toUpperCase();
  const amountKes = Number(payload.TransAmount);
  const amountMinor = Math.round(amountKes * 100);
  const rawMsisdn = String(payload.MSISDN ?? "").trim().slice(0, 80);
  const normalizedMsisdn = rawMsisdn.replace(/[^0-9]/g, "");
  const payerPhone = /^254[17][0-9]{8}$/.test(normalizedMsisdn) ? normalizedMsisdn : null;
  const occurredAt = parseSafaricomTimestamp(payload.TransTime);
  const isBuyGoods = transactionType.includes("buygoods") ||
    transactionType.includes("customermerchantpayment") ||
    transactionType.includes("customerbuygoodsonline");

  // Safaricom C2B v1 may hash MSISDN and C2B v2 may mask it. Preserve that
  // source value, but only treat a complete Kenyan MSISDN as a usable phone.
  if (!shortcode || !isBuyGoods || !Number.isFinite(amountKes) ||
      amountKes <= 0 || !Number.isSafeInteger(amountMinor) || !rawMsisdn) {
    return json({ ResultCode: 1, ResultDesc: "Invalid Buy Goods payment details" }, 400);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  if (!supabaseUrl || !serviceRoleKey) return json({ error: "Payment ingress storage is not configured" }, 503);
  const supabase = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: channels, error: channelError } = await supabase
    .from("payment_ingress_channels")
    .select("business_id,shortcode,channel_type,is_active")
    .eq("shortcode", shortcode)
    .eq("channel_type", "till")
    .eq("is_active", true);

  if (channelError) return json({ error: "Could not verify the registered Till" }, 500);
  if (!channels || channels.length !== 1) {
    return json({ ResultCode: 1, ResultDesc: "This Till is not registered for this business" }, 403);
  }

  // Safaricom calls the validation URL before the confirmation URL. Validation
  // accepts a well-formed payment but never writes a paid order.
  if (mode === "validate") {
    return json({ ResultCode: 0, ResultDesc: "Accepted" });
  }

  if (!/^[A-Z0-9]{8,16}$/.test(receipt) || !occurredAt) {
    return json({ ResultCode: 1, ResultDesc: "Invalid transaction reference or timestamp" }, 400);
  }

  const businessId = channels[0].business_id as string;
  const rawPayload = JSON.stringify(payload);
  const payloadHash = await sha256(rawPayload);
  const { data: existing, error: existingError } = await supabase
    .from("payment_ingress_events")
    .select("id,amount_minor,shortcode,payer_phone,verification_status")
    .eq("business_id", businessId)
    .eq("transaction_ref", receipt)
    .maybeSingle();

  if (existingError) return json({ error: "Could not check duplicate payment reference" }, 500);

  if (existing) {
    const conflict = Number(existing.amount_minor) !== amountMinor ||
      existing.shortcode !== shortcode ||
      (existing.payer_phone && payerPhone && existing.payer_phone !== payerPhone);
    if (conflict) {
      await supabase.from("payment_ingress_events")
        .update({ verification_status: "ambiguous" })
        .eq("business_id", businessId)
        .eq("id", existing.id)
        .neq("verification_status", "verified");
      return json({ ResultCode: 1, ResultDesc: "Conflicting duplicate receipt requires reconciliation" }, 409);
    }

    const { error: updateError } = await supabase.from("payment_ingress_events")
      .update({
        provider: "safaricom_c2b",
        source_kind: "daraja_callback",
        payer_phone: payerPhone ?? existing.payer_phone,
        raw_msisdn: rawMsisdn,
        occurred_at: occurredAt,
        payload_sha256: payloadHash,
        verification_status: "verified",
      })
      .eq("business_id", businessId)
      .eq("id", existing.id);
    if (updateError) return json({ error: "Could not verify the existing receipt" }, 500);
    return json({ ResultCode: 0, ResultDesc: "Accepted" });
  }

  const { error: insertError } = await supabase.from("payment_ingress_events").insert({
    business_id: businessId,
    provider: "safaricom_c2b",
    transaction_ref: receipt,
    amount_minor: amountMinor,
    shortcode,
    payer_phone: payerPhone,
    raw_msisdn: rawMsisdn,
    occurred_at: occurredAt,
    source_kind: "daraja_callback",
    payload_sha256: payloadHash,
    verification_status: "verified",
  });

  if (insertError) {
    // Safaricom may retry a callback after a network timeout. A duplicate
    // receipt is safe to acknowledge only if the stored record matches.
    const { data: duplicate } = await supabase.from("payment_ingress_events")
      .select("amount_minor,shortcode,payer_phone")
      .eq("business_id", businessId)
      .eq("transaction_ref", receipt)
      .maybeSingle();
    if (duplicate && Number(duplicate.amount_minor) === amountMinor &&
        duplicate.shortcode === shortcode && (!payerPhone || duplicate.payer_phone === payerPhone)) {
      return json({ ResultCode: 0, ResultDesc: "Accepted" });
    }
    return json({ error: "Could not store verified payment event" }, 500);
  }

  return json({ ResultCode: 0, ResultDesc: "Accepted" });
});
