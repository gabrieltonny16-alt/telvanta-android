import { createClient } from "npm:@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: cors });

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
if (!supabaseUrl || !serviceKey) throw new Error("Supabase service configuration is missing.");
const db = createClient(supabaseUrl, serviceKey, {
  auth: { persistSession: false, autoRefreshToken: false },
});

function normalizePhone(value: string): string | null {
  const digits = value.replace(/[^0-9]/g, "");
  if (/^254[17][0-9]{8}$/.test(digits)) return digits;
  if (/^0[17][0-9]{8}$/.test(digits)) return "254" + digits.slice(1);
  if (/^[17][0-9]{8}$/.test(digits)) return "254" + digits;
  return null;
}
function timestampEAT(): string {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Africa/Nairobi",
    year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", second: "2-digit",
    hourCycle: "h23",
  }).formatToParts(new Date());
  const p = Object.fromEntries(parts.map((x) => [x.type, x.value]));
  return `${p.year}${p.month}${p.day}${p.hour}${p.minute}${p.second}`;
}
async function accessToken(baseUrl: string, key: string, secret: string): Promise<string> {
  const response = await fetch(`${baseUrl}/oauth/v1/generate?grant_type=client_credentials`, {
    headers: { Authorization: `Basic ${btoa(`${key}:${secret}`)}` },
  });
  const data = await response.json();
  if (!response.ok || !data.access_token) throw new Error("Daraja authentication failed.");
  return String(data.access_token);
}
function metadataValue(items: any[], name: string): unknown {
  return items.find((item) => String(item.Name).toLowerCase() === name.toLowerCase())?.Value;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "Method not allowed." }, 405);

  try {
    const body = await req.json();
    // Safaricom callback: protected by a high-entropy URL token configured in Supabase secrets.
    if (body?.Body?.stkCallback) {
      const callbackToken = new URL(req.url).searchParams.get("callback_token") ?? "";
      const expectedToken = Deno.env.get("MPESA_CALLBACK_TOKEN") ?? "";
      if (!expectedToken || callbackToken.length < 32 || callbackToken !== expectedToken) {
        return json({ error: "Callback authentication failed." }, 401);
      }
      const cb = body.Body.stkCallback;
      const checkoutId = String(cb.CheckoutRequestID ?? "");
      const merchantId = String(cb.MerchantRequestID ?? "");
      const resultCode = Number(cb.ResultCode);
      if (!checkoutId || !merchantId || !Number.isInteger(resultCode)) {
        return json({ error: "Incomplete Daraja callback." }, 400);
      }
      const { data: attempt, error } = await db.from("mpesa_subscription_attempts")
        .select("id,expected_amount_kes,payer_phone,status,checkout_request_id,expires_at")
        .eq("checkout_request_id", checkoutId).maybeSingle();
      if (error || !attempt) return json({ error: "Payment attempt not found." }, 404);
      if (attempt.status === "completed") return json({ ResultCode: 0, ResultDesc: "Already processed." });
      if (attempt.status !== "pending") return json({ ResultCode: 0, ResultDesc: "Attempt is not pending." });

      if (resultCode !== 0) {
        await db.from("mpesa_subscription_attempts").update({
          status: "failed", result_code: resultCode,
          result_description: String(cb.ResultDesc ?? "Payment was not completed").slice(0, 500),
          updated_at: new Date().toISOString(),
        }).eq("id", attempt.id).eq("status", "pending");
        return json({ ResultCode: 0, ResultDesc: "Payment failure recorded." });
      }

      const items = cb.CallbackMetadata?.Item ?? [];
      const amount = Number(metadataValue(items, "Amount"));
      const phone = normalizePhone(String(metadataValue(items, "PhoneNumber") ?? ""));
      const receipt = String(metadataValue(items, "MpesaReceiptNumber") ?? "");
      if (amount !== attempt.expected_amount_kes || phone !== attempt.payer_phone || !receipt) {
        await db.from("mpesa_subscription_attempts").update({
          status: "failed", result_code: -1,
          result_description: "Callback amount, payer or receipt did not match the pending request.",
          updated_at: new Date().toISOString(),
        }).eq("id", attempt.id).eq("status", "pending");
        return json({ error: "Payment callback did not match the expected payment." }, 422);
      }
      const { data: subscriptionId, error: activationError } = await db.rpc("activate_mpesa_subscription", {
        p_attempt_id: attempt.id,
        p_result_code: resultCode,
        p_amount_kes: amount,
        p_payer_phone: phone,
        p_receipt: receipt,
        p_checkout_request_id: checkoutId,
        p_result_description: String(cb.ResultDesc ?? "Payment verified"),
      });
      if (activationError) {
        console.error("M-PESA subscription activation rejected:", activationError.message);
        return json({ error: "Payment was received but subscription activation needs reconciliation." }, 409);
      }
      return json({ ResultCode: 0, ResultDesc: "Subscription activated.", subscription_id: subscriptionId });
    }

    const authorization = req.headers.get("Authorization") ?? "";
    const jwt = authorization.match(/^Bearer\\s+(.+)$/i)?.[1];
    if (!jwt) return json({ error: "Sign in to purchase a subscription." }, 401);
    const { data: authData, error: authError } = await db.auth.getUser(jwt);
    const user = authError ? null : authData.user;
    if (!user) return json({ error: "Your session is invalid. Sign in again." }, 401);

    const businessId = String(body.business_id ?? "");
    const planCode = String(body.plan_code ?? "");
    const payerPhone = normalizePhone(String(body.payer_phone ?? ""));
    if (!/^[0-9a-f-]{36}$/i.test(businessId) || !/^[a-z0-9_-]{2,40}$/.test(planCode) || !payerPhone) {
      return json({ error: "Business, plan or M-PESA phone number is invalid." }, 400);
    }
    const { data: membership } = await db.from("business_memberships").select("role")
      .eq("business_id", businessId).eq("user_id", user.id).eq("is_active", true)
      .in("role", ["owner", "business_admin", "agent_manager"]).maybeSingle();
    if (!membership) return json({ error: "You are not allowed to manage this business subscription." }, 403);

    const { data: plan, error: planError } = await db.from("platform_subscription_plans")
      .select("code,price_kes,duration_seconds,is_active")
      .eq("code", planCode).eq("is_active", true).maybeSingle();
    if (planError || !plan || !Number.isInteger(plan.price_kes) || plan.price_kes <= 0) {
      return json({ error: "The selected subscription plan is unavailable." }, 404);
    }

    const shortcode = Deno.env.get("MPESA_BUSINESS_SHORTCODE") ?? "";
    const consumerKey = Deno.env.get("MPESA_CONSUMER_KEY") ?? "";
    const consumerSecret = Deno.env.get("MPESA_CONSUMER_SECRET") ?? "";
    const passkey = Deno.env.get("MPESA_PASSKEY") ?? "";
    const callbackToken = Deno.env.get("MPESA_CALLBACK_TOKEN") ?? "";
    const environment = (Deno.env.get("MPESA_ENV") ?? "sandbox").toLowerCase();
    if (!shortcode || !consumerKey || !consumerSecret || !passkey || callbackToken.length < 32) {
      return json({ error: "M-PESA Daraja is not configured yet. No payment request was sent.", code: "MPESA_NOT_CONFIGURED" }, 503);
    }
    if (environment !== "sandbox" && environment !== "production") {
      return json({ error: "M-PESA environment must be sandbox or production." }, 503);
    }
    // This integration is explicitly for the user's approved Buy Goods Till.
    if (shortcode !== "1561763") {
      return json({ error: "Configured M-PESA shortcode does not match the approved Buy Goods Till." }, 409);
    }

    const { data: openAttempt } = await db.from("mpesa_subscription_attempts")
      .select("id,status,checkout_request_id,expires_at")
      .eq("business_id", businessId).in("status", ["initiating", "pending"])
      .gt("expires_at", new Date().toISOString()).maybeSingle();
    if (openAttempt) return json({ error: "An M-PESA subscription payment is already in progress.", attempt_id: openAttempt.id }, 409);

    const { data: attempt, error: insertError } = await db.from("mpesa_subscription_attempts").insert({
      business_id: businessId, requested_by: user.id, plan_code: plan.code,
      expected_amount_kes: plan.price_kes, payer_phone: payerPhone, status: "initiating",
    }).select("id").single();
    if (insertError || !attempt) return json({ error: "Could not create the payment attempt." }, 500);

    try {
      const baseUrl = environment === "production" ? "https://api.safaricom.co.ke" : "https://sandbox.safaricom.co.ke";
      const token = await accessToken(baseUrl, consumerKey, consumerSecret);
      const timestamp = timestampEAT();
      const password = btoa(`${shortcode}${passkey}${timestamp}`);
      const callbackUrl = `${supabaseUrl}/functions/v1/mpesa-subscriptions?callback_token=${encodeURIComponent(callbackToken)}`;
      const response = await fetch(`${baseUrl}/mpesa/stkpush/v1/processrequest`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          BusinessShortCode: shortcode,
          Password: password,
          Timestamp: timestamp,
          TransactionType: "CustomerBuyGoodsOnline",
          Amount: plan.price_kes,
          PartyA: payerPhone,
          PartyB: shortcode,
          PhoneNumber: payerPhone,
          CallBackURL: callbackUrl,
          AccountReference: `BPS${attempt.id.replaceAll("-", "").slice(0, 9)}`,
          TransactionDesc: "BingwaPulse",
        }),
      });
      const result = await response.json();
      if (!response.ok || !result.CheckoutRequestID || !result.MerchantRequestID) {
        await db.from("mpesa_subscription_attempts").update({
          status: "failed", result_description: String(result.errorMessage ?? result.ResponseDescription ?? "Daraja rejected the request").slice(0, 500),
          updated_at: new Date().toISOString(),
        }).eq("id", attempt.id);
        return json({ error: "Safaricom did not accept the M-PESA request.", details: result.errorMessage ?? result.ResponseDescription ?? null }, 502);
      }
      await db.from("mpesa_subscription_attempts").update({
        status: "pending", merchant_request_id: result.MerchantRequestID,
        checkout_request_id: result.CheckoutRequestID,
        updated_at: new Date().toISOString(),
      }).eq("id", attempt.id);
      return json({
        status: "pending_pin_authorization",
        attempt_id: attempt.id,
        message: "M-PESA PIN request sent. Enter your PIN on the M-PESA prompt to authorize payment.",
      });
    } catch (error) {
      await db.from("mpesa_subscription_attempts").update({
        status: "failed", result_description: String(error).slice(0, 500),
        updated_at: new Date().toISOString(),
      }).eq("id", attempt.id);
      console.error("M-PESA STK initiation failed:", error);
      return json({ error: "Could not initiate the M-PESA payment request." }, 502);
    }
  } catch (error) {
    console.error("M-PESA subscription handler error:", error);
    return json({ error: "Unexpected M-PESA subscription service error." }, 500);
  }
});
