# Saumya Solar Energy Marketplace SaaS

## Product Requirements — Version 2.0

| Attribute | Value |
|---|---|
| Product | Multi-tenant solar vendor marketplace and SaaS ERP |
| Document | Business and Product Requirements |
| Version | 2.0 |
| Status | Approved architecture baseline for Commercial MVP planning |
| Effective branch | docs/marketplace-saas-v2.0 |
| Backend | Java 21, Spring Boot 3.5, Maven |
| Frontend | React 19, TypeScript, Vite |
| Database | MySQL 8.4, shared database and shared schema |
| Architecture | Modular monolith |
| Marketplace domain | One common platform domain; vendor path routing |

## 1. Product vision and mandatory decisions

The product is one SaaS-operated marketplace that enables independent solar vendors to manage their own storefronts and business operations. Customers use one global account to discover vendors and transact with each vendor separately.

The following decisions are mandatory and supersede conflicting wording in earlier document versions:

1. The Platform Super Admin operates the SaaS platform; it is not a commercial vendor and owns no commercial catalogue, products, inventory, customer orders or solar services.
2. If Saumya Solar sells products or services, Saumya Solar must be onboarded as an ordinary vendor tenant and must use the same commercial features, APIs, database ownership rules, permissions and subscription policy as other vendors.
3. The database strategy is one shared MySQL 8.4 database and one shared schema. Every tenant-owned record carries a non-null tenant_id and is protected by application authorization plus database constraints.
4. Every customer has exactly one global customer identity across the marketplace. Vendor-specific customer relationships and CRM data remain tenant-scoped.
5. Vendor A can access only Vendor A's customer relationship, CRM notes, pricing discussions, products, inventory, orders, invoices, payments, returns, shipments and service history. Vendor A cannot access Vendor B's equivalent data.
6. The customer portal may show the customer's orders from multiple vendors in one account. Every order is clearly labelled with its vendor and is accessible only to that customer.
7. A cart contains products from exactly one vendor. A checkout creates exactly one order for exactly one vendor.
8. Customer purchase payments go directly from the customer to the selected vendor's merchant/payment account. The platform does not collect, hold, pool, split or pay out customer purchase funds.
9. Vendor subscription payments are separate from customer purchase payments. Subscription invoices and receipts are platform receivables and must never be mixed with vendor order invoices or payments.
10. All vendors use one common platform domain. Vendor storefronts use paths, not vendor subdomains or custom domains.
11. Payment and messaging integrations use replaceable adapters. Mock adapters are enabled only through explicit non-production feature flags; they must never silently simulate successful production payments or messages.
12. The initial application is a modular monolith with the technology baseline in §16.

## 2. Business model and actors

### 2.1 Platform Super Admin

The Platform Super Admin manages platform operations only:

- Create, review, activate, suspend, reactivate and close vendor tenants.
- Create the first Vendor Admin identity and tenant membership during onboarding.
- Manage subscription plans, prices, billing periods, entitlements and subscription lifecycle.
- View platform-level subscription metrics, tenant status, platform health and audit records.
- Manage global technical configuration, payment-provider configuration, email templates and supported integration adapters.
- Provide controlled support access with purpose, time limit and audit trail.

The Platform Super Admin must not create or publish a commercial product, solar package, vendor price, stock record, customer quotation, customer order or vendor service request as a platform-owned business. Global configuration templates, if introduced, are non-saleable references that a vendor may copy into its own tenant catalogue.

### 2.2 Vendor / Tenant Admin

Each vendor tenant owns and manages its commercial business:

- Store profile, catalogue, categories, products, packages and prices.
- Inventory, branches and warehouses where enabled by plan.
- Leads, customers, vendor-specific customer relationships and private CRM notes.
- Quotations, orders, invoices, payments, shipping, returns and refunds.
- Site surveys, installations, warranty and service requests.
- Employees, assignments, custom roles and tenant-level permissions.
- Vendor analytics, exports, audit history and payment reconciliation reports.
- Its own gateway merchant account onboarding and status, using supported provider capabilities.

### 2.3 Vendor employees

