import { createClient } from "https://esm.sh/@supabase/supabase-js@2.118.0";

const SUPABASE_URL = "https://tuuhucisljlaaovtnbeu.supabase.co";
const SUPABASE_PUBLISHABLE_KEY = "sb_publishable_6ymGXYgVhVjk5GXY_KfYfA_3hul4qS8";
const supabase = createClient(SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY, {
  auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true }
});

const $ = (id) => document.getElementById(id);
const state = { session: null, business: null, role: null, page: "overview", metrics: {}, orders: [], offers: [], providers: [], customers: [], payments: [], candidates: [], rules: [], channels: [], devices: [] };
let createAccountMode = false;
let toastTimer;

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (ch) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[ch]);
}
function kes(value) {
  const amount = Number(value || 0);
  return new Intl.NumberFormat("en-KE", { style: "currency", currency: "KES", maximumFractionDigits: 0 }).format(amount);
}
function when(value) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return new Intl.DateTimeFormat("en-KE", { dateStyle: "medium", timeStyle: "short", timeZone: "Africa/Nairobi" }).format(date);
}
function slugify(value) {
  const base = String(value).normalize("NFKD").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 40);
  const suffix = crypto.randomUUID().slice(0, 4);
  return (base || "business") + "-" + suffix;
}
function flash(message, error = false) {
  const el = $("toast");
  el.textContent = message;
  el.style.borderColor = error ? "rgba(255,143,131,.38)" : "rgba(154,240,100,.3)";
  el.classList.add("visible");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove("visible"), 3200);
}
function setMessage(id, message, type = "") {
  const el = $(id);
  if (!el) return;
  el.textContent = message;
  el.className = "form-message" + (type ? " " + type : "");
}
function showGate(name) {
  $("authGate").hidden = name !== "auth";
  $("workspaceGate").hidden = name !== "workspace";
  $("appShell").hidden = name !== "app";
}
function setAuthMode() {
  $("authTitle").textContent = createAccountMode ? "Create your account." : "Welcome back.";
  $("authDescription").textContent = createAccountMode ? "Start your secure BingwaPulse workspace." : "Sign in to manage your telecom business.";
  $("authSubmit").textContent = createAccountMode ? "Create account" : "Sign in";
  $("authSwitchText").textContent = createAccountMode ? "Already have an account?" : "New to BingwaPulse?";
  $("authSwitch").textContent = createAccountMode ? "Sign in" : "Create an account";
  $("authPassword").autocomplete = createAccountMode ? "new-password" : "current-password";
  setMessage("authMessage", "");
}
function normalizeKenyanPhone(input) {
  const digits = String(input || "").replace(/\D/g, "");
  if (digits.startsWith("254") && digits.length === 12) return digits;
  if (digits.startsWith("0") && digits.length === 10) return "254" + digits.slice(1);
  if ((digits.startsWith("7") || digits.startsWith("1")) && digits.length === 9) return "254" + digits;
  return null;
}
function statusPill(value) {
  const status = String(value || "unknown");
  const lower = status.toLowerCase();
  let tone = "";
  if (["delivered", "paid", "verified", "matched", "active", "online"].includes(lower)) tone = "good";
  else if (["failed", "rejected", "ambiguous", "mismatch", "revoked"].includes(lower)) tone = "bad";
  else if (["review_required", "reconciliation_required", "unverified", "pending", "inactive", "unknown", "awaiting_payment", "pending_pairing", "offline", "paused"].includes(lower)) tone = "warn";
  return '<span class="pill ' + tone + '">' + escapeHtml(status.replaceAll("_", " ")) + "</span>";
}
function emptyState(title, description) {
  return '<div class="empty-state"><b>' + escapeHtml(title) + "</b>" + escapeHtml(description) + "</div>";
}
function tableMarkup(headers, rows, emptyTitle, emptyDescription) {
  if (!rows.length) return '<div class="table-panel">' + emptyState(emptyTitle, emptyDescription) + "</div>";
  return '<div class="table-panel"><div class="table-scroll"><table><thead><tr>' + headers.map(h => "<th>" + escapeHtml(h) + "</th>").join("") + '</tr></thead><tbody>' + rows.join("") + "</tbody></table></div></div>";
}
function orderRows(rows) {
  return rows.map(o => "<tr><td><strong>" + escapeHtml(o.recipient_phone) + "</strong></td><td>" + escapeHtml(o.payer_phone || "—") + "</td><td>" + statusPill(o.status) + "</td><td>" + kes(o.customer_price_kes) + "</td><td>" + when(o.created_at) + "</td></tr>");
}
function renderOverview() {
  const review = state.orders.filter(o => ["REVIEW_REQUIRED", "RECONCILIATION_REQUIRED", "UNKNOWN"].includes(o.status)).length;
  const recent = orderRows(state.orders.slice(0, 6));
  $("pageContent").innerHTML = `
    <div class="stat-grid">
      <article class="stat-card"><div class="stat-label">TOTAL ORDERS</div><div class="stat-value">${state.metrics.orders ?? 0}</div><div class="stat-note">All recorded business orders</div></article>
      <article class="stat-card"><div class="stat-label">OFFERS AVAILABLE</div><div class="stat-value">${state.metrics.offers ?? 0}</div><div class="stat-note">Active offer records</div></article>
      <article class="stat-card"><div class="stat-label">CUSTOMERS</div><div class="stat-value">${state.metrics.customers ?? 0}</div><div class="stat-note">Saved customer contacts</div></article>
      <article class="stat-card"><div class="stat-label">NEEDS REVIEW</div><div class="stat-value">${review}</div><div class="stat-note">Orders not ready for fulfilment</div></article>
    </div>
    <div class="notice"><strong>Payment safety:</strong> an M-PESA phone notification is evidence to review, not proof that a payment is verified. BingwaPulse does not start automatic USSD purchases from this dashboard.</div>
    <div class="section-heading"><div><h2>Latest orders</h2><p>Most recent order activity in this workspace.</p></div><button class="button outline small" data-action="go-orders">View all orders</button></div>
    ${tableMarkup(["Recipient", "Payer", "Status", "Customer price", "Created"], recent, "No orders yet", "New orders will appear here when they are recorded.")}
    <div class="two-col">
      <section class="panel"><div class="panel-head"><div><h2>Provider network</h2><p>Configured providers for this business</p></div></div><div class="count-line">${state.providers.length} provider record(s) · ${state.providers.filter(p => p.status === "active").length} active</div><div class="count-line">New providers start inactive until checked.</div></section>
      <section class="panel"><div class="panel-head"><div><h2>Payment review</h2><p>Payment records visible to business managers</p></div></div><div class="count-line">${state.payments.filter(p => p.verification_status === "verified").length} verified payment(s)</div><div class="count-line">${state.candidates.filter(c => c.match_status !== "matched").length} notification candidate(s) need review</div></section>
    </div>`;
}
function renderOrders() {
  $("pageContent").innerHTML = `
    <div class="section-heading"><div><h2>Order register</h2><p>Order history and fulfilment status for this business.</p></div><button class="button outline small" data-action="refresh">Refresh data</button></div>
    <div class="notice"><strong>Fulfilment guard:</strong> review-required and unverified orders are not dispatched by the web dashboard.</div>
    ${tableMarkup(["Recipient", "Payer", "Status", "Customer price", "Created"], orderRows(state.orders), "No orders recorded", "Orders will show here when they are received or entered into BingwaPulse.")}
    <p class="empty-note">Showing the latest ${state.orders.length} order record(s). Access is restricted by your workspace membership.</p>`;
}
function renderOffers() {
  const providerRows = state.providers.map(p => "<tr><td><strong>" + escapeHtml(p.name) + "</strong></td><td>" + escapeHtml(p.adapter_key) + "</td><td>" + statusPill(p.status) + "</td><td>" + when(p.created_at) + "</td></tr>");
  const offerRows = state.offers.map(o => "<tr><td><strong>" + escapeHtml(o.name) + "</strong><small class='muted-cell'>" + escapeHtml(o.code || o.external_offer_ref) + "</small></td><td>" + escapeHtml(o.category) + "</td><td>" + escapeHtml(o.providers?.name || "—") + "</td><td>" + kes(o.provider_cost_kes) + "</td><td>" + statusPill(o.is_active ? "active" : "inactive") + "</td></tr>");
  const canManage = ["owner", "business_admin", "agent_manager"].includes(state.role);
  $("pageContent").innerHTML = `
    <div class="section-heading"><div><h2>Offer catalogue</h2><p>Keep provider and offer details accurate for your business.</p></div><div class="action-row">${canManage ? '<button class="button outline small" data-action="new-provider">Add provider</button><button class="button primary small" data-action="new-offer">Add offer</button>' : ""}</div></div>
    ${tableMarkup(["Provider", "Connection type", "Status", "Created"], providerRows, "No providers yet", "Add a provider before creating offers.")}
    <div class="section-heading"><div><h2>Offers</h2><p>Offers are business-specific and start inactive until you enable them.</p></div></div>
    ${tableMarkup(["Offer", "Category", "Provider", "Provider cost", "Status"], offerRows, "No offers configured", "Add verified offer details for this workspace.")}
    <p class="empty-note">Do not invent offer prices, menu codes or provider routes. Only add details you have confirmed.</p>`;
}
function renderCustomers() {
  const rows = state.customers.map(c => "<tr><td><strong>" + escapeHtml(c.display_name || "Customer") + "</strong></td><td>" + escapeHtml(c.phone_e164) + "</td><td>" + when(c.created_at) + "</td></tr>");
  const canManage = ["owner", "business_admin", "agent_manager", "agent"].includes(state.role);
  $("pageContent").innerHTML = `
    <div class="section-heading"><div><h2>Customer book</h2><p>Contact records belonging to this business workspace.</p></div>${canManage ? '<button class="button primary small" data-action="new-customer">Add customer</button>' : ""}</div>
    ${tableMarkup(["Customer", "Phone number", "Added"], rows, "No customers saved", "Add a customer contact to start building your customer book.")}
    <p class="empty-note">Phone numbers are stored in international format beginning with 254.</p>`;
}
function renderPayments() {
  const paymentRows = state.payments.map(p => "<tr><td><strong>" + escapeHtml(p.receipt_code) + "</strong></td><td>" + kes(p.amount_kes) + "</td><td>" + escapeHtml(p.payer_phone || "—") + "</td><td>" + statusPill(p.verification_status) + "</td><td>" + when(p.created_at) + "</td></tr>");
  const candidateRows = state.candidates.map(c => "<tr><td><strong>" + escapeHtml(c.receipt_candidate || "Not detected") + "</strong></td><td>" + (c.amount_minor ? kes(Number(c.amount_minor) / 100) : "—") + "</td><td>" + escapeHtml(c.sender_phone_candidate || "—") + "</td><td>" + statusPill(c.match_status) + "</td><td>" + when(c.created_at) + "</td></tr>");
  $("pageContent").innerHTML = `
    <div class="section-heading"><div><h2>Payment control</h2><p>View recorded payments and device notification evidence.</p></div><button class="button outline small" data-action="refresh">Refresh data</button></div>
    <div class="notice"><strong>Not a payment confirmation:</strong> notification candidates are not verified payments. Only an authorised verification process can confirm funds. Do not fulfil an order based on a notification alone.</div>
    <div class="section-heading"><div><h2>Payment records</h2><p>Payment verification states recorded in the database.</p></div></div>
    ${tableMarkup(["M-PESA receipt", "Amount", "Payer", "Verification", "Recorded"], paymentRows, "No payment records", "Verified callback records will appear here when the payment service is configured.")}
    <div class="section-heading"><div><h2>Phone notification candidates</h2><p>Best-effort device extraction, retained for matching and review.</p></div></div>
    ${tableMarkup(["Receipt candidate", "Amount", "Sender", "Match state", "Captured"], candidateRows, "No notification candidates", "Captured M-PESA notification evidence will appear here after a signed-in Android device relays it.")}
    <p class="empty-note">Raw notification text is not displayed in this browser dashboard.</p>`;
}
function renderAutomation() {
  const canManage = ["owner", "business_admin"].includes(state.role);
  const channelRows = state.channels.map(c => "<tr><td><strong>" + escapeHtml(c.shortcode) + "</strong></td><td>" + escapeHtml(c.channel_type) + "</td><td>" + statusPill(c.is_active ? "active" : "inactive") + "</td><td>" + when(c.created_at) + "</td></tr>");
  const ruleRows = state.rules.map(r => "<tr><td><strong>" + kes(Number(r.amount_minor) / 100) + "</strong></td><td>" + escapeHtml(r.offers?.name || "Offer") + "</td><td>" + escapeHtml(r.menu_entry_code) + "</td><td>" + statusPill(r.is_enabled ? "active" : "inactive") + "</td></tr>");
  const deviceRows = state.devices.map(d => "<tr><td><strong>" + escapeHtml(d.device_label) + "</strong></td><td>" + escapeHtml(d.platform) + "</td><td>" + statusPill(d.status) + "</td><td>" + when(d.last_seen_at) + "</td></tr>");
  $("pageContent").innerHTML = `
    <div class="section-heading"><div><h2>Automation readiness</h2><p>Configuration records and Android worker visibility.</p></div><button class="button outline small" data-action="refresh">Refresh data</button></div>
    <div class="notice"><strong>Automation is not live.</strong> The callback service has not been deployed or registered with Safaricom, and the Android USSD worker is still observation-only. This page cannot initiate purchases or mark payments as paid.</div>
    <div class="section-heading"><div><h2>M-PESA collection channels</h2><p>New channels are saved inactive. Callback registration must be completed separately.</p></div>${canManage ? '<button class="button primary small" data-action="new-channel">Add Till / PayBill</button>' : ""}</div>
    ${tableMarkup(["Shortcode", "Type", "Status", "Created"], channelRows, "No M-PESA channel configured", "Add your Till or PayBill as an inactive record.")}
    <div class="section-heading"><div><h2>Amount-to-offer rules</h2><p>Every amount maps to one specific offer. Rules remain inactive until the real menu flow is verified.</p></div>${canManage ? '<button class="button outline small" data-action="new-rule">Add offer rule</button>' : ""}</div>
    ${tableMarkup(["Payment amount", "Offer", "USSD entry code", "Rule status"], ruleRows, "No offer rules configured", "Add a rule only after confirming the exact payment amount and USSD entry code.")}
    <div class="section-heading"><div><h2>Worker devices</h2><p>Android devices paired with this business workspace.</p></div></div>
    ${tableMarkup(["Device", "Platform", "Status", "Last seen"], deviceRows, "No Android worker paired", "Install BingwaPulse Android and complete the device-pairing setup when it is available.")}
    <p class="empty-note">This is a readiness view only. No background purchases, automatic USSD confirmations or live payment actions are enabled.</p>`;
}
function renderPage() {
  const map = {
    overview: ["BUSINESS OVERVIEW", "Overview", renderOverview],
    orders: ["ORDER MANAGEMENT", "Orders", renderOrders],
    offers: ["CATALOGUE MANAGEMENT", "Offers", renderOffers],
    customers: ["CUSTOMER MANAGEMENT", "Customers", renderCustomers],
    payments: ["PAYMENT CONTROL", "Payments", renderPayments],
    automation: ["DEVICE & AUTOMATION", "Automation", renderAutomation]
  };
  const selected = map[state.page] || map.overview;
  $("pageEyebrow").textContent = selected[0];
  $("pageTitle").textContent = selected[1];
  document.querySelectorAll(".nav-item").forEach(el => el.classList.toggle("active", el.dataset.page === state.page));
  selected[2]();
}
async function countRows(table, businessId, filter) {
  let query = supabase.from(table).select("id", { count: "exact", head: true }).eq("business_id", businessId);
  if (filter) query = filter(query);
  const { count, error } = await query;
  return error ? 0 : (count ?? 0);
}
async function rowsFor(table, columns, businessId, limit = 50) {
  const { data, error } = await supabase.from(table).select(columns).eq("business_id", businessId).order("created_at", { ascending: false }).limit(limit);
  if (error) {
    console.warn("BingwaPulse table read skipped:", table, error.message);
    return [];
  }
  return data || [];
}
async function loadWorkspaceData() {
  if (!state.business) return;
  const id = state.business.id;
  const results = await Promise.all([
    countRows("orders", id),
    countRows("offers", id, q => q.eq("is_active", true)),
    countRows("customers", id),
    countRows("providers", id),
    rowsFor("orders", "id,recipient_phone,payer_phone,status,customer_price_kes,selling_price_kes,created_at", id),
    rowsFor("offers", "id,provider_id,name,category,code,external_offer_ref,provider_cost_kes,is_active,created_at,providers(name)", id),
    rowsFor("providers", "id,name,adapter_key,status,created_at", id),
    rowsFor("customers", "id,phone_e164,display_name,created_at", id),
    rowsFor("payments", "id,receipt_code,amount_kes,payer_phone,verification_status,evidence_source,created_at", id),
    rowsFor("payment_notification_candidates", "id,receipt_candidate,amount_minor,sender_phone_candidate,candidate_status,match_status,created_at", id),
    rowsFor("payment_offer_rules", "id,amount_minor,menu_entry_code,is_enabled,menu_flow_verified_at,created_at,offers(name)", id),
    rowsFor("payment_ingress_channels", "id,shortcode,channel_type,is_active,created_at", id),
    rowsFor("devices", "id,device_label,platform,status,last_seen_at,created_at", id)
  ]);
  [state.metrics.orders, state.metrics.offers, state.metrics.customers, state.metrics.providers,
    state.orders, state.offers, state.providers, state.customers, state.payments, state.candidates,
    state.rules, state.channels, state.devices] = results;
  $("navOrderCount").textContent = String(state.metrics.orders);
  renderPage();
}
async function getMemberships(userId) {
  const { data, error } = await supabase.from("business_memberships").select("business_id,role").eq("user_id", userId).eq("is_active", true).order("created_at", { ascending: true });
  if (error) throw error;
  const memberships = [];
  for (const member of data || []) {
    const { data: business, error: businessError } = await supabase.from("businesses").select("id,name,slug,currency,timezone").eq("id", member.business_id).maybeSingle();
    if (!businessError && business) memberships.push({ ...business, role: member.role });
  }
  return memberships;
}
async function continueWithSession(session) {
  state.session = session;
  const { data, error } = await supabase.auth.getUser();
  if (error || !data.user) {
    showGate("auth");
    setMessage("authMessage", "Your session could not be confirmed. Please sign in again.", "error");
    return;
  }
  $("userEmail").textContent = data.user.email || "Signed in";
  const memberships = await getMemberships(data.user.id);
  if (!memberships.length) {
    showGate("workspace");
    $("businessName").value = "Gabriel’s Internet Services";
    setMessage("workspaceMessage", "Your account is ready. Create your business workspace to continue.");
    return;
  }
  state.business = memberships[0];
  state.role = memberships[0].role;
  $("sidebarBusiness").textContent = state.business.name;
  showGate("app");
  await loadWorkspaceData();
}
async function startApp() {
  showGate("auth");
  setMessage("authMessage", "");
  const { data, error } = await supabase.auth.getSession();
  if (error) {
    setMessage("authMessage", error.message, "error");
    return;
  }
  if (data.session) {
    try { await continueWithSession(data.session); }
    catch (err) { setMessage("authMessage", err.message || "Could not load workspace.", "error"); }
  }
}
function openModal(title, description, fields, submitText) {
  $("modalContent").innerHTML = '<h2>' + escapeHtml(title) + '</h2><p>' + escapeHtml(description) + '</p><form class="modal-form" data-modal-form="' + escapeHtml(fields.kind) + '">' + fields.html + '<button class="button primary wide" type="submit">' + escapeHtml(submitText || "Save") + "</button></form>";
  $("modal").showModal();
}
function providerModal() {
  openModal("Add a provider", "Create an inactive provider record. Do not activate a connection until its method is confirmed.", {
    kind: "provider",
    html: '<label>Provider name<input name="name" maxlength="100" placeholder="e.g. Safaricom" required></label><label>Connection method<select name="adapter_key"><option value="manual">Manual review</option><option value="ussd_assisted">USSD assisted (not automated)</option></select></label>'
  }, "Save provider");
}
function offerModal() {
  if (!state.providers.length) return flash("Add a provider first.", true);
  const choices = state.providers.map(p => '<option value="' + escapeHtml(p.id) + '">' + escapeHtml(p.name) + "</option>").join("");
  openModal("Add an offer", "Enter an offer you have confirmed. New offer records are saved inactive.", {
    kind: "offer",
    html: '<label>Provider<select name="provider_id" required>' + choices + '</select></label><label>Offer name<input name="name" maxlength="140" required placeholder="e.g. 1GB / 24 hours"></label><label>Offer reference<input name="external_offer_ref" maxlength="120" required placeholder="Unique provider reference"></label><label>Offer code (optional)<input name="code" maxlength="80"></label><label>Category<select name="category" required><option value="data">Data</option><option value="sms">SMS</option><option value="minutes">Minutes</option><option value="combo">Combo</option></select></label><label>Provider cost (KES)<input name="provider_cost_kes" type="number" min="0" step="1" required></label><label>Description (optional)<textarea name="description" rows="2" maxlength="500"></textarea></label>'
  }, "Save inactive offer");
}
function customerModal() {
  openModal("Add a customer", "Save a contact in this business workspace.", {
    kind: "customer",
    html: '<label>Customer name (optional)<input name="display_name" maxlength="120"></label><label>Phone number<input name="phone" inputmode="tel" placeholder="07xx xxx xxx or 2547xx xxx xxx" required></label>'
  }, "Save customer");
}
function channelModal() {
  openModal("Add a collection channel", "This record will be saved inactive. Saving it does not register a Safaricom callback or accept live payments.", {
    kind: "channel",
    html: '<label>Till / PayBill number<input name="shortcode" inputmode="numeric" value="1561763" pattern="[0-9]{4,20}" required></label><label>Channel type<select name="channel_type"><option value="till">Buy Goods Till</option><option value="paybill">PayBill</option></select></label><p class="empty-note">The saved channel stays inactive until the callback service is deployed and configured.</p>'
  }, "Save inactive channel");
}
function ruleModal() {
  if (!state.offers.length) return flash("Add a confirmed offer before creating an amount rule.", true);
  const choices = state.offers.map(o => '<option value="' + escapeHtml(o.id) + '">' + escapeHtml(o.name) + " · " + escapeHtml(o.category) + "</option>").join("");
  openModal("Add amount-to-offer rule", "The rule stays inactive. Use a real USSD entry code confirmed on your Safaricom SIM. This does not enable automatic buying.", {
    kind: "rule",
    html: '<label>Offer<select name="offer_id" required>' + choices + '</select></label><label>Customer payment amount (KES)<input name="amount_kes" type="number" min="1" step="1" required></label><label>Confirmed USSD entry code<input name="menu_entry_code" placeholder="e.g. *xxx#" pattern="\*[0-9*]+#" required></label><p class="empty-note">Menu steps and verification remain empty. Automatic fulfilment remains disabled until the real menu flow is captured and reviewed.</p>'
  }, "Save inactive rule");
}
async function submitModal(form) {
  const values = Object.fromEntries(new FormData(form).entries());
  const business_id = state.business.id;
  let result;
  if (form.dataset.modalForm === "provider") {
    result = await supabase.from("providers").insert({ business_id, name: values.name.trim(), adapter_key: values.adapter_key, status: "inactive" });
  } else if (form.dataset.modalForm === "offer") {
    result = await supabase.from("offers").insert({
      business_id, provider_id: values.provider_id, external_offer_ref: values.external_offer_ref.trim(),
      code: values.code.trim() || null, name: values.name.trim(), category: values.category,
      description: values.description.trim() || null, provider_cost_kes: Number(values.provider_cost_kes), is_active: false
    });
  } else if (form.dataset.modalForm === "customer") {
    const phone = normalizeKenyanPhone(values.phone);
    if (!phone) throw new Error("Enter a valid Kenyan mobile number, such as 0712 345 678.");
    result = await supabase.from("customers").insert({ business_id, phone_e164: phone, display_name: values.display_name.trim() || null, created_by: state.session.user.id });
  } else if (form.dataset.modalForm === "channel") {
    const shortcode = values.shortcode.trim();
    if (!/^[0-9]{4,20}$/.test(shortcode)) throw new Error("Enter a valid Till or PayBill number.");
    result = await supabase.from("payment_ingress_channels").insert({ business_id, shortcode, channel_type: values.channel_type, is_active: false });
  } else if (form.dataset.modalForm === "rule") {
    const amount = Number(values.amount_kes);
    if (!Number.isSafeInteger(amount) || amount < 1) throw new Error("Enter a whole-number KES amount.");
    if (!/^\*[0-9*]+#$/.test(values.menu_entry_code.trim())) throw new Error("Enter the exact USSD code, including * and #.");
    result = await supabase.from("payment_offer_rules").insert({
      business_id, offer_id: values.offer_id, amount_minor: amount * 100,
      menu_entry_code: values.menu_entry_code.trim(), menu_steps: [], is_enabled: false,
      created_by: state.session.user.id
    });
  }
  if (result?.error) throw result.error;
  $("modal").close();
  flash("Saved successfully.");
  await loadWorkspaceData();
}
async function createWorkspace(event) {
  event.preventDefault();
  const name = $("businessName").value.trim();
  if (name.length < 2) return setMessage("workspaceMessage", "Enter a business name with at least 2 characters.", "error");
  const button = event.submitter;
  if (button) button.disabled = true;
  setMessage("workspaceMessage", "Creating your secure workspace…");
  try {
    const { data, error } = await supabase.rpc("create_business", { p_name: name, p_slug: slugify(name) });
    if (error) throw error;
    const { data: sessionData, error: sessionError } = await supabase.auth.getSession();
    if (sessionError || !sessionData.session) throw sessionError || new Error("Please sign in again.");
    await continueWithSession(sessionData.session);
    flash("Business workspace created.");
  } catch (err) {
    setMessage("workspaceMessage", err.message || "Could not create the business workspace.", "error");
  } finally {
    if (button) button.disabled = false;
  }
}
function updatePage(page) {
  state.page = page;
  renderPage();
}
document.addEventListener("click", async (event) => {
  const nav = event.target.closest("[data-page]");
  if (nav) { updatePage(nav.dataset.page); return; }
  const action = event.target.closest("[data-action]");
  if (!action) return;
  switch (action.dataset.action) {
    case "go-orders": updatePage("orders"); break;
    case "refresh":
      try { await loadWorkspaceData(); flash("Workspace data refreshed."); }
      catch (err) { flash(err.message || "Refresh failed.", true); }
      break;
    case "new-provider": providerModal(); break;
    case "new-offer": offerModal(); break;
    case "new-customer": customerModal(); break;
    case "new-channel": channelModal(); break;
    case "new-rule": ruleModal(); break;
  }
});
$("modalContent").addEventListener("submit", async (event) => {
  if (!event.target.matches("[data-modal-form]")) return;
  event.preventDefault();
  const button = event.target.querySelector("button[type=submit]");
  if (button) button.disabled = true;
  try { await submitModal(event.target); }
  catch (err) { flash(err.message || "Could not save this record.", true); }
  finally { if (button) button.disabled = false; }
});
$("authSwitch").addEventListener("click", () => { createAccountMode = !createAccountMode; setAuthMode(); });
$("authForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const email = $("authEmail").value.trim();
  const password = $("authPassword").value;
  const button = $("authSubmit");
  button.disabled = true;
  button.textContent = createAccountMode ? "Creating account…" : "Signing in…";
  setMessage("authMessage", "");
  try {
    if (createAccountMode) {
      const { data, error } = await supabase.auth.signUp({ email, password });
      if (error) throw error;
      if (!data.session) {
        setMessage("authMessage", "Account request submitted. Check your email to confirm the account, then return here and sign in.", "success");
      } else {
        await continueWithSession(data.session);
      }
    } else {
      const { data, error } = await supabase.auth.signInWithPassword({ email, password });
      if (error) throw error;
      await continueWithSession(data.session);
    }
  } catch (err) {
    setMessage("authMessage", err.message || "Could not complete sign-in.", "error");
  } finally {
    button.disabled = false;
    button.textContent = createAccountMode ? "Create account" : "Sign in";
  }
});
$("workspaceForm").addEventListener("submit", createWorkspace);
async function signOut() {
  const { error } = await supabase.auth.signOut();
  if (error) return flash(error.message, true);
  state.session = null; state.business = null; state.role = null;
  showGate("auth");
  flash("You have signed out.");
}
$("signOutButton").addEventListener("click", signOut);
$("workspaceSignOut").addEventListener("click", signOut);
startApp().catch(err => {
  console.error(err);
  showGate("auth");
  setMessage("authMessage", err.message || "BingwaPulse could not start.", "error");
});
