import { createClient } from "npm:@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Content-Type": "application/json",
};
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: cors });
const url = Deno.env.get("SUPABASE_URL");
const secret = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
if (!url || !secret) throw new Error("Storefront service configuration is missing.");
const db = createClient(url, secret, { auth: { persistSession: false, autoRefreshToken: false } });

function normalizePhone(value: unknown): string | null {
  const d = String(value ?? "").replace(/\D/g, "");
  if (d.startsWith("254") && d.length === 12) return d;
  if (d.startsWith("0") && d.length === 10) return "254" + d.slice(1);
  if ((d.startsWith("7") || d.startsWith("1")) && d.length === 9) return "254" + d;
  return null;
}

async function getStore(slug: string) {
  if (!/^[a-zA-Z0-9_-]{3,49}$/.test(slug)) return { error: "This store link is invalid.", status: 400 };
  const { data: link, error } = await db.from("site_links")
    .select("business_id,agent_user_id,slug,display_name,businesses(name)")
    .eq("slug", slug).eq("is_active", true).maybeSingle();
  if (error) return { error: "The store could not be loaded.", status: 500 };
  if (!link) return { error: "This store is unavailable or has been paused.", status: 404 };
  const { data: members, error: memberError } = await db.from("business_memberships")
    .select("role").eq("business_id", link.business_id).eq("user_id", link.agent_user_id)
    .eq("is_active", true).limit(1);
  if (memberError || !members?.length) return { error: "This store is not currently accepting orders.", status: 404 };
  const { data: rows, error: offerError } = await db.from("agent_offers")
    .select("selling_price_kes,offers!inner(id,name,category,description,is_active,valid_from,valid_until,stale_after)")
    .eq("business_id", link.business_id).eq("agent_user_id", link.agent_user_id).eq("is_published", true);
  if (offerError) return { error: "Offers could not be loaded.", status: 500 };
  const now = Date.now();
  const offers = (rows ?? []).flatMap((row: any) => {
    const o = row.offers;
    if (!o?.is_active || (o.valid_from && Date.parse(o.valid_from) > now) ||
        (o.valid_until && Date.parse(o.valid_until) <= now) ||
        (o.stale_after && Date.parse(o.stale_after) <= now)) return [];
    return [{ id: o.id, name: o.name, category: o.category, description: o.description, price_kes: row.selling_price_kes }];
  });
  const business = Array.isArray(link.businesses) ? link.businesses[0] : link.businesses;
  return { data: { slug: link.slug, store_name: link.display_name || business?.name || "BingwaPulse Store", business_name: business?.name || "Telecom business", offers } };
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  try {
    if (req.method === "GET") {
      const result = await getStore(new URL(req.url).searchParams.get("slug") ?? "");
      return result.error ? json({ error: result.error }, result.status) : json(result.data);
    }
    if (req.method !== "POST") return json({ error: "Method not allowed." }, 405);
    const raw = await req.text();
    if (raw.length > 6000) return json({ error: "Request is too large." }, 413);
    let body: any;
    try { body = JSON.parse(raw); } catch { return json({ error: "Invalid request body." }, 400); }
    const slug = String(body.slug ?? "");
    const offerId = String(body.offer_id ?? "");
    const recipient = normalizePhone(body.recipient_phone);
    const payer = normalizePhone(body.payer_phone);
    const key = String(body.idempotency_key ?? "");
    if (!recipient || !payer) return json({ error: "Enter valid Kenyan mobile numbers for the recipient and payer." }, 400);
    if (!/^[0-9a-f-]{36}$/i.test(offerId)) return json({ error: "Select a valid offer." }, 400);
    if (!/^[A-Za-z0-9-]{16,80}$/.test(key)) return json({ error: "Refresh the page and try again." }, 400);

    const store = await getStore(slug);
    if (store.error) return json({ error: store.error }, store.status);
    const { data: link, error: linkError } = await db.from("site_links")
      .select("business_id,agent_user_id,slug").eq("slug", slug).eq("is_active", true).maybeSingle();
    if (linkError || !link) return json({ error: "This store is no longer available." }, 404);

    const { data: assigned, error: assignedError } = await db.from("agent_offers")
      .select("selling_price_kes,offers!inner(id,provider_cost_kes,is_active,valid_from,valid_until,stale_after)")
      .eq("business_id", link.business_id).eq("agent_user_id", link.agent_user_id)
      .eq("offer_id", offerId).eq("is_published", true).maybeSingle();
    if (assignedError || !assigned || !assigned.offers?.is_active) return json({ error: "That offer is no longer available." }, 409);
    const offer = assigned.offers, now = Date.now();
    if ((offer.valid_from && Date.parse(offer.valid_from) > now) ||
        (offer.valid_until && Date.parse(offer.valid_until) <= now) ||
        (offer.stale_after && Date.parse(offer.stale_after) <= now)) return json({ error: "That offer has expired or is not currently available." }, 409);

    const idem = "sitelink:" + key;
    const { data: existing, error: existingError } = await db.from("orders")
      .select("id,status,offer_id,recipient_phone,payer_phone,customer_price_kes")
      .eq("business_id", link.business_id).eq("idempotency_key", idem).maybeSingle();
    if (existingError) return json({ error: "Could not check the previous request." }, 500);
    if (existing) {
      if (existing.offer_id !== offerId || existing.recipient_phone !== recipient ||
          existing.payer_phone !== payer || existing.customer_price_kes !== assigned.selling_price_kes)
        return json({ error: "This request key was already used for a different order. Refresh and try again." }, 409);
      return json({ order_id: existing.id, status: existing.status, amount_kes: existing.customer_price_kes, repeated: true });
    }

    const { data: customer, error: customerError } = await db.from("customers")
      .upsert({ business_id: link.business_id, phone_e164: recipient, created_by: link.agent_user_id },
        { onConflict: "business_id,phone_e164" }).select("id").single();
    if (customerError || !customer) return json({ error: "Could not save the recipient details." }, 500);

    const { data: order, error: orderError } = await db.from("orders").insert({
      business_id: link.business_id, agent_user_id: link.agent_user_id, customer_id: customer.id,
      offer_id: offerId, channel: "sitelink", recipient_phone: recipient, payer_phone: payer,
      status: "AWAITING_PAYMENT", currency: "KES", provider_cost_kes: offer.provider_cost_kes,
      selling_price_kes: assigned.selling_price_kes, customer_price_kes: assigned.selling_price_kes,
      idempotency_key: idem,
    }).select("id,status,customer_price_kes").single();
    if (orderError || !order) {
      if (orderError?.code === "23505") {
        const { data: retry } = await db.from("orders").select("id,status,offer_id,recipient_phone,payer_phone,customer_price_kes")
          .eq("business_id", link.business_id).eq("idempotency_key", idem).maybeSingle();
        if (retry && retry.offer_id === offerId && retry.recipient_phone === recipient && retry.payer_phone === payer)
          return json({ order_id: retry.id, status: retry.status, amount_kes: retry.customer_price_kes, repeated: true });
      }
      return json({ error: "The order request could not be saved. Please try again." }, 500);
    }
    return json({ order_id: order.id, status: order.status, amount_kes: order.customer_price_kes,
      message: "Order request received. Payment and delivery are not yet confirmed." }, 201);
  } catch (error) {
    console.error("SiteLink request failed:", error);
    return json({ error: "The storefront is temporarily unavailable. Please try again later." }, 500);
  }
});