Vendor Admin can invite employees and assign permission-limited roles, such as Sales Manager, Sales Executive, Inventory Manager, Operations Manager, Accountant, Service Manager, Service Engineer and Content Manager. Custom roles may contain only permissions granted to that tenant. Platform permissions can never be granted by a vendor.

### 2.4 Global customer

A customer registers once and uses the same global identity across all vendors. The customer can:

- Browse the marketplace and vendor storefronts.
- Select a vendor and view that vendor's published catalogue and prices.
- Maintain a single account and common authentication credentials.
- Add products from one vendor to a cart and complete that vendor's checkout.
- View their own orders across vendors, clearly grouped and labelled by vendor.
- View/download the invoice and receipt for each order.
- Track shipment, installation, return, refund, warranty and service activity for their own transactions.
- Manage account-level profile, authentication and consent preferences.

A customer's global account does not make any vendor's private CRM data globally visible.

## 3. Marketplace experience and common domain

### 3.1 Domain and storefront routing

All customers and vendors use one common platform domain, for example:

- Marketplace: https://marketplace.example.com/
- Vendor directory: https://marketplace.example.com/vendors
- Vendor A storefront: https://marketplace.example.com/vendors/vendor-a
- Vendor A products: https://marketplace.example.com/vendors/vendor-a/products
- Vendor B storefront: https://marketplace.example.com/vendors/vendor-b

Do not create per-vendor subdomains or custom vendor domains for the Commercial MVP.

For public requests, the server resolves the vendor slug from the route to an active tenant. The slug is a discovery/routing hint, not proof of authorization. Authenticated vendor APIs derive active tenant context from the authenticated identity and a server-validated tenant membership. A client-supplied tenant_id, query parameter, body field, header or URL slug must never override the authenticated tenant authorization context.

### 3.2 Marketplace browsing

- The marketplace lists only active vendors and their published products/packages.
- Vendor identity, display name, policies, fulfilment information and published contact details are visible on each vendor storefront.
- Catalogue search and filters must not leak unpublished or private vendor data.
- Product detail and price are owned by the vendor tenant.
- Vendor selection is explicit before adding an item to a cart.

## 4. Platform administration and SaaS subscriptions

### 4.1 Vendor lifecycle

Vendor lifecycle states: PENDING, UNDER_REVIEW, ACTIVE, SUSPENDED, CLOSED.

The platform must capture legal/business profile, vendor contacts, GST/tax identifiers where applicable, store slug, onboarding state, initial Vendor Admin, terms acceptance and audit history. Closing a vendor is a controlled lifecycle action, not a cascading hard delete of financial or audit records.

### 4.2 Subscription plans

The platform offers configurable monthly, quarterly and yearly plans. A plan can define:

- Price and currency.
- Billing interval and billing anchor rules.
- Included users, storage, branches and warehouses.
- Feature entitlements and usage limits.
- Trial eligibility, grace period and suspension policy.
- Tax configuration and invoice description.

Plan price/terms are versioned or snapshotted on each subscription/invoice so historical invoices do not change when a plan is edited.

### 4.3 Subscription billing

- Each tenant has a subscription record and billing history.
- Subscription invoices and payment transactions are separate from customer purchase invoices and payment records.
- Renewal obligations and payment attempts are idempotent.
- Failed renewal enters PAST_DUE/GRACE_PERIOD according to the configured policy.
- The default policy allows access during the configured grace period, with reminders. After grace expiry, vendor write access is suspended; read-only access and storefront behavior are configurable and must be explicit.
- Successful payment can reactivate an eligible subscription.
- Vendor lifecycle suspension by a platform administrator is independent of subscription delinquency.
- Suspension never deletes vendor data, orders, invoices or audit history.

## 5. Global customer identity and vendor-specific relationships

The identity model contains one global authentication identity and one global customer profile per customer. A customer can have relationships with many vendor tenants.

For each relationship, the platform stores a tenant-owned record that can include vendor customer number, relationship status, first/last transaction dates, assigned sales employee, consent/provenance and private CRM notes.

A vendor may read and update only its own tenant_customer record and associated private notes. Vendor A must not see Vendor B's notes, lead scores, negotiated prices, pricing discussions, contact history, support history or service history.

The customer portal uses the global customer identity to list only that customer's transactions across all vendors. Vendor-specific transaction details are labelled with the vendor name and remain independently owned by the corresponding tenant.

