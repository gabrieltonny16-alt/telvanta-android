# BingwaPulse Unified Ecosystem Specification

Status: implementation baseline draft  
Repository: gabrieltonny16-alt/telvanta-android  
Working branch: feature/vecta-branding  
Protected baseline: main must remain untouched.

## 1. Product definition

BingwaPulse is an independent, multi-tenant Safaricom Bingwa Sokoni business automation platform. It is not a wrapper for Bingwa Nexus, Bingwa One, or another reseller product. It manages the complete business flow for data, SMS, voice minutes, and combo offers.

The ecosystem consists of five coordinated components:
1. BingwaPulse Android business application and on-device execution worker.
2. BingwaPulse Management Website for owners, administrators, and agents.
3. SiteLink customer storefront for browsing offers, placing orders, and tracking delivery.
4. ConnectPulse Android companion for device health, monitoring, diagnostics, and safe recovery assistance.
5. Shared backend and database providing identity, tenant isolation, synchronization, queues, audit records, and APIs.

These are one product ecosystem, not unrelated applications. A single authorized identity and tenant model must connect them.

## 2. Product boundaries and responsibilities

### 2.1 BingwaPulse Android
Primary mobile operations application and execution worker. It owns day-to-day order operations and authorized on-device purchasing.

Core areas:
- Dashboard and business overview.
- Orders and transaction history.
- Payments and receipt matching.
- Offers, categories, signature offers, and provider configuration.
- Customers, tags, customer history, and permitted blacklist controls.
- Agents, roles, and device assignments.
- SIM profiles and SIM selection.
- Quick Dial, Express USSD, and Advanced USSD.
- USSD execution and execution attempt history.
- Wallet and financial ledger views (no unverified balances).
- Auto Renewals, where explicitly configured and authorized.
- EngageBot / AutoReply and messaging tools, subject to channel permissions and separate live-use approval.
- Bulk SMS, subject to consent, provider rules, and explicit live-use approval.
- Statistics, reports, audit history, notifications, settings, and updates.
- Emergency stop controls.

The Android app must remain mobile-first, usable on Android phones, and preserve the existing project rather than being replaced.

### 2.2 BingwaPulse Management Website
A responsive web control centre sharing the same backend and authorization model as the Android app.

Required workspaces:
- Owner / platform administrator overview.
- Business and tenant management.
- Agent and role management.
- Device registration, pairing, assignment, and revocation.
- Provider and purchasing-route configuration.
- Offer catalogue and offer availability.
- Orders, payments, execution attempts, and delivery reconciliation.
- Customer records and customer support history.
- Wallet/ledger and financial reports.
- SiteLink storefront configuration.
- ConnectPulse device health, incidents, and recovery history.
- Audit logs, system settings, feature controls, and emergency stop.
- Exportable reports and operational analytics.

The website must be responsive for desktop and mobile browsers. Tenant-scoped users must never see another tenant's records. Platform-level administration must be separately permissioned and audited.

### 2.3 SiteLink Customer Storefront
A separate public-facing customer experience, configurable for each business.

Customer journey:
1. Open the business SiteLink URL.
2. Browse currently available data, SMS, minutes, and combo offers.
3. View offer price, allowance, validity, eligibility, and applicable conditions.
4. Select an offer.
5. Enter the recipient phone number.
6. Enter the payer phone number when different.
7. Review order details and payment instructions.
8. Submit the order and receive a unique order reference.
9. Complete the authorized payment method.
10. View payment and fulfilment status.
11. Receive a clear confirmation or support path if the order requires review.

Storefront requirements:
- Mobile-first, fast, accessible interface.
- Business branding and configurable contact/support details.
- Only display offers enabled for that business and currently available.
- Validate phone numbers and order inputs.
- Never claim payment or delivery before verification.
- Do not expose internal agent, device, provider, or tenant data.
- Prevent duplicate submissions using idempotency controls.
- Show pending, review, processing, delivered, and failed states accurately.
- Provide a customer order lookup mechanism with secure verification.
- Support per-business domain/subdomain or stable public link when hosting is selected.

SiteLink is not the management website. It is the customer-facing storefront.

### 2.4 ConnectPulse Companion
ConnectPulse is an assistance companion, not a second order-management or purchasing application.

Its responsibilities:
- Pair with authorized BingwaPulse identities/devices.
- Display paired device and worker health.
- Observe heartbeat, connectivity, permissions, SIM availability, worker status, queue state, and synchronization.
- Detect crashes, hangs, stalled work, and interrupted USSD sessions where evidence is available.
- Classify incidents and show affected orders.
- Attempt safe technical recovery automatically where Android and the available communication path permit.
- Notify an authorized agent/admin when recovery is unsafe or unsuccessful.
- Maintain an incident and recovery audit trail.

