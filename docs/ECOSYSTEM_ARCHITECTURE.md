# BingwaPulse Ecosystem Architecture

## Product boundaries

This repository is the coordinated development home for the BingwaPulse ecosystem.

### 1. BingwaPulse Android (main app)
The primary business-management app. It owns the operator experience for:
- agents and agent permissions
- customers
- provider and offer catalogue
- agent-specific selling prices
- orders and transaction records
- reporting and audit history
- SiteLink configuration and publishing controls

#### Dashboard interface baseline
Preserve these dashboard actions and navigation as the current product baseline:
- Quick actions: **New order**, **Add customer**, **Manage offers**, **Transactions**
- Dashboard tabs: **Overview**, **Transactions**, **Analytics**
- Bottom navigation: **Home**, **Transactions**, **Wallet**, **Profile**

These are distinct navigation layers: dashboard tabs organize the dashboard content; bottom navigation switches the app's main areas.

#### Current operational truth
The current BingwaPulse dashboard stores and displays local records using an offline/local-only data setup. It does **not** currently:
- verify M-Pesa payments with Safaricom or another trusted payment source
- submit USSD purchases automatically
- automatically deliver bundles to customers
- synchronize business records through a production cloud backend

Any payment, order, or transaction shown by the current local app must be labelled according to its actual local status. A local record or notification is not proof of verified payment or successful bundle fulfilment.

### 2. BingwaPulse Website (management web)
The browser-based management interface for the same business platform and account data as BingwaPulse Android. It is not the public customer storefront.

### 3. Bingwa SiteLink (feature of BingwaPulse)
SiteLink is a feature managed from BingwaPulse, not a separate app or independent product.
- Agents configure their storefront and choose which eligible offers to publish.
- Customers open a public web link without needing the management app.
- Customers can browse offers and submit orders.
- SiteLink must use the same authoritative offer, agent, order and transaction records as BingwaPulse.
- Public storefront access must not expose private customer, agent or business-management data.

### 4. Connect Pulse Android (companion app)
The authorized-device companion. Its responsibility is to pair and manage explicitly authorized Android phones/SIM profiles and report permitted device status/events to BingwaPulse. It must not silently execute USSD actions, send messages, or initiate purchases. Any future sensitive action must have explicit authorization, visible status and an audit trail.

## Shared platform contract

All clients should converge on one backend and one identity/permission model. Do not create separate competing copies of business data.

Core records:
- Account / business
- User and agent membership with role and permissions
- Authorized device and SIM profile
- Provider
- Offer definition (provider identity, category, bundle details, source/reference price)
- Agent offer (agent-specific selling price and publication state)
- SiteLink configuration and public slug
- Customer
- Order
- Payment/transaction evidence
- Device event and audit log

Important invariants:
- Selling prices belong to an agent's offer, not to the provider's source offer.
- The same agent cannot have two active offers with the same selling price, unless a later explicit rule changes this.
- SiteLink displays only offers that the agent has deliberately published.
- Orders created through SiteLink must appear in the same order-management view in BingwaPulse.
- Payment evidence must not be treated as verified payment until a trusted verification process is implemented.
- A device pairing code is short-lived and single-use; pairing must be explicitly approved by an authorized account.
- Provider integrations remain adapter-based. Do not assume private APIs or undocumented USSD access.

## Coordinated development sequence

Develop BingwaPulse Android and Connect Pulse Android in parallel, keeping their interfaces aligned with the shared platform contract. Develop the management website and the public SiteLink web experience as two distinct web surfaces over the same backend.

1. Stabilize the shared domain model, account identity and role permissions.
2. Build the authorized-device pairing handshake between BingwaPulse and Connect Pulse.
3. Connect offers, agent-specific prices and SiteLink publication state.
4. Connect SiteLink orders to the central order queue.
5. Add transaction evidence and verification only after a safe, testable verification design exists.
6. Add provider-specific fulfilment adapters only after access and provider rules are verified.

## Safety and release rules

- Keep development on the feature branch; do not modify or merge into `main` without explicit approval.
- Keep sandbox/test integrations separate from production.
- Do not enable live payments, paid services, live messaging, automatic USSD submission or bundle fulfilment without explicit approval.
- Label unfinished or local-only features honestly. A screen or local database record is not proof of cloud synchronization or a working provider integration.
- Test both Android app build targets together and report the actual CI result before presenting APKs as ready.
