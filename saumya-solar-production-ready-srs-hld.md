# Saumya Solar Energy Marketplace SaaS

## Software Requirements Specification and High-Level Design — Version 2.0

| Attribute | Value |
|---|---|
| Product | Multi-tenant solar vendor marketplace and SaaS ERP |
| Document type | SRS and HLD |
| Version | 2.0 |
| Status | Architecture baseline for Commercial MVP |
| Backend | Java 21, Spring Boot 3.5, Maven |
| Frontend | React 19, TypeScript, Vite |
| Database | MySQL 8.4, one shared database and shared schema |
| Migrations | Flyway |
| Architecture | Modular monolith |
| API | REST, OpenAPI 3, RFC 9457 Problem Details |
| Local deployment | Docker Compose |

## 1. Purpose and authority

This document defines the functional architecture, non-functional requirements, data ownership, security model, APIs, workflows, integrations, acceptance criteria and technology decisions for a multi-vendor solar marketplace SaaS.

This Version 2.0 supersedes conflicting statements in Version 1.0. The companion file saumya-solar-energy-solution-requirements.md is the business requirements document. These two documents must be changed together if a foundational product decision changes.

## 2. Product vision and binding decisions

One SaaS platform → many independent vendor tenants → vendor employees and customer relationships → global customers who transact separately with each vendor.

Binding decisions:

- Platform Super Admin is the SaaS operator and does not own commercial products, catalogue, inventory, customers, orders or services.
- Saumya Solar must be onboarded as an ordinary tenant to sell; no implicit platform catalogue or platform vendor.
- One MySQL 8.4 database and one shared schema with tenant_id on every tenant-owned record.
- Application-level tenant isolation is mandatory; composite database constraints provide a second line of defense. MySQL does not provide PostgreSQL-style native row-level security policies.
- One global customer identity and one global customer profile per customer. Tenant-specific relationships and private CRM data are separate tenant-owned records.
- Vendor users see only their own tenant's relationships and transactions. A customer can see only their own transactions from multiple vendors.
- One shared platform domain with path-based vendor storefronts. No per-vendor subdomains or custom domains in the MVP.
- A cart contains exactly one vendor's items. A checkout creates one vendor-owned order.
- Customer purchase funds go directly to the selected vendor's merchant account. The platform does not collect or pay out customer purchase money.
- Vendor SaaS subscription charges are separate from customer purchase payments and are collected for the platform's account.
- Mock payment and SMS/WhatsApp adapters are feature-flagged for non-production only. Production must fail startup or deployment validation if a mock provider is selected.
- Modular monolith; do not split into microservices for the Commercial MVP.

## 3. Scope and exclusions

### 3.1 Commercial MVP scope

- Platform and vendor identity, vendor lifecycle and tenant membership.
- Global customer identity and global customer portal.
- Vendor directory and storefronts on one common domain.
- Vendor catalogue, product, solar-package and pricing management.
- CRM, enquiry, leads, customer relationships, private notes and quotations.
- Single-vendor cart, checkout and order management.
- Inventory availability/reservation sufficient for checkout.
- Vendor-direct customer payment through a mock adapter in local/test environments, behind a feature flag.
- Vendor invoice and payment records.
- Platform subscription plans, tenant subscriptions, subscription invoices and separate subscription payment records.
- Vendor Admin, vendor employee RBAC, platform administration and audit.
- Email through Spring Mail (Mailpit locally).
- Mock SMS/WhatsApp adapters behind explicit flags.
- MinIO local object storage behind S3-compatible storage interface.
- Redis for cache and distributed rate limiting.
- Docker Compose local deployment, migrations, tests and observability.

### 3.2 Operational modules

The domain model and interfaces anticipate branches, multiple warehouses, shipping, installation, warranty, service tickets, returns, credit notes, refunds and advanced reports. Each is released only when complete acceptance criteria and integrated tests exist.

### 3.3 Out of scope

- Platform-owned commercial storefront or catalogue.
- Platform custody, pooling, splitting or payout of customer purchase funds.
- Vendor subdomains and custom domains.
- Multi-vendor cart or one order containing multiple vendors.
- Native mobile applications and microservice decomposition.
- A production payment gateway integration until provider and merchant-account approval is complete.
- Real SMS/WhatsApp provider delivery until approved provider adapter is implemented and enabled.

## 4. Stakeholders and trust boundaries

| Actor | Scope | Permitted responsibility |
|---|---|---|
| Platform Super Admin | Platform | Tenant lifecycle, subscriptions, platform configuration, platform analytics/audit |
| Platform Support | Platform, restricted | Time-limited support access with purpose and audit |
| Vendor Admin | One authorized tenant | Vendor operations, employees, roles, catalogue, CRM and finance |
| Vendor Employee | One authorized tenant | Permission- and assignment-scoped work |
| Global Customer | Own identity | Marketplace browsing and own cross-vendor transactions |
| Payment provider | Vendor merchant context or platform subscription context | External payment execution and verified status events |
| Messaging provider | Tenant/template context | Delivery of approved messages |

The platform is a service provider, not a vendor. Platform administrators must not inherit vendor permissions. A platform administrator who is also a vendor user must use a separate vendor tenant membership and commercial context.

## 5. Architecture principles

1. Deny by default and enforce permissions in backend services.
2. Resolve tenant scope from trusted authenticated identity plus server-side membership, never an untrusted tenant_id.
3. Separate global identity from tenant membership and tenant customer relationships.
4. Keep global identity data minimal; do not expose global customer history to vendors.
5. Make tenant ownership explicit in entities, keys, queries, events, cache keys, files and exports.
6. Keep customer purchase payments separate from platform subscription billing.
7. Use provider adapters and fail closed when payment provider configuration is incomplete.
8. Use transactional boundaries, idempotency and outbox/inbox patterns for external side effects.
9. Treat platform audit and vendor audit as different access scopes.
10. Maintain a modular monolith with module-owned domain models and application services.

## 6. High-level logical architecture

~~~mermaid
flowchart TB
  C[Global Customer] --> WEB[React 19 Marketplace<br/>Single Shared Domain]
  VA[Vendor Admin / Employee] --> WEB
  PA[Platform Super Admin] --> WEB
  WEB --> API[Spring Boot 3.5 REST API]
  API --> IAM[Identity and Authorization]
  API --> TEN[Tenant Context and Membership]
  API --> MOD[Modular Monolith Modules]
  MOD --> DB[(MySQL 8.4<br/>Shared Schema + tenant_id)]
  MOD --> REDIS[(Redis)]
  MOD --> OBJ[Object Storage Port]
  OBJ --> MINIO[MinIO Local / S3 Compatible]
  MOD --> MAIL[Spring Mail]
  MAIL --> MP[Mailpit Local]
  MOD --> MSG[Messaging Adapter]
  MOD --> PAY[Vendor Payment Adapter]
  MOD --> SUBPAY[Platform Subscription Payment Adapter]
  MSG --> MOCKMSG[Flagged Mock SMS/WhatsApp]
  PAY --> MOCKPAY[Flagged Mock Payment]
  PAY --> VG[Vendor Merchant Account]
  SUBPAY --> PLATFORM[Platform Merchant Account]
  MOD --> OBS[Actuator / Micrometer]
  OBS --> PROM[Prometheus]
~~~

### 6.1 Deployment topology

- React static assets served through the common platform domain.
- API path under the same site/origin where feasible; TLS terminated at reverse proxy/load balancer.
- Two or more stateless API instances in production when infrastructure permits.
- Managed MySQL 8.4 with automated backup, point-in-time recovery, encryption and tested restore.
- Redis for distributed rate limits, cache and coordination; never the source of truth for tenant authorization.
- MinIO for local development; S3-compatible storage adapter for production.
- Mailpit for local email capture; production email credentials via secret management.
- Prometheus scrapes Actuator/Micrometer metrics.
- Docker Compose provisions local API, web, MySQL, Redis, MinIO, Mailpit and Prometheus services.
- Production secrets are injected through managed secret storage, never committed to Git.

### 6.2 Modular monolith modules

Suggested backend modules:

- identity
- platform
- tenant
- membership
- subscription
- customer
- vendor-customer
- catalog
- pricing
- cart
- checkout
- order
- inventory
- quotation
- crm
- invoice
- vendor-payment
- subscription-payment
- shipment
- returns
- installation
- warranty
- service
- notification
- document
- reporting
- audit
- integration

Each module owns its persistence and application-service boundary. Cross-module interaction uses public application services or domain events; direct repository coupling is prohibited except for an explicitly documented shared-kernel dependency.

## 7. Shared domain, domain routing and tenant resolution

### 7.1 One common domain

Example route design:

- / — marketplace landing page
- /vendors — vendor directory
- /vendors/{vendorSlug} — storefront
- /vendors/{vendorSlug}/products — public vendor catalogue
- /vendors/{vendorSlug}/products/{productSlug} — product detail
- /customer/* — global customer portal
- /vendor/* — authenticated vendor workspace
- /platform/* — authenticated platform administration
- /api/v1/* — REST APIs

No vendor subdomains or custom vendor domains are supported in this MVP.

### 7.2 Authenticated tenant resolution

1. Validate the JWT access token and token/session state.
2. Resolve the global user identity.
3. For vendor operations, resolve the active tenant membership server-side and verify its status.
4. Build an immutable request-scoped tenant context containing tenant ID, user ID, membership ID, roles and permissions.
5. Require tenant context for every tenant-owned read/write.
6. Verify target resource ownership before returning or mutating it.
7. Clear request/thread context reliably after request completion.
8. Never accept tenant_id from a request body, query parameter, header or path as an authorization source.

If an employee belongs to more than one tenant, the server may issue a tenant-scoped access context only after validating a membership selection against persisted memberships. Customers do not select an active vendor tenant for their global portal; customer access is scoped by customer identity plus each order's tenant-owned resource policy.

For public storefront reads, resolve vendorSlug to an ACTIVE tenant. This resolution allows only published public data; it does not grant vendor administration or access to private records.

### 7.3 Tenant lifecycle gate

A vendor operation is allowed only when:
- Tenant lifecycle state permits the action.
- Subscription status and entitlement policy permits the action.
- Membership is active.
- The user has the required permission.
- Resource ownership matches the resolved tenant.

Platform security suspension and subscription delinquency are separate reasons and must be separately auditable.

## 8. Tenant isolation strategy

### 8.1 Selected database strategy

Use Option C: one shared database, one shared schema, tenant_id on tenant-owned rows.

This is the selected Commercial MVP architecture because it suits a modular monolith, reduces operational complexity and centralizes migrations. It is not native row-level security. MySQL 8.4 does not provide PostgreSQL-style row-level security policies; isolation is enforced through application authorization, tenant-scoped queries and database integrity constraints.

### 8.2 Tenant data rules

- Every tenant-owned table has tenant_id BIGINT NOT NULL.
- Global identities and genuinely platform-global configuration do not have tenant_id.
- Platform subscription plan definitions are global; tenant subscriptions and subscription invoices are associated with a tenant.
- Customer profile and authentication identity are global; tenant_customers and tenant_customer_notes are tenant-owned.
- Products, categories, packages, pricing, inventory, carts, orders, invoices, payments, shipment, returns, refunds, CRM, service, vendor audit and vendor employees/memberships are tenant-owned.
- Tenant_id is immutable on a persisted business record. Moving data between tenants is a controlled migration with explicit authorization, audit and reconciliation, not an ordinary update.
- Every child table that references a tenant-owned parent includes tenant_id and uses composite foreign keys where feasible.
- Unique business identifiers are tenant-scoped unless a global uniqueness requirement is explicitly defined.

### 8.3 Defense in depth

- Mandatory tenant argument in tenant repository/application-service methods.
- Tenant-scoped specifications and query helpers; prohibit unscoped tenant repository access.
- Composite foreign keys for order→order item→product, cart→cart item→product, invoice/payment/return→order and tenant_customer relationships.
- Database indexes start with tenant_id for common tenant-scoped access paths.
- Bulk update/delete, native SQL, JPQL, exports and report queries require tenant predicate.
- Redis cache keys include tenant_id and resource identity; global customer cache entries must not contain vendor-private CRM data.
- Object storage key includes tenant_id; signed downloads re-authorize ownership before issuing a URL.
- Queue messages and scheduled jobs include validated tenant context and re-check tenant state.
- Vendor search, counts, facets, analytics and error responses must not reveal cross-tenant data.
- Platform support access uses an explicit audited privileged path, never a hidden tenant filter bypass.

## 9. Tenant ownership and visibility matrix

Legend: G = global/platform-owned; T = tenant-owned; C = customer-owned global identity; T↔C = tenant relationship to a global customer.

| Data/domain | Ownership | Platform Super Admin | Vendor A | Vendor B | Global customer |
|---|---|---|---|---|---|
| Authentication user identity | G | Platform controls per policy | Own/membership details | Own/membership details | Own identity |
| Customer profile | C/G | Restricted support only | Minimum details needed for own relationship/orders | Same, own relationship only | Own profile |
| Tenant/vendor profile | T | Manage lifecycle | Own profile | Own profile | Public published vendor profile |
| Tenant membership/employee | T | Platform governance/restricted support | Vendor A users only | Vendor B users only | None |
| Vendor customer relationship | T↔C | Restricted support/audit | Vendor A relationship only | Vendor B relationship only | Own relationship-facing information |
| CRM notes/pricing discussions | T | Restricted, audited support only | Vendor A only | Vendor B only | Never |
| Product/category/package/price | T | No commercial ownership; support metadata only | Vendor A catalogue | Vendor B catalogue | Published vendor listing and displayed price only |
| Cart/cart items | T + C | No routine access | No access unless authorized support | No access unless authorized support | Own cart; one tenant |
| Order/order items/status | T + C | Aggregate/restricted audited support | Vendor A orders only | Vendor B orders only | Own orders across vendors |
| Vendor invoice/payment/refund | T + C | Aggregate/restricted audited support | Vendor A records only | Vendor B records only | Own transaction documents |
| Vendor merchant account | T | Provider configuration/support; restricted secrets | Own merchant account status | Own merchant account status | No |
| Inventory/warehouse | T | No commercial ownership | Vendor A only | Vendor B only | Public availability only if published |
| Shipment/return/installation/service/warranty | T + C | Restricted audited support | Vendor A only | Vendor B only | Own records |
| Vendor audit events | T | Authorized audit/support access | Vendor A events per permission | Vendor B events per permission | No |
| Subscription plan definitions | G | Manage | Read current plan | Read current plan | No |
| Tenant subscription/invoices/payments | T, platform-billed | Manage platform billing | Own subscription and bills | Own subscription and bills | No |
| Platform audit and health | G | Authorized platform roles | No | No | No |
| Global reference/configuration | G | Manage | Read only where exposed | Read only where exposed | Public only |

### 9.1 Customer privacy boundary

A vendor may see only customer data needed to fulfil that vendor's own enquiry, quotation, order, payment, delivery, installation, return, warranty or service. A vendor may not query another vendor's customer relationship, notes, purchase history, support interactions, negotiated price or service record. Cross-vendor identity matching must not disclose that a customer has a relationship with another vendor.

### 9.2 Platform support boundary

Platform Super Admin may view platform-level operational metrics and audit records. Any access to tenant-private content for support must require an explicit permission, reason, ticket/reference, time limit and immutable audit entry. Platform analytics should be aggregated by default.

## 10. Global identity and customer relationship design

### 10.1 Identity model

- users is the global authentication identity table. Normalize email and phone and enforce the agreed uniqueness/verification rules.
- customer_profiles is one-to-one with a customer user identity. One person has exactly one global customer identity.
- tenant_users links a global user to one or more vendor tenants as an employee, with tenant-specific roles and membership status.
- tenant_customers links a global customer profile to a vendor tenant. Unique constraint: (tenant_id, customer_id).
- tenant_customer_notes contains private vendor-only CRM notes under the relationship and tenant.
- Customer authentication is global. Tenant authorization is not granted merely because a global customer identity exists.

### 10.2 Global customer experience

The customer portal queries orders by the authenticated customer ID, not by a customer-supplied customer_id. Each order carries tenant_id and is rendered with the vendor's public display name, vendor order number, status and permitted documents. Every detail request verifies both customer ownership and order tenant association.

Vendor-facing APIs begin from the authenticated tenant context and query only that tenant's tenant_customers and associated transactions.

## 11. Roles and permissions

### 11.1 Standard roles

| Role | Scope | Main permissions |
|---|---|---|
| PLATFORM_SUPER_ADMIN | Platform | Tenant lifecycle, plan management, platform analytics/audit, platform configuration |
| PLATFORM_SUPPORT | Platform, restricted | Time-bound support with reason and audit |
| VENDOR_ADMIN | One tenant | Tenant-wide vendor administration |
| SALES_MANAGER | One tenant | Leads, quotations, assigned team and sales reports |
| SALES_EXECUTIVE | One tenant/assigned | Assigned leads, customer relationship, quotations and permitted orders |
| INVENTORY_MANAGER | One tenant | Products, stock and warehouses |
| OPERATIONS_MANAGER | One tenant | Surveys, shipment/installation fulfilment |
| ACCOUNTANT | One tenant | Vendor invoices, vendor payment status, refunds and finance reports |
| SERVICE_MANAGER | One tenant | Service requests, engineer assignment and SLA |
| SERVICE_ENGINEER | Assigned tenant resources | Assigned work only |
| CONTENT_MANAGER | One tenant | Vendor storefront content |
| CUSTOMER | Global identity, self | Own profile and own transactions across vendors |

### 11.2 Permission catalogue

Platform permissions: PLATFORM_TENANT_CREATE, PLATFORM_TENANT_UPDATE, PLATFORM_TENANT_SUSPEND, PLATFORM_TENANT_CLOSE, PLATFORM_PLAN_MANAGE, PLATFORM_SUBSCRIPTION_VIEW, PLATFORM_SUBSCRIPTION_ADJUST, PLATFORM_ANALYTICS_VIEW, PLATFORM_AUDIT_VIEW, PLATFORM_SUPPORT_ACCESS.

Vendor permissions: TENANT_PROFILE_VIEW/UPDATE, USER_VIEW/CREATE/UPDATE/DISABLE, ROLE_VIEW/CREATE/UPDATE/ASSIGN, CUSTOMER_RELATION_VIEW/CREATE/UPDATE, CRM_NOTE_VIEW/CREATE/UPDATE, PRODUCT_VIEW/CREATE/UPDATE/ARCHIVE, PRICE_VIEW/UPDATE, LEAD_VIEW/CREATE/UPDATE/ASSIGN, QUOTATION_VIEW/CREATE/UPDATE/SEND/APPROVE/CONVERT, CART_SUPPORT_VIEW only when explicitly authorized, ORDER_VIEW/CREATE/UPDATE/CANCEL, INVENTORY_VIEW/RECEIVE/RESERVE/TRANSFER/ADJUST, INVOICE_VIEW/CREATE/SEND/VOID, VENDOR_PAYMENT_VIEW/RECONCILE/REFUND, SHIPMENT_VIEW/UPDATE, RETURN_VIEW/APPROVE/RECEIVE, INSTALLATION_VIEW/ASSIGN/UPDATE/COMMISSION, SERVICE_VIEW/CREATE/ASSIGN/UPDATE/RESOLVE, REPORT_VIEW/EXPORT, VENDOR_AUDIT_VIEW.

A vendor role cannot contain PLATFORM_* permissions. Global customer identity does not grant employee permissions. Resource assignment restrictions are applied in addition to role permissions.

## 12. Functional requirements

### 12.1 Identity and session

- IAM-001: Register/login once with a global identity.
- IAM-002: Support email verification and optional mobile verification.
- IAM-003: Use Spring Security with short-lived JWT access tokens.
- IAM-004: Rotate refresh tokens, store only hashed refresh-token identifiers server-side, detect reuse and revoke token family.
- IAM-005: Browser refresh/session cookies use HttpOnly, Secure and appropriate SameSite attributes; protect cookie-authenticated state-changing requests with CSRF controls. Do not store long-lived bearer tokens in localStorage.
- IAM-006: Privileged platform users require MFA.
- IAM-007: Tenant membership must be validated for every vendor context.
- IAM-008: Password/session, membership, role and privilege changes are audited.

### 12.2 Platform and vendor lifecycle

- TEN-001: Platform Admin creates tenant and first Vendor Admin user/membership.
- TEN-002: Vendor profile includes legal/display name, slug, contact, address, tax identifiers and onboarding status.
- TEN-003: Tenant states are PENDING, UNDER_REVIEW, ACTIVE, SUSPENDED and CLOSED.
- TEN-004: Tenant close is a controlled, non-cascading operation; preserve financial and audit records.
- TEN-005: No platform commercial catalogue, product, stock, order, customer or service owner exists.
- TEN-006: Saumya Solar must be created as an ordinary tenant if it sells.
- TEN-007: Vendor slug is unique and used only for common-domain public routes.

### 12.3 Subscription plans and entitlements

- SUB-001: Platform Admin manages monthly, quarterly and yearly plans.
- SUB-002: Plan includes price, currency, interval, entitlement limits, feature set, tax metadata and grace policy.
- SUB-003: Plan price/entitlements are versioned; each subscription/invoice snapshots agreed terms.
- SUB-004: Tenant may have one effective subscription at a time, with full subscription history.
- SUB-005: Renewal creates an invoice/obligation idempotently and initiates subscription payment using platform merchant configuration.
- SUB-006: Subscription payment failures, retry schedule, notices, grace period and suspension are recorded.
- SUB-007: Entitlement enforcement is server-side.
- SUB-008: Subscription suspension is distinct from manual tenant suspension.
- SUB-009: Reactivation after successful payment is audited.
- SUB-010: Subscription invoices/payments never share tables or transaction IDs with customer purchase payments.
- SUB-011: Subscription cancellation, end-of-term expiry and refunds follow explicit policy and preserve history.

### 12.4 Global customer and vendor CRM

- CUS-001: Customer identity and authentication are global and unique.
- CUS-002: A global customer can transact with multiple vendors using one account.
- CUS-003: Create tenant_customers relationship when a customer submits an enquiry or starts a vendor relationship, as appropriate.
- CUS-004: Vendor CRM notes, lead score, sales assignment, pricing discussion and contact history are tenant-private.
- CUS-005: Vendor APIs cannot search or count customers outside their active tenant relationship.
- CUS-006: Customer portal returns only records owned by the authenticated global customer.
- CUS-007: A vendor sees only its relationship and transactions, not another tenant's private records.

### 12.5 Vendor catalogue and pricing

- CAT-001: Every category, product, package, price, image and publication state belongs to one tenant.
- CAT-002: Tenant SKU uniqueness is enforced by UNIQUE(tenant_id, sku).
- CAT-003: Public catalogue exposes only active/published tenant records.
- CAT-004: There is no platform-owned commercial product catalogue.
- CAT-005: Platform reference templates, if any, are non-saleable and must be explicitly copied into a tenant before publication.
- CAT-006: Price, tax and discount changes are audited.

### 12.6 Enquiry, lead and quotation

- CRM-001: Public enquiry is associated with the vendor selected by storefront route.
- CRM-002: Lead ownership and assignment are tenant-scoped.
- CRM-003: Quotation line prices/tax are snapshotted on sent revisions.
- CRM-004: Customer accepts/rejects a valid revision through global identity authorization.
- CRM-005: Accepted quotation converts to one order at most, with idempotency.
- CRM-006: Vendor CRM and pricing discussions remain private to that tenant.

### 12.7 Cart and checkout

- MKT-001: A cart has one customer_id and exactly one tenant_id.
- MKT-002: First valid cart item fixes cart tenant.
- MKT-003: Every cart item product must match cart tenant; enforce in service and composite database FK.
- MKT-004: Cross-vendor item insertion returns HTTP 409 with stable RFC 9457 problem type CART_VENDOR_MISMATCH.
- MKT-005: Cart switch requires explicit customer confirmation; no silent replacement or data loss.
- MKT-006: Checkout revalidates vendor active status, subscription/marketplace eligibility as policy requires, product publication, price, tax, stock, shipping and vendor merchant-account readiness.
- MKT-007: Checkout creates exactly one order for exactly one tenant.
- MKT-008: All order items reference same-tenant products; order records price/tax/product snapshots.
- MKT-009: Checkout and order creation are idempotent and concurrency-safe.
- MKT-010: Inventory reservation is atomic with order state transition or uses an explicit saga with compensating release.

### 12.8 Vendor-direct order payment

- PAY-001: Every vendor has a verified vendor_payment_account for the selected provider before accepting online payments.
- PAY-002: Customer payment intent/order is created under the vendor's merchant account context, not the platform subscription merchant.
- PAY-003: Gateway settles purchase funds directly to the vendor merchant account according to its merchant agreement.
- PAY-004: Platform stores vendor-owned payment reference, amount, currency, status, provider reference, order ID, tenant ID and reconciliation metadata, but does not hold customer funds.
- PAY-005: Payment webhooks are signature-verified, replay-protected and idempotent.
- PAY-006: Refund is issued against the original vendor merchant account and transaction; refund cannot exceed refundable amount.
- PAY-007: Payment state changes are append-only events or audited transitions; never silently overwrite gateway evidence.
- PAY-008: Platform does not create marketplace payouts, split transfers or a platform vendor settlement ledger for customer purchases.
- PAY-009: Provider adapter must verify merchant account identity on create, verify, refund and reconciliation.
- PAY-010: Mock payment adapter is allowed only in explicit local/test profile and feature flag; production fails closed if selected.
- PAY-011: Production provider activation requires written confirmation that vendor-direct settlement is supported for target jurisdiction, account type and product.

### 12.9 Subscription payment

- SUBPAY-001: Subscription payment collection is a separate payment flow using platform merchant configuration.
- SUBPAY-002: Subscription invoice is issued by the platform to the vendor tenant.
- SUBPAY-003: Subscription payment records reference tenant_subscription and subscription_invoice, never an order.
- SUBPAY-004: Separate provider credentials/configuration, webhook routing, idempotency namespace and reconciliation are used.
- SUBPAY-005: Mock subscription payment behavior is also non-production only and must not simulate production collection.

### 12.10 Order, invoice, fulfilment and returns

- ORD-001: Order has exactly one tenant_id and one global customer_id.
- ORD-002: Every order item, invoice, vendor payment, shipment, return and refund shares the order tenant_id.
- ORD-003: Order lifecycle supports draft, confirmed, payment pending/paid, fulfilment, shipped/delivered or installation, completed, cancelled and refund states.
- ORD-004: Vendor order number is unique per tenant.
- ORD-005: Customer portal can list own orders across vendors and clearly labels each vendor.
- FIN-001: Vendor invoice belongs to exactly one vendor order; subscription invoice is a distinct entity.
- FIN-002: Invoice issue/correction rules are immutable/credit-note based.
- SHP-001: Shipment and tracking events belong to one vendor order.
- RET-001: Return belongs to one vendor order and its order items.
- RET-002: Return eligibility, quantities, reason, approval, received condition and refund/credit outcomes are recorded.
- RET-003: Return/refund endpoints validate tenant ownership and original payment.

### 12.11 Inventory, branches and warehouses

- INV-001: Tenant owns branches, warehouses, inventory balances and transactions.
- INV-002: Stock distinguishes on-hand, reserved, available and damaged quantities.
- INV-003: Inventory movements are immutable; correction uses reversing transactions.
- INV-004: Reservations cannot oversell stock under concurrency.
- INV-005: Transfer source and destination must belong to the same tenant.
- INV-006: Serialized products retain serial traceability through fulfilment/installation.

### 12.12 Installation, warranty and service

- OPS-001: Installation derives from eligible order/quotation and has tenant/customer/site/order ownership.
- OPS-002: Engineers can access only assigned jobs unless explicitly elevated.
- OPS-003: Warranty is linked to eligible order item/product/installation.
- OPS-004: Service request is tenant-owned and linked to the relevant customer and order/installation.
- OPS-005: Customer sees only own installation, warranty and service details across vendors.
- OPS-006: Vendor sees only its own service history and customer relationship.

### 12.13 Notifications, documents and CMS

- NTF-001: Email uses Spring Mail; Mailpit is the local development SMTP inbox.
- NTF-002: SMS/WhatsApp use a provider interface and mock adapter behind explicit feature flags; Twilio may be added later.
- NTF-003: Mock delivery cannot be enabled in production.
- DOC-001: OpenPDF generates quotations, vendor invoices, subscription invoices and receipts from distinct templates/data owners.
- DOC-002: MinIO is used locally behind an object-storage port; production uses S3-compatible adapter.
- DOC-003: Object keys and document metadata include tenant ownership; global customer access is granted only after transaction authorization.
- CMS-001: Published vendor storefront content is tenant-owned and path-routed.

### 12.14 Reporting and audit

- RPT-001: Vendor dashboards aggregate only the active tenant's own customer relationships and transactions.
- RPT-002: Customer portal aggregates only the authenticated customer's transactions across tenants.
- RPT-003: Platform analytics are aggregated/platform scope; tenant-private detail requires explicit audited support access.
- AUD-001: Record actor, tenant, action, resource, outcome, timestamp, correlation ID and reason as appropriate.
- AUD-002: Keep platform audit and vendor audit access separate.
- AUD-003: Financial and audit events are immutable to ordinary users; corrections use compensating records.

## 13. Core workflows and diagrams

### 13.1 Enquiry to fulfilment

~~~mermaid
flowchart TD
  A[Customer selects vendor on marketplace] --> B[Vendor storefront and published catalogue]
  B --> C[Enquiry / lead under selected tenant]
  C --> D[Vendor quotation]
  D --> E[Customer accepts quotation]
  E --> F[Single-vendor cart or order checkout]
  F --> G[Vendor merchant payment request]
  G --> H[Customer pays vendor directly]
  H --> I[Verified gateway event]
  I --> J[Vendor order marked paid]
  J --> K[Inventory reservation and fulfilment]
  K --> L[Shipment or installation]
  L --> M[Vendor invoice / receipt and warranty]
  M --> N[Returns / service where eligible]
~~~

### 13.2 Checkout sequence

~~~mermaid
sequenceDiagram
  autonumber
  actor Customer
  participant Web as React Marketplace
  participant API as Spring Boot API
  participant Cart as Cart/Checkout Module
  participant DB as MySQL
  participant Pay as Vendor Payment Adapter
  participant Gateway as Vendor Merchant Gateway
  participant Vendor as Vendor Merchant Account
  Customer->>Web: Select Vendor A and add product
  Web->>API: Add item (product reference)
  API->>Cart: Resolve customer and vendor from trusted route/product
  Cart->>DB: Validate cart tenant and product tenant
  DB-->>Cart: Same-tenant product and cart
  Cart-->>Web: Cart updated for Vendor A
  Customer->>Web: Checkout
  Web->>API: POST checkout with idempotency key
  API->>Cart: Revalidate tenant, price, tax, stock and merchant readiness
  Cart->>DB: Create PENDING order and reserve stock transactionally
  Cart->>Pay: Create payment for Vendor A merchant account
  Pay->>Gateway: Create vendor-context payment request
  Gateway-->>Pay: Provider payment reference / client action
  Pay-->>API: Payment reference
  API-->>Web: Vendor-hosted/tokenized payment action
  Customer->>Gateway: Authorize/pay vendor
  Gateway->>Vendor: Settle funds directly to Vendor A
  Gateway-->>API: Signed payment webhook
  API->>Pay: Verify signature and vendor merchant reference
  Pay->>DB: Idempotently record payment event/status
  DB-->>API: Order/payment state updated
  API-->>Web: Customer order status
~~~

### 13.3 Direct vendor payment settlement design

~~~mermaid
flowchart TD
  C[Customer] -->|Pays order total| G[Gateway payment flow]
  G -->|Merchant context = Vendor A| VA[Vendor A merchant account]
  G -->|Signed status event only| WH[Platform webhook]
  WH --> VPA[Vendor Payment Adapter]
  VPA -->|Verify signature, merchant, amount, currency and order| DB[(Tenant-owned payment record)]
  DB --> O[Vendor A order payment status]
  DB --> REC[Vendor A reconciliation reference]
  subgraph No platform custody
    N[No platform customer-fund balance]
    P[No pooled funds]
    S[No marketplace payout/split ledger]
  end
  G -.-> N
~~~

Design consequences:

- The payment provider's merchant context must be Vendor A's verified merchant account.
- Gateway settlement is made by the gateway to Vendor A under the vendor's merchant agreement.
- The platform records status and references, not a platform-held customer cash balance.
- The platform does not create payout instructions or marketplace split transfers.
- Vendor reconciliation compares platform order/payment records with gateway/vendor settlement reports.
- Vendor gateway fees and settlement timing are determined by the gateway/vendor agreement and are not assumed by the platform.
- The platform's subscription charges use a distinct platform merchant account and separate records.

Production note: the selected provider must contractually and technically support the intended vendor-direct model in the target country. The adapter contract is provider-neutral; a named provider cannot be considered approved merely because an SDK exists.

### 13.4 Subscription state machine

~~~mermaid
stateDiagram-v2
  [*] --> PENDING
  PENDING --> ACTIVE: Initial subscription payment succeeds
  PENDING --> PAST_DUE: Initial payment fails
  PAST_DUE --> GRACE_PERIOD: Retry policy starts grace
  ACTIVE --> PAST_DUE: Renewal invoice unpaid at due time
  ACTIVE --> CANCELLED: Immediate cancellation approved
  ACTIVE --> CANCEL_AT_PERIOD_END: Cancel at period end requested
  CANCEL_AT_PERIOD_END --> CANCELLED: Period ends
  GRACE_PERIOD --> ACTIVE: Payment succeeds
  GRACE_PERIOD --> SUSPENDED: Grace period expires unpaid
  SUSPENDED --> ACTIVE: Outstanding invoice paid / reactivated
  SUSPENDED --> CANCELLED: Subscription cancelled under policy
  ACTIVE --> EXPIRED: Non-renewing term ends
  CANCELLED --> [*]
  EXPIRED --> [*]
~~~

Subscription states:

- PENDING: subscription created; initial payment/activation incomplete.
- ACTIVE: entitled and paid for current period.
- PAST_DUE: invoice is unpaid/failed collection; retries and reminders are pending.
- GRACE_PERIOD: configured grace window is running; default permits normal access with prominent notices.
- SUSPENDED: subscription entitlement blocks vendor writes; read-only data access may remain for billing/support according to policy.
- CANCEL_AT_PERIOD_END: cancellation requested but current paid period remains active.
- CANCELLED: subscription ended by explicit cancellation.
- EXPIRED: paid term ended without renewal.

Vendor tenant lifecycle states (PENDING, UNDER_REVIEW, ACTIVE, SUSPENDED, CLOSED) are separate. Effective vendor access requires tenant lifecycle ACTIVE, acceptable subscription state, active membership and required permission. A security/manual tenant suspension is not automatically cleared by subscription payment.

### 13.5 Subscription billing sequence

~~~mermaid
sequenceDiagram
  autonumber
  participant Job as Subscription Scheduler
  participant Sub as Subscription Module
  participant DB as MySQL
  participant Pay as Platform Subscription Payment Adapter
  participant PG as Platform Merchant Gateway
  participant Vendor as Vendor Admin
  Job->>Sub: Renewal due
  Sub->>DB: Idempotently create subscription invoice
  Sub->>Pay: Collect invoice using platform merchant context
  Pay->>PG: Create platform subscription payment
  PG-->>Pay: Payment result/reference
  Pay->>DB: Record subscription payment attempt
  alt Payment succeeds
    PG-->>Sub: Verified success event
    Sub->>DB: Mark invoice paid and subscription ACTIVE
    Sub-->>Vendor: Receipt/renewal notification
  else Payment fails
    PG-->>Sub: Verified failure event
    Sub->>DB: Mark PAST_DUE / GRACE_PERIOD
    Sub-->>Vendor: Payment failure and due-date notice
  end
~~~

Customer purchase payment APIs and subscription billing APIs must use distinct provider configuration, secrets, idempotency namespaces, webhook routes or event classification, tables, accounting/reporting and reconciliation.

## 14. ER model and database design

### 14.1 Consistent ER diagram

This diagram defines principal ownership and relationships. Join/child tables carry tenant_id where marked. The customer identity is global; vendor relationships and transactions are tenant-owned.

~~~mermaid
erDiagram
  USERS ||--o| CUSTOMER_PROFILES : has
  USERS ||--o{ TENANT_USERS : joins
  TENANTS ||--o{ TENANT_USERS : employs
  CUSTOMER_PROFILES ||--o{ TENANT_CUSTOMERS : relates
  TENANTS ||--o{ TENANT_CUSTOMERS : owns_relationship
  TENANT_CUSTOMERS ||--o{ TENANT_CUSTOMER_NOTES : has_private_notes

  TENANTS ||--|| VENDOR_PROFILES : has
  TENANTS ||--o{ PRODUCTS : owns
  TENANTS ||--o{ CATEGORIES : owns
  TENANTS ||--o{ SOLAR_PACKAGES : owns
  TENANTS ||--o{ VENDOR_PAYMENT_ACCOUNTS : configures

  CUSTOMER_PROFILES ||--o{ CARTS : owns
  TENANTS ||--o{ CARTS : scopes
  CARTS ||--o{ CART_ITEMS : contains
  PRODUCTS ||--o{ CART_ITEMS : selected

  CUSTOMER_PROFILES ||--o{ ORDERS : places
  TENANTS ||--o{ ORDERS : owns
  ORDERS ||--o{ ORDER_ITEMS : contains
  PRODUCTS ||--o{ ORDER_ITEMS : snapshot_source
  ORDERS ||--o{ INVOICES : billed
  ORDERS ||--o{ VENDOR_PAYMENTS : paid_by
  VENDOR_PAYMENT_ACCOUNTS ||--o{ VENDOR_PAYMENTS : processes
  VENDOR_PAYMENTS ||--o{ PAYMENT_ATTEMPTS : has
  VENDOR_PAYMENTS ||--o{ REFUNDS : reversed_by
  ORDERS ||--o{ SHIPMENTS : fulfilled_by
  ORDERS ||--o{ RETURNS : returned_by

  SUBSCRIPTION_PLANS ||--o{ TENANT_SUBSCRIPTIONS : selected
  TENANTS ||--o{ TENANT_SUBSCRIPTIONS : subscribes
  TENANT_SUBSCRIPTIONS ||--o{ SUBSCRIPTION_INVOICES : billed
  SUBSCRIPTION_INVOICES ||--o{ SUBSCRIPTION_PAYMENTS : paid_by

  TENANTS ||--o{ INVENTORY_BALANCES : owns
  TENANTS ||--o{ INVENTORY_TRANSACTIONS : records
  TENANTS ||--o{ LEADS : manages
  TENANTS ||--o{ QUOTATIONS : creates
  TENANTS ||--o{ SERVICE_REQUESTS : owns
  TENANTS ||--o{ VENDOR_AUDIT_EVENTS : records
  USERS ||--o{ PLATFORM_AUDIT_EVENTS : acts

  USERS {
    bigint id PK
    string normalized_email UK
    string normalized_phone
    string auth_status
  }
  CUSTOMER_PROFILES {
    bigint id PK
    bigint user_id FK,UK
    string display_name
  }
  TENANTS {
    bigint id PK
    string slug UK
    string lifecycle_status
  }
  TENANT_USERS {
    bigint id PK
    bigint tenant_id FK
    bigint user_id FK
    string membership_status
  }
  TENANT_CUSTOMERS {
    bigint id PK
    bigint tenant_id FK
    bigint customer_id FK
  }
  TENANT_CUSTOMER_NOTES {
    bigint id PK
    bigint tenant_id FK
    bigint tenant_customer_id FK
    string note_visibility
  }
  PRODUCTS {
    bigint id PK
    bigint tenant_id FK
    string sku
    string publication_status
  }
  CARTS {
    bigint id PK
    bigint tenant_id FK
    bigint customer_id FK
    string status
  }
  CART_ITEMS {
    bigint id PK
    bigint tenant_id FK
    bigint cart_id FK
    bigint product_id FK
  }
  ORDERS {
    bigint id PK
    bigint tenant_id FK
    bigint customer_id FK
    string order_number
    decimal total
  }
  ORDER_ITEMS {
    bigint id PK
    bigint tenant_id FK
    bigint order_id FK
    bigint product_id FK
    decimal unit_price_snapshot
  }
  VENDOR_PAYMENTS {
    bigint id PK
    bigint tenant_id FK
    bigint order_id FK
    bigint merchant_account_id FK
    decimal amount
  }
  SUBSCRIPTION_PLANS {
    bigint id PK
    string billing_interval
    decimal price
  }
  TENANT_SUBSCRIPTIONS {
    bigint id PK
    bigint tenant_id FK
    bigint plan_id FK
    string state
  }
~~~

The ER diagram is conceptual. Physical migrations must define full columns, keys, checks, indexes, deletion behavior and audit columns. All tenant-owned child tables must include tenant_id and use composite constraints wherever possible.

### 14.2 Global and tenant-owned table inventory

Global/platform-owned tables (no tenant_id unless a separate scope is justified):
- users
- customer_profiles
- subscription_plans and subscription_plan_prices
- platform_configuration
- platform_audit_events
- provider catalog/reference metadata
- global permission definitions

Tenant-owned tables (tenant_id NOT NULL):
- tenants' vendor profile and tenant configuration
- tenant_users, tenant roles and tenant role grants
- tenant_customers and tenant_customer_notes
- categories, products, product images metadata, solar_packages, package_items and vendor prices
- branches, warehouses, inventory_balances and inventory_transactions
- enquiries, leads, site_surveys, quotations, quotation revisions and quotation_items
- carts, cart_items
- orders, order_items, order_status_history
- invoices, vendor_payment_accounts, vendor_payments, payment_attempts, refunds
- shipments, shipment_events, returns and return_items
- installations, installation assignments/checklists, warranties, service_requests and service_visits
- vendor CMS content, vendor notifications, tenant-scoped exports and vendor_audit_events
- tenant_subscriptions, subscription invoices, subscription payments, subscription attempts, usage and subscription events

### 14.3 Mandatory ownership constraints

- Composite unique key on tenant-owned parents: UNIQUE(tenant_id, id) to support tenant-aware foreign keys.
- Product: UNIQUE(tenant_id, sku).
- Vendor slug: UNIQUE(slug) globally because all stores share the domain.
- Tenant customer: UNIQUE(tenant_id, customer_id).
- Tenant membership: UNIQUE(tenant_id, user_id).
- Vendor order: UNIQUE(tenant_id, order_number).
- Order item references order and product with tenant-aware composite foreign keys.
- Cart item references cart and product with tenant-aware composite foreign keys.
- Vendor payment references order and vendor_payment_account with same tenant_id.
- Vendor invoice, shipment, return and refund reference the originating vendor order and tenant_id.
- Subscription invoice/payment references tenant_subscription and tenant_id; never vendor order.
- Money uses DECIMAL, never floating point. Store currency and tax snapshots.
- Tenant-owned rows use ON DELETE RESTRICT for financial, audit and historical records. Use archival/anonymization workflows where lawful.

### 14.4 Example ownership-safe constraints

~~~sql
CREATE TABLE products (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  sku VARCHAR(80) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_products_tenant_id_id (tenant_id, id),
  UNIQUE KEY uk_products_tenant_sku (tenant_id, sku),
  CONSTRAINT fk_products_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE TABLE orders (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  order_number VARCHAR(80) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_orders_tenant_id_id (tenant_id, id),
  UNIQUE KEY uk_orders_tenant_number (tenant_id, order_number),
  CONSTRAINT fk_orders_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_orders_customer
    FOREIGN KEY (customer_id) REFERENCES customer_profiles(id)
);

CREATE TABLE order_items (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_order_items_order
    FOREIGN KEY (tenant_id, order_id)
    REFERENCES orders(tenant_id, id),
  CONSTRAINT fk_order_items_product
    FOREIGN KEY (tenant_id, product_id)
    REFERENCES products(tenant_id, id)
);
~~~

This illustrates the same-tenant parent/child relationship. Production migrations must also define data types, indexes, monetary snapshots, quantity constraints and deletion policy.

## 15. REST API conventions

### 15.1 Standards

- REST under /api/v1.
- OpenAPI 3 contract generated/maintained with request and response schemas.
- Errors use RFC 9457 Problem Details (application/problem+json).
- Pagination, sorting and filtering are server-side with bounded page size.
- Authentication uses JWT access token and rotated refresh token.
- Idempotency-Key required for checkout, payment creation/refund and other retry-sensitive mutations.
- Vendor APIs do not accept tenant_id as a client-selected authorization parameter.
- Common-domain vendor slug routes are public discovery only; authenticated vendor context is membership-derived.

### 15.2 API route families

| Family | Example endpoints | Scope |
|---|---|---|
| Authentication | POST /auth/register, /auth/login, /auth/refresh, /auth/logout | Global identity |
| Marketplace | GET /marketplace/vendors, /vendors/{slug}, /vendors/{slug}/products | Public published data |
| Customer | GET /customer/me, /customer/orders, /customer/orders/{id} | Own global identity |
| Platform tenants | POST/GET/PATCH /platform/tenants | Platform permission |
| Platform plans | CRUD /platform/subscription-plans | Platform permission |
| Subscription | GET /vendor/subscription, GET /vendor/subscription/invoices | Active tenant |
| Vendor catalogue | CRUD /vendor/products, /vendor/categories, /vendor/packages | Active tenant |
| Vendor CRM | CRUD /vendor/customers, /vendor/leads, /vendor/quotations | Active tenant |
| Cart | GET/POST/PATCH/DELETE /customer/cart | Customer, one tenant per cart |
| Checkout | POST /customer/checkout | Customer, cart tenant |
| Vendor orders | GET /vendor/orders, GET /vendor/orders/{id} | Active tenant |
| Vendor payment | POST /customer/orders/{id}/payment-intent, POST /webhooks/vendor-payments/{provider}, POST /vendor/payments/{id}/refund | Customer/order owner or tenant accountant; webhook verified |
| Subscription payment | POST /webhooks/platform-subscriptions/{provider} | Platform subscription flow |
| Vendor finance | /vendor/invoices, /vendor/payments, /vendor/returns | Active tenant |
| Audit | /platform/audit, /vendor/audit | Separate scopes |

Endpoint examples are illustrative; exact contracts must be maintained in OpenAPI.

### 15.3 Error contracts

- 400: malformed or invalid request.
- 401: unauthenticated.
- 403: authenticated but not permitted.
- 404: resource absent or intentionally non-disclosing for cross-tenant IDs.
- 409: state conflict, cart vendor mismatch, duplicate idempotency mismatch or invalid transition.
- 422: semantically invalid request where adopted by API policy.
- 429: rate limit.

Cross-tenant resource probes must not reveal whether another tenant's identifier exists.

## 16. Technology baseline and integration adapters

| Layer | Required baseline |
|---|---|
| Language/runtime | Java 21 |
| Backend | Spring Boot 3.5, Spring Security, Spring Data JPA, Maven |
| Frontend | React 19, TypeScript, Vite |
| Database | MySQL 8.4 |
| Schema migrations | Flyway |
| Authentication | Spring Security, JWT access tokens, rotated refresh tokens |
| API | REST, OpenAPI 3, RFC 9457 Problem Details |
| Backend tests | JUnit 5, Testcontainers, REST Assured |
| Frontend tests | Vitest, React Testing Library where appropriate, Playwright |
| PDF | OpenPDF |
| Email | Spring Mail; Mailpit for local SMTP capture |
| SMS/WhatsApp | Provider adapter; mock adapter behind explicit flag; Twilio candidate for later integration |
| File storage | Storage port; MinIO locally; S3-compatible production adapter |
| Customer payment | Vendor-direct payment adapter; mock adapter behind explicit non-production flag; Stripe/Razorpay candidates subject to provider approval |
| Subscription payment | Separate platform subscription payment adapter and platform merchant account |
| Cache/rate limiting | Redis |
| Monitoring | Spring Boot Actuator, Micrometer, Prometheus |
| Local environment | Docker Compose |
| Architecture | Modular monolith |

### 16.1 Feature-flag rules for mock adapters

- MOCK_PAYMENT_ENABLED and MOCK_MESSAGING_ENABLED default to false.
- Mock providers are permitted only in explicit local/test profiles.
- Production configuration validation must fail if a mock provider is selected or a mock flag is true.
- Mock payment responses must be clearly marked test/simulated and may not be used as production payment evidence.
- A mock payment must simulate vendor merchant identity, verified/unverified events, duplicate webhook delivery, failure, timeout and refund outcomes.
- Adapter interfaces must not leak provider-specific SDK models into domain services.

### 16.2 Payment adapter contracts

Vendor payment adapter operations:
- Validate vendor merchant-account readiness and capability.
- Create customer payment session/intent within the vendor merchant context.
- Verify signed webhook and correlate provider event, tenant, order, amount and currency.
- Query transaction status.
- Initiate refund against the original vendor merchant account/transaction.
- Import or reconcile vendor settlement references where provider supports it.

Subscription payment adapter operations:
- Create payment for platform subscription invoice using platform merchant context.
- Verify subscription payment webhook and correlate platform invoice/tenant.
- Query transaction and handle retry/refund policies.

Do not reuse merchant IDs, secrets, payment records, webhook idempotency namespaces or reconciliation processes between the two adapters.

### 16.3 Provider approval gate

The future provider selection (including Stripe or Razorpay) must be validated for India/target jurisdiction, merchant onboarding, vendor-direct collection, refunds, webhook signing, settlement reporting, tax invoicing, chargebacks and account eligibility. This is an external provider/legal/commercial approval gate. No provider-specific direct-settlement capability is assumed by this SRS.

## 17. Subscription billing model

### 17.1 Entities

- subscription_plans: platform-owned plan definitions.
- subscription_plan_prices: versioned price, currency and interval.
- tenant_subscriptions: tenant, plan version, start/end, state and renewal settings.
- subscription_invoices: platform-issued subscription invoices, immutable after issue except correction/credit note.
- subscription_payments: payment for a subscription invoice, platform merchant context.
- subscription_payment_attempts: attempt outcome, provider reference, retry schedule and failure.
- subscription_events: append-only state transition and actor/reason.
- subscription_usage: measured entitlement usage by tenant and period.

### 17.2 Billing rules

- Supported intervals: monthly, quarterly and yearly.
- Subscription invoice includes billing period, plan snapshot, tax breakdown, due date and total.
- Renewal is idempotent using subscription ID and billing period.
- Payment retries use bounded backoff and configured maximum attempts.
- Grace duration is a versioned plan/platform policy and the due dates are explicit.
- Default grace behavior: preserve normal vendor access during grace with reminders; on grace expiry set subscription SUSPENDED and block vendor writes. Any read-only mode/storefront policy must be configured and tested.
- Payment success during grace or suspension may reactivate subscription if no independent tenant/security suspension exists.
- Vendor data and financial records are preserved after cancellation/expiry.
- Platform may manually suspend a tenant independent of subscription status.
- Tax, refund and statutory SaaS invoice requirements require local legal/tax review before production.

### 17.3 State transition rules

State machine in §13.4 is normative. Each transition records old state, new state, event, actor/system process, timestamp, reason, invoice/payment reference and correlation ID. Invalid transitions are rejected.

## 18. Direct-to-vendor payment and reconciliation

### 18.1 Ownership and money flow

- Customer order is owned by one vendor tenant.
- Customer payment is created under that tenant's verified merchant account.
- Customer funds settle from the gateway directly to the vendor's merchant account.
- The platform stores payment metadata and verified provider events for order status, customer support and vendor reconciliation.
- The platform does not receive customer purchase funds, hold funds, split funds or pay vendors.
- No platform vendor-payout ledger is required in this model.
- Platform subscription fees are paid separately to the platform merchant account.

### 18.2 Required records

Vendor payment: tenant_id, order_id, customer_id, vendor_payment_account_id, provider, provider_payment_reference, amount, currency, status, authorized_at, captured_at, refunded_amount, provider_settlement_reference (when supplied), created_at.

Vendor payment attempt: tenant_id, payment_id, idempotency_key, attempt status, provider request reference, error category and timestamps.

Vendor payment account: tenant_id, provider, provider merchant-account reference, verification state, capabilities, encrypted credential reference/token handle, last verification time. Raw secrets must not be stored in application tables or logs.

Refund: tenant_id, payment_id, order_id, amount, reason, provider refund reference, status and actor.

The vendor-facing finance view can reconcile order amounts and payment references against provider settlement reports. The exact settlement date, transaction fee and bank credit are provider/vendor contractual details; the platform must not infer them from a successful authorization event.

### 18.3 Payment state model

A provider payment may move through CREATED, REQUIRES_CUSTOMER_ACTION, AUTHORIZED, CAPTURED/ SUCCEEDED, FAILED, CANCELLED, PARTIALLY_REFUNDED and REFUNDED. Actual transitions depend on the selected provider's capabilities. Persist provider events idempotently; do not treat a browser redirect as proof of payment.

### 18.4 Refund and return controls

- Refund must reference original order and payment.
- Refund amount cannot exceed captured minus already refunded amount.
- Partial refunds are supported only when provider and product policy support them.
- Return acceptance and refund execution are separate state transitions.
- Vendor authority and approval thresholds apply.
- Webhook signatures, replay detection and reconciliation must cover refunds as well as payments.

## 19. Database conventions and data integrity

- Use BIGINT identifiers or UUIDs consistently; do not mix strategies without documented reason.
- Use DECIMAL for money and explicit currency codes.
- Timestamps are stored in UTC; tenant timezone controls display and billing date calculation.
- Every mutable entity has created_at, created_by, updated_at, updated_by and version where appropriate.
- Financial and audit rows are append-only or corrected through compensating records.
- Tenant-owned entities include tenant_id NOT NULL.
- Composite foreign keys enforce same-tenant relationships.
- Foreign keys on financial/audit history use RESTRICT; deletion uses controlled archival/anonymization subject to retention.
- Use optimistic locking for mutable aggregates and database locking/conditional updates for stock and checkout concurrency.
- Use unique idempotency scope by tenant, actor/operation and key; store request hash and result.
- Flyway migrations are versioned, tested against clean and upgraded MySQL 8.4 databases and use expand/contract for production.

## 20. Security requirements

### 20.1 Authentication and authorization

- Spring Security is the authoritative authorization layer.
- JWT access tokens are short-lived and audience/issuer/signature validated.
- Refresh tokens rotate on every use; reuse revokes token family.
- Refresh token storage is hashed/revocable; secure HttpOnly/Secure/SameSite cookies are preferred for browser session material with CSRF protection.
- RBAC is permission-based and supplemented by tenant membership and record assignment.
- Platform and vendor permissions are disjoint.
- Global customer self-access is verified by customer ID on every resource.
- Tenant scope is immutable within a request; thread/async context is cleared and explicitly propagated to jobs.

### 20.2 Application and data security

- Validate DTOs with Bean Validation and allow-listed input.
- Use parameterized ORM/SQL.
- Protect against IDOR, mass assignment, injection, CSRF, XSS, SSRF and unsafe uploads.
- CORS allows only approved common-domain origins.
- Apply rate limits to authentication, public enquiry, checkout, payment, exports and webhooks.
- Webhooks verify provider signature, timestamp/replay protection, merchant account, amount, currency and related order/invoice.
- Protect provider secrets with secret manager/encrypted credential references.
- Encrypt data in transit and at rest; redact PII/secrets from logs.
- Signed document URL issuance rechecks customer/tenant authorization.
- Audit cross-tenant denials and support access according to policy.

### 20.3 Critical security tests

- Tenant A cannot use guessed IDs, query filters, path parameters, headers or body fields to access tenant B.
- Tenant A cannot access tenant B private customer notes, price discussions, leads, order/payment/service history, attachments or exports.
- Customer A cannot access Customer B order even when the order belongs to a vendor they both use.
- Vendor A cannot use global customer ID to query Vendor B relationship.
- Cross-tenant foreign key insertion is rejected by MySQL.
- Tenant context is cleared after exceptions and thread reuse.
- Background jobs and exports cannot omit tenant scope.
- Platform support access is audited.

## 21. Non-functional requirements

### 21.1 Performance and scale

- p95 normal read API target <= 500 ms under agreed baseline load, excluding provider calls and large reports.
- p95 normal write API target <= 1 second under agreed baseline load.
- Lists are paginated; default 20, maximum 100 unless approved.
- Reports exceeding 30 seconds run asynchronously.
- Initial capacity test target: 100 active tenants, 1,000 concurrent sessions and 100 API requests/second; revise using measured load.
- Tenant-scoped indexes support all high-volume access paths.

### 21.2 Availability and recovery

- Initial monthly availability target 99.9%, excluding approved maintenance.
- At least two stateless API instances in production where supported.
- Initial RPO target 15 minutes and RTO target 4 hours subject to managed database capability.
- Restore test at least quarterly.
- Document backup, recovery, rollback and incident runbooks.

### 21.3 Observability

- Actuator health/liveness/readiness endpoints with restricted details.
- Micrometer metrics exported to Prometheus.
- Structured JSON logs with correlation/trace IDs and authorized tenant context only.
- Monitor HTTP latency/error rate, JVM, DB pool, Redis, queue/job backlog, payment webhook failures, subscription renewals, notification failures and storage.
- Alert on tenant-isolation denial spikes, failed payment events, renewal backlog, DB saturation and failed backup/restore.

### 21.4 Quality and accessibility

- OpenAPI contract tests and API compatibility checks.
- Unit, integration, browser and security tests are required.
- Core transactional screens target WCAG 2.2 AA.
- Responsive desktop/tablet/mobile web behavior.
- No horizontal overflow on core workspace layouts.

## 22. Integration and background processing

- Use transactional outbox for domain events leaving a database transaction.
- Use inbox/idempotency for webhook and inbound event deduplication.
- External provider calls use timeouts, bounded retry, circuit breaker where appropriate and dead-letter/manual retry.
- Each async event carries event ID, tenant ID if tenant-owned, actor/source, aggregate ID, correlation ID and schema version.
- Validate tenant state and ownership at job execution, not only at enqueue.
- Never enqueue global customer events containing another tenant's private CRM information.

Adapters:
- Email: Spring Mail; Mailpit local.
- SMS/WhatsApp: mock adapter flag for local/test; future Twilio adapter.
- Customer payments: mock vendor-direct adapter flag for local/test; future approved Stripe/Razorpay adapter.
- Subscription payments: separate platform billing adapter.
- Storage: MinIO local and S3-compatible production adapter.
- Cache/rate limit: Redis.

## 23. Screen catalogue

### Public marketplace
- Landing page, vendor directory, vendor profile/storefront, vendor product/package search/detail, enquiry/quotation request, login/register and legal pages.

### Global customer portal
- Global dashboard, profile, order history across vendors, vendor-labelled order details, cart, cart-switch confirmation, checkout, invoices/receipts, payment status, shipments, returns/refunds, installation, warranty, service and notifications.

### Vendor workspace
- Vendor dashboard, vendor profile, catalogue/categories/products/packages/prices, inventory, leads/enquiries, customer relationships, private CRM notes, quotations, single-tenant orders, fulfilment, invoices, vendor payments/reconciliation, returns/refunds, branches/warehouses, employees/roles, reports, audit and settings.

### Platform administration
- Tenant directory/onboarding/status, initial Vendor Admin creation, subscription plans/prices, tenant subscriptions/invoices/payments, platform analytics, platform audit, integration configuration and system health. No commercial product catalogue, inventory or order management.

## 24. API acceptance and behavior matrix

| Operation | Actor | Tenant rule | Key validation |
|---|---|---|---|
| Browse vendor products | Public/customer | Resolve slug to active tenant | Published products only |
| Add cart item | Authenticated customer | Product tenant sets/equals cart tenant | Reject mixed vendor |
| Checkout | Authenticated customer | Exactly one cart tenant | Idempotency, price, stock, merchant ready |
| View customer order | Global customer | order.customer_id equals principal customer ID | Display vendor label |
| View vendor order | Vendor employee | order.tenant_id equals active tenant | Permission + assignment |
| View CRM notes | Vendor employee | tenant_customer.tenant_id equals active tenant | CRM_NOTE_VIEW |
| Create vendor payment | Customer/order owner | Order tenant determines merchant account | Never accept merchant tenant from client |
| Payment webhook | Gateway | Match stored provider account/order tenant | Signature, replay, amount/currency |
| Refund | Vendor accountant | Original payment/order tenant | Permission and refundable balance |
| Renew subscription | Scheduler/platform | Tenant subscription record | Platform payment adapter only |
| Platform tenant suspend | Platform Admin | Platform scope | Reason and audit |

## 25. Acceptance criteria

### 25.1 Tenant and platform ownership

~~~gherkin
Scenario: Super Admin has no commercial catalogue
  Given a Platform Super Admin is authenticated
  When the user accesses platform administration
  Then no platform-owned product, catalogue, inventory or order can be created
  And selling as Saumya Solar requires an ordinary vendor tenant

Scenario: Vendor private CRM isolation
  Given Vendor A and Vendor B both have a relationship with the same global customer
  And each vendor has private CRM notes and pricing discussions
  When Vendor A requests its customer relationship
  Then only Vendor A relationship and notes are returned
  And Vendor B data is not disclosed
~~~

### 25.2 Global identity and customer portal

~~~gherkin
Scenario: One customer identity across vendors
  Given a customer has a global account
  When the customer buys from Vendor A and Vendor B
  Then both orders use the same global customer identity
  And the customer portal lists both orders with the correct vendor labels
  And each vendor sees only its own order and relationship
~~~

### 25.3 Single-vendor cart and checkout

~~~gherkin
Scenario: Mixed vendor cart item is rejected
  Given a cart belongs to Vendor A
  When the customer adds a Vendor B product
  Then the API returns 409 CART_VENDOR_MISMATCH
  And the original cart remains unchanged

Scenario: Checkout creates one vendor order
  Given the cart contains only Vendor A products
  When checkout succeeds
  Then exactly one Vendor A order is created
  And every order item belongs to Vendor A
  And retries with the same idempotency key return the same order
~~~

### 25.4 Direct vendor payment

~~~gherkin
Scenario: Customer payment goes directly to vendor
  Given Vendor A has a verified eligible merchant account
  And the customer checks out a Vendor A cart
  When the customer pays successfully
  Then the payment is created in Vendor A merchant context
  And the gateway settlement destination is Vendor A
  And the platform records the verified payment reference
  And no platform customer-fund balance or vendor payout is created

Scenario: Payment webhook cannot change another tenant order
  Given a signed webhook references Vendor A merchant account
  When the webhook references a Vendor B order or mismatched amount
  Then the webhook is rejected/quarantined
  And no Vendor B order or payment is changed
~~~

### 25.5 Subscription separation and lifecycle

~~~gherkin
Scenario: Vendor subscription renewal is separate
  Given Vendor A has an active SaaS subscription
  When renewal becomes due
  Then a subscription invoice is created
  And payment uses the platform subscription merchant context
  And no vendor customer order payment record is created

Scenario: Unpaid subscription is suspended after grace
  Given Vendor A renewal payment fails
  When the configured grace period expires without payment
  Then subscription becomes SUSPENDED
  And vendor write operations are denied
  And vendor data and financial history remain intact

Scenario: Payment restores subscription
  Given Vendor A subscription is suspended only for non-payment
  When the outstanding subscription invoice is paid
  Then subscription can return to ACTIVE
  And an independent manual/security tenant suspension remains in effect
~~~

### 25.6 Returns, refunds and customer authorization

~~~gherkin
Scenario: Return and refund remain vendor-scoped
  Given an order belongs to Vendor A
  When a return/refund is requested
  Then it references only Vendor A order items and payment
  And Vendor B cannot view, approve or execute it

Scenario: Customer cannot read another customer's order
  Given Customer A and Customer B have marketplace accounts
  When Customer A requests Customer B order ID
  Then no order details are disclosed
~~~

### 25.7 Database integrity

- MySQL rejects an order item whose order and product tenant IDs differ.
- MySQL rejects a cart item whose cart and product tenant IDs differ.
- MySQL rejects a vendor payment whose merchant account tenant differs from the order tenant.
- Tenant-owned rows cannot persist with null tenant_id.
- Flyway clean-install and upgrade tests pass on MySQL 8.4.

## 26. Testing strategy and release gates

Backend:
- JUnit 5 unit tests for domain rules, state transitions and pricing.
- Testcontainers with MySQL 8.4 for Flyway, composite constraints, locking, transaction rollback and tenant isolation.
- REST Assured API tests for authentication, authorization, errors, pagination, idempotency and webhooks.

Frontend:
- Vitest for unit/component tests.
- Playwright for marketplace browsing, global customer flows, vendor workspace, cart vendor-switch, checkout and cross-tenant browser scenarios.

Mandatory test suites:
1. Role × endpoint authorization matrix.
2. Tenant isolation across all tenant-owned entities and operations.
3. Global customer identity with two or more vendors.
4. Vendor-private CRM note and pricing discussion isolation.
5. One-vendor cart and mixed-vendor rejection.
6. Checkout concurrency/idempotency and same-tenant foreign keys.
7. Vendor-direct payment mock scenarios, signature failure, duplicate webhook, mismatch, timeout and refund.
8. Separate subscription payment, renewal, retry, grace and suspension tests.
9. Object storage and export isolation.
10. Background job tenant propagation and thread-context cleanup.
11. Upgrade migration from the currently supported baseline schema.
12. Docker Compose smoke test.

A release is blocked if a critical tenant-isolation test fails, a mock adapter is active in production, direct-vendor payment account context is not verified, or subscription and purchase payment records are mixed.

## 27. Delivery sequence

### Release A — Foundation
- Java 21/Spring Boot 3.5 modular monolith.
- React 19/Vite/TypeScript.
- MySQL 8.4/Flyway.
- Global identity, tenant membership, JWT rotation and permission framework.
- Common-domain routing.
- Tenant ownership matrix translated into migrations and tests.

### Release B — Vendor marketplace and customer identity
- Tenant onboarding and initial Vendor Admin.
- Global customer account.
- Vendor directory/storefront/catalogue.
- Vendor-specific customer relationships and private CRM.
- Vendor dashboards and platform administration.

### Release C — Commercial transactions
- Enquiries, quotations, cart, single-vendor checkout and orders.
- Vendor merchant account onboarding abstraction.
- Flagged mock direct-vendor payment adapter.
- Vendor invoices/receipts and customer order portal.
- Returns/refund domain and authorization.

### Release D — SaaS billing and operations
- Plans, entitlements, subscription invoices/payments, renewals, grace and suspension.
- Inventory and fulfilment.
- Branch/warehouse, shipment, installation, warranty and service workflows.
- Reporting, notifications and audit hardening.

### Release E — Production readiness
- Approved live vendor-direct payment provider and platform subscription payment provider.
- Real messaging adapter only after explicit provider approval.
- Backup/restore, alerting, load/security tests, legal/tax review and release rehearsal.

## 28. Definition of Done

A feature is complete only when:
- Requirement and acceptance criteria are traceable.
- UI, API, service, repository and persistence are integrated.
- Tenant ownership, roles and resource assignment are enforced.
- Database constraints and migrations are reviewed.
- Automated unit, integration, API and relevant Playwright tests pass.
- Idempotency, retry, error handling and audit behavior are present where applicable.
- OpenAPI, operations and user documentation are updated.
- Metrics, logs and alerts are included.
- No mock provider is enabled in production.
- Product Owner accepts the end-to-end journey in staging.

## 29. Risks, assumptions and items requiring approval

1. Gateway vendor-direct merchant capability and onboarding requirements must be confirmed for the chosen provider and jurisdiction.
2. Subscription billing provider, retry schedule, grace duration, tax and invoice compliance require product/finance/legal approval.
3. Vendor returns, refund windows, shipping liability and cancellation policy require business approval.
4. Customer identity verification, account recovery, duplicate account merge and privacy/consent policy require approval.
5. Platform support access to tenant-private information requires a documented break-glass policy.
6. Data retention, erasure and financial record retention must comply with applicable law.
7. Price, GST, settlement-fee and tax invoice requirements require qualified tax/accounting review.

No implementation may silently invent an answer to these business/legal decisions. Defaults in this document define the architecture; the approval gates above govern production activation.

## Appendix A. Naming and ownership examples

- Tenant: TEN-000001
- Global customer: CUS-GLOBAL-000001
- Tenant customer relationship: TCUS-TEN-000001-000001
- Vendor order: ORD-TEN-000001-2026-000001
- Vendor invoice: INV-TEN-000001-2026-000001
- Subscription invoice: SUBINV-TEN-000001-2026-000001

Human-readable identifiers are not authorization tokens. Internal IDs must be protected by ownership checks regardless of whether they are sequential, UUID or opaque identifiers.