ConnectPulse does not independently execute business purchases. The BingwaPulse worker remains responsible for USSD execution.

A powered-off or OS-level unresponsive primary phone cannot be remotely restarted without a separate working recovery path, such as an independent secondary device.

### 2.5 Shared backend
The backend is the source of truth for shared identities, tenant membership, orders, payments, execution attempts, delivery evidence, devices, incidents, and audit records.

It must support:
- Authentication and session management.
- Multi-tenant business isolation.
- Role-based permissions.
- Secure device identity and revocation.
- Atomic order claiming and queue assignment.
- Idempotency and duplicate prevention.
- Synchronization and offline recovery.
- Realtime or appropriately timely status updates.
- Immutable or append-only financial and audit history where practical.
- API contracts shared by Android, management website, SiteLink, and ConnectPulse.

Supabase is the intended backend, subject to identifying the correct BingwaPulse project. The separate CREOVA project must not be used.

## 3. Multi-tenant identity and access

Core roles may include:
- Platform owner / platform administrator.
- Business owner.
- Business administrator.
- Agent / operator.
- Device worker identity.
- Support or auditor role with explicitly limited access.

Every tenant-owned record must carry the appropriate tenant/business identity. Access must be enforced server-side and through database policies, not only by filtering UI results.

Device pairing must use an authorization challenge and owner/admin approval. Credentials must be revocable and must not be shared as plain secrets in client code.

## 4. Offers and provider configuration

Offer catalogue supports:
- Data.
- SMS.
- Voice minutes.
- Combo bundles.
- Signature Offers / FREE SIGNATURE source imports where access is authorized.
- Offer source, source reference, price, allowance, validity, availability, restrictions, and last refresh.
- Business-specific offer enablement and pricing.
- Versioned provider menu definitions and purchasing routes.

Imported offer data must be validated before publication. A seed catalogue is not proof of live availability.

The exact provider purchasing mechanism remains an unresolved integration requirement and must not be fabricated.

## 5. Payment reception and order lifecycle

Payment sources may include authorized M-PESA notification observation, authorized SMS access, SiteLink checkout, manual review, and future trusted provider integrations. Notification Listener access and SMS inbox access are separate Android capabilities.

Payment matching must consider transaction reference, amount, payer, intended recipient, business/Till, order reference, and time. Ambiguous matches enter review; they must not be marked paid automatically.

Required order lifecycle:
RECEIVED -> VALIDATED -> PAID -> QUEUED -> CLAIMED -> USSD_SENT -> DELIVERED / FAILED / UNKNOWN

Additional operational states may include AWAITING_PAYMENT, REVIEW_REQUIRED, CANCELLED, and RECONCILIATION_REQUIRED. Exact transition rules must be implemented and tested.

Payment confirmation, USSD submission, and bundle delivery are distinct events. Never equate them.

## 6. Emergency stop

Support global, business, agent, device, and provider-level stop controls.

Activation must stop new execution assignments at the selected scope, preserve existing records, and allow safe monitoring/reconciliation to continue. An in-progress USSD interaction must be handled according to its last known state; do not blindly terminate or replay a potentially submitted purchase.

Only authorized roles may activate/deactivate a switch. Record actor, scope, reason, time, affected work, and release event. Restarting must not replay uncertain purchases.

## 7. Android SIM and USSD execution

The Android worker must register a unique device identity, authorized business/agent assignment, SIM profiles, heartbeat, permissions, and execution history.

Before execution:
1. Confirm payment verification.
2. Confirm order eligibility and tenant ownership.
3. Confirm the order is not already fulfilled or claimed.
4. Atomically claim the order.
5. Confirm authorized provider route and SIM subscription.
6. Confirm required permissions and service availability.
7. Start the supported USSD workflow.
8. Classify the menu and navigate only recognized options.
9. Validate offer, recipient, SIM, and cost where available.
10. Submit only when authorized.
11. Capture response evidence and report the result.

Unknown menus, unexpected prompts, invalid inputs, and permission failures must stop safely. Never store or transmit PINs or credentials.

Execution attempts require unique identifiers and idempotency controls. If submission may have occurred but the response is missing, mark UNKNOWN and reconcile before any retry. A worker restart must inspect persisted state and never replay uncertain purchases.

## 8. Delivery verification

USSD submission is not delivery. Delivery requires separate evidence, such as a trusted provider response, recognized delivery notification, or verified transaction reference.

Delivery states:
PENDING, CONFIRMED, FAILED, UNKNOWN, REQUIRES_REVIEW.

Evidence must match the correct order, recipient, offer, tenant, and attempt. Uncertain delivery must be reconciled before another purchase.

