# BingwaPulse SiteLink

## Components
- Management interface: web/index.html
- Customer storefront: web/sitelink/index.html
- Public backend endpoint: https://tuuhucisljlaaovtnbeu.supabase.co/functions/v1/sitelink-storefront
- Edge Function source: supabase/functions/sitelink-storefront/index.ts

## Management workflow
1. Sign in to the BingwaPulse Management Website.
2. Open Offers. Add only verified offer details, then activate an offer when it is ready to be sold.
3. Open SiteLink and create a store for the signed-in account. It starts paused.
4. Add an active offer to the store and set its customer selling price.
5. Publish the offer, then activate the store.
6. Copy the generated store URL and share it with customers.

A store displays only offers assigned to that agent, marked published, and still active within their validity window. Pausing the SiteLink hides the catalogue.

## Order safety
- The public Edge Function is intentionally callable without a customer login.
- It uses a server-side Supabase key; no service-role key is shipped to the browser.
- It validates the active store, active membership, assigned/published offer, offer validity and Kenyan phone numbers.
- Order requests are idempotent and are recorded with channel sitelink and status AWAITING_PAYMENT.
- It does not create a payment, mark a payment verified, enqueue a worker dispatch, call USSD, or claim bundle delivery.
- Payment confirmation and fulfilment remain separate workflows and require their own authorised integrations.

## Hosting
The GitHub Pages workflow publishes the web/ directory from feature/vecta-branding. Before the first deployment, enable Pages for the repository in Settings > Pages > Build and deployment > GitHub Actions. The connected GitHub Actions token cannot enable Pages on the repository itself.