## 6. Single-vendor cart and checkout requirements

- A cart belongs to one global customer and exactly one tenant.
- The first valid product added to an empty cart establishes cart.tenant_id.
- Every cart item must reference an active, published product owned by that same tenant.
- Adding a product from another vendor is rejected by the backend. The UI explains that the cart belongs to the current vendor and offers a deliberate clear-and-switch action.
- A cart switch must not silently discard the original cart. The UI must confirm the action; the original cart may be retained as an inactive/draft cart or explicitly cleared.
- Checkout revalidates tenant ownership, price snapshot, tax, discount, inventory, shipping/fulfilment eligibility and vendor payment-account readiness.
- One successful checkout produces exactly one order and one vendor tenant_id.
- Order items, invoice, payment, shipment, return and refund must carry the same tenant ownership as the order.
- Idempotency keys and concurrency controls prevent duplicate orders, payment intents and inventory reservations.
- Vendor catalogue price changes after order creation do not alter the order's stored line-price/tax snapshots.

## 7. Direct-to-vendor customer payments

The customer purchase payment model is DIRECT_VENDOR_PAYMENT:

1. Each vendor onboards and verifies a merchant/payment account supported by the selected gateway.
2. Checkout creates a payment request in the context of that vendor's merchant account.
3. The customer pays the vendor directly through the gateway's hosted or tokenized payment experience.
4. The gateway settles customer purchase funds to the vendor's merchant account under the gateway's merchant agreement and settlement schedule.
5. The platform receives verified payment status/reference events and updates the vendor-owned order/payment records.
6. Refunds, where allowed, are initiated against the original vendor merchant account and original transaction.
7. The platform does not receive customer purchase funds, operate a pooled balance, calculate vendor payouts or run a vendor payout ledger.

The payment adapter must preserve the selected vendor merchant-account identity for create, verify, refund and reconciliation operations. The production gateway must explicitly support the intended vendor-direct merchant arrangement in the target market. Stripe/Razorpay are future adapter candidates, not a guarantee that a particular account/route structure is available; commercial, provider and regulatory approval is a production gate.

The platform's subscription billing uses a separate platform-owned merchant account, credentials, invoices, payment records and reconciliation flow. Never reuse customer order payment records for subscription collection.

## 8. Vendor business lifecycle

The Commercial MVP supports the end-to-end vendor workflow:

Enquiry → Lead → Site Survey → Quotation → Customer Acceptance → Order → Direct Vendor Payment → Inventory Reservation → Shipment/Delivery or Installation → Invoice/Receipt → Warranty/Service → Return/Refund where eligible.

Each workflow step is tenant-owned, permission-controlled, validated, auditable and idempotent where retries are possible.

## 9. Functional scope

### 9.1 Commercial MVP
- Vendor onboarding and tenant lifecycle.
- Global authentication and customer identity.
- Tenant membership, RBAC and custom vendor roles.
- Vendor storefront and marketplace directory on the shared domain.
- Vendor-owned categories, products, solar packages and pricing.
- Customer/vendor relationship and CRM.
- Enquiries, leads, site surveys and quotations.
- Single-vendor cart and checkout.
- Orders, stock availability/reservation and order history.
- Vendor-direct payment using a flag-controlled mock adapter initially.
- Vendor invoices/receipts and customer portal.
- Subscription plans, subscription invoices and subscription payment records.
- Email notifications using Spring Mail; local delivery via Mailpit.
- Mock SMS/WhatsApp adapters behind explicit feature flags.
- Platform/vendor dashboards and audit trail.
- Docker Compose local stack, migrations, tests and operational metrics.

### 9.2 Operations capabilities
- Branches and warehouses.
- Transfers, stock movements and serialized inventory.
- Shipping/delivery lifecycle and fulfilment evidence.
- Installation planning, assignment, checklist and commissioning.
- Warranty and service-ticket SLA workflows.
- Customer returns, credit notes and vendor-account refunds.
- Advanced analytics, exports and audit investigation.

These operational modules are in the domain design; the implementation rollout must preserve a deployable vertical slice and must not claim a feature is complete until its APIs, UI, persistence, authorization and tests are integrated.

## 10. Vendor dashboard and customer portal boundaries

Vendor dashboard metrics are computed only from that tenant's relationships and transactions: its customers, leads, quotations, orders, order revenue, payments, inventory, installations, returns and service records. Platform-wide data is never included in a vendor report.

The customer portal can show all orders belonging to the authenticated global customer across vendors. Each order must display vendor identity, order number, status, payment state, invoice and fulfilment status. A customer cannot access another customer's order by changing an identifier.

## 11. Security, isolation and audit requirements

- Deny by default; enforce authorization in backend services and data access.
- Tenant context comes from authenticated principal plus server-side membership validation.
- Every tenant-owned table has tenant_id NOT NULL except explicitly global identity/configuration tables.
- Cross-tenant references are prevented with composite foreign keys where feasible.
- MySQL shared-schema design is not native row-level security. Enforce tenant predicates in all data access and use DB constraints as a second boundary.
- Search, counts, reports, exports, object storage, caches, asynchronous jobs and audit queries are tenant-scoped.
- Global customer identity is not a tenant ownership shortcut.
- Platform support access is time-limited, purpose-bound and auditable.
- Audit critical identity, role, tenant, catalogue price, order, stock, invoice, payment, refund and subscription changes.
- Access/refresh credentials are rotated; refresh tokens use revocation/reuse detection. Browser tokens must not be stored in localStorage; use secure HttpOnly cookie handling and CSRF protection where cookie authentication is used.
- Validate uploads, file signatures, size, malware scan state, tenant quotas and signed download ownership.

## 12. Core domain entities

Global/platform entities: users, customer_profiles, tenants, subscription_plans, subscription_plan_prices, platform_configuration, platform_audit_events.

Tenant relationship entities: tenant_users, tenant_customers, tenant_customer_notes, vendor_profiles, vendor_payment_accounts.

Tenant commercial entities: categories, products, solar_packages, package_items, branches, warehouses, inventory_balances, inventory_transactions, leads, enquiries, site_surveys, quotations, quotation_items, carts, cart_items, orders, order_items, order_status_history, invoices, payments, payment_attempts, refunds, shipments, shipment_events, returns, return_items, installations, warranties, service_requests, service_visits, vendor_notifications, vendor_audit_events.

Subscription billing entities: tenant_subscriptions, subscription_invoices, subscription_payments, subscription_payment_attempts, subscription_events, subscription_usage.

Every transactional child record must preserve tenant ownership through tenant_id and relationship constraints.

## 13. Acceptance criteria

1. The Platform Super Admin cannot create or publish platform-owned commercial products or receive orders as a platform tenant.
2. Saumya Solar can sell only after being onboarded as an ordinary tenant with the same tenant rules as any other vendor.
3. A Vendor A employee cannot read, write, search, export or infer Vendor B's products, customer relationships, CRM notes, negotiated prices, leads, orders, inventory, invoices, payments, returns, shipments or service history.
4. A customer has one global account and can view their own orders from Vendor A and Vendor B in one portal.
5. Vendor A sees only Vendor A's customer relationships and transactions.
6. All vendor storefronts use the common domain and path-based routing; no vendor subdomain is required.
7. Mixed-vendor cart item insertion is rejected by the API; explicit cart switching is required.
8. Each checkout creates one order with one tenant_id and only same-tenant order items.
9. Customer purchase funds settle directly to the selected vendor's verified merchant account; the platform has no customer-fund custody/payout workflow.
10. Vendor subscription collection is separate from customer order payment collection, with separate merchant configuration and records.
11. Subscription renewal failure, grace period, suspension and reactivation are tested.
12. Returns/refunds are restricted to the original vendor order and payment.
13. All tenant isolation, checkout and payment tests run against MySQL 8.4 through the normal CI pipeline.

## 14. Release governance

No requirement is complete solely because a menu, screen, controller, DTO, entity or adapter exists. Completion requires end-to-end traceability from UI action through API, validation, authorization, service logic, persistence, external adapter behavior and automated tests.

The Product Owner must approve provider capabilities, tax/invoice rules, subscription grace policy, refund policy, vendor onboarding/KYC and legal/privacy text before production. This document is the Version 2.0 product baseline; implementation scope must be tracked separately in release plans and acceptance evidence.