## 9. ConnectPulse heartbeat and recovery

Workers periodically report device ID, timestamp, connectivity, worker/service health, permissions, current execution state, and last synchronization.

When heartbeats are missed:
1. Mark the device potentially unavailable.
2. Check whether the interruption is temporary.
3. Inspect last-known execution state.
4. Identify whether a purchase may have been submitted.
5. Prevent unsafe replay or reassignment.
6. Attempt only safe technical recovery.
7. Alert the authorized agent/admin if unresolved.

Safe actions include controlled reconnect/backoff, retrying synchronization, restarting a worker component where permitted, refreshing health checks, recovering persisted local state, and reconciling records.

ConnectPulse must never repeat an uncertain purchase, invent payment/delivery success, delete history, bypass tenant authorization, disable emergency stop, or initiate spending without approval.

Incidents follow DETECTED -> CLASSIFIED -> RECOVERY_ATTEMPTED -> RESOLVED / ESCALATED and record all actions and outcomes.

## 10. Original business modules to preserve and integrate

The implementation audit must include:
- Dashboard.
- Wallet and financial ledger.
- Offers and Signature Offers.
- Orders and transactions.
- Quick Dial.
- SIM selection and SIM management.
- Express USSD and Advanced USSD.
- Auto Renewals.
- EngageBot / AutoReply.
- M-PESA, Till, PayBill, and SiteLink processing.
- Customers, tags, and blacklist controls.
- Signature / offer catalogue source.
- Statistics and reports.
- Vecta Connect / ConnectPulse pairing and device health.
- Settings, updates, account, and sign-out.
- Bulk SMS and messaging capabilities, subject to permissions and explicit live-operation approval.

Each feature must be classified as WORKING_VERIFIED, IMPLEMENTED_NOT_VERIFIED, PARTIAL, PLACEHOLDER, or NOT_IMPLEMENTED.

## 11. Security, safety, and operational constraints

- No cross-tenant data access.
- No duplicate purchases.
- No unverified payment marked paid.
- No unverified delivery marked delivered.
- No retry of an uncertain purchase without reconciliation.
- No deletion of financial or transaction history as a recovery shortcut.
- No live payments, live messaging, paid services, or live fulfilment without separate explicit approval.
- No claims of functionality or tests passing without evidence.
- Preserve existing repository and original launcher artwork.
- Continue on feature/vecta-branding; keep main untouched.
- Build for Android-phone-first operation and free-first hosting where feasible.

## 12. Website and application implementation scope

The current repository contains Android modules and shared contracts. The management website and SiteLink storefront must be added as first-class deliverables, not treated as future optional mockups.

Before choosing deployment services, compare free-tier and self-hostable options. Do not create paid resources or activate paid plans without approval. The web applications must use the same backend contracts and tenant authorization as the Android applications.

## 13. Acceptance and release criteria

A feature is complete only when:
- It is implemented.
- Its permission and failure behaviour are tested.
- Tenant isolation is verified where relevant.
- Persistent state is verified.
- Duplicate and retry behaviour is tested.
- Relevant Android/browser/device tests are run.
- Results and limitations are documented.

A successful compilation is not proof of live payment, purchasing, delivery, or recovery.

## 14. Implementation sequence

1. Record and reconcile the full ecosystem specification.
2. Audit current GitHub implementation and CI.
3. Obtain the actual BingwaPulse Supabase project reference and perform a read-only backend audit.
4. Map existing schema to required tenant, identity, order, payment, execution, delivery, device, incident, and audit entities.
5. Implement backend identity, tenant isolation, and authorization.
6. Implement shared API/data contracts and synchronization.
7. Complete Android order/payment state handling and safe execution queue.
8. Implement ConnectPulse pairing, health monitoring, and safe recovery.
9. Build the responsive management website.
10. Build the SiteLink customer storefront.
11. Complete end-to-end tests and release checks.

## 15. Immediate blockers and unresolved decisions

- Correct BingwaPulse Supabase Project Reference ID is still required. Do not substitute CREOVA.
- Exact authorized provider purchasing method is not yet confirmed.
- Exact Till-funds-to-purchase settlement mechanism is unresolved.
- Signature Offers source access and refresh method must be confirmed.
- Secondary recovery communication path for a powered-off primary phone is unresolved.
- Any commission, wallet settlement, or pricing formula must remain configurable until confirmed.
- Live messaging and live fulfilment require separate approval.

## 16. Working agreement

Continue the existing repository and branch. Do not restart the project. Make small, reviewable changes, preserve main, and verify each implementation stage. Build the Android apps, management website, SiteLink storefront, and shared backend as one coordinated ecosystem.