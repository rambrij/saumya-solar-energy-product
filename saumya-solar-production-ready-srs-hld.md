# Saumya Solar Energy Solution

## Software Requirements Specification and High-Level Design

| Document attribute  | Value                                                                                       |
|---------------------|---------------------------------------------------------------------------------------------|
| Product             | Saumya Solar Energy Solution Platform                                                       |
| Document type       | Software Requirements Specification and High-Level Design                                   |
| Version             | 1.0                                                                                         |
| Status              | Baseline for MVP planning                                                                   |
| Target architecture | Multi-tenant SaaS, modular monolith                                                         |
| Backend             | Java 21, Spring Boot 3.x, Maven                                                             |
| Frontend            | React with TypeScript                                                                       |
| Database            | MySQL 8.x                                                                                   |
| Primary users       | Platform administrators, vendors/dealers, vendor employees, customers and service engineers |

---

## 1. Purpose

This document defines the production-ready functional requirements, non-functional requirements, security controls,
architecture, modules, roles and permissions, data model, REST APIs, user interfaces, workflows, tenant-isolation
strategy, and acceptance criteria for the Saumya Solar Energy Solution platform.

The platform will operate as a multi-tenant Solar ERP, CRM, e-commerce, quotation, order, inventory, installation,
invoicing, and service-management SaaS product. A single deployed platform will support multiple independent solar
vendors while preventing one vendor from accessing another vendor's data.

## 2. Product Vision

> One SaaS platform → many solar vendors → many vendor employees → many customers.

The platform must allow Saumya Solar Energy Solution to:

1. Operate its own solar business.
2. Onboard external solar vendors and dealers.
3. Provide each vendor with an isolated store and operational workspace.
4. Allow customers to discover solar packages, request quotations, place orders, track installations, access invoices,
   and request service.
5. Support future subscription plans, white-label branding, multiple branches, multiple warehouses, payment gateways,
   accounting integrations, and mobile applications.

## 3. Scope

### 3.1 In scope

- Public solar website and vendor storefronts
- Customer registration and portal
- Platform and vendor administration
- Multi-tenant identity and access management
- Vendor onboarding and lifecycle management
- Product and solar-package management
- Enquiry, lead, site-survey and CRM management
- Quotation creation, approval and PDF generation
- Order and status management
- Inventory and warehouse management
- Installation scheduling and commissioning
- Invoice and payment tracking
- Warranty and service-request management
- Team and role management
- Blog and content management
- Notifications by email, SMS, WhatsApp adapter and in-app channel
- Operational dashboards, reporting and exports
- Audit trails, monitoring, backups and security controls

### 3.2 Out of scope for the first MVP

- Native Android and iOS applications
- Full accounting or general-ledger functions
- Automated government subsidy submission
- IoT-based live solar-generation monitoring
- AI-based roof analysis using satellite imagery
- Marketplace settlement between the platform and vendors
- Microservice decomposition

These may be delivered in later phases through integrations or dedicated modules.

## 4. Assumptions and Constraints

- The first production release will use Java 21, Spring Boot 3.x, React with TypeScript, and MySQL 8.x.
- The initial architecture will be a modular monolith to reduce infrastructure and operational complexity.
- Vendor-owned business data will use a shared database and shared schema with mandatory `tenant_id` isolation.
- All business APIs will be exposed over HTTPS and versioned under `/api/v1`.
- Currency, tax rules, document numbering, time zone, language, and notification templates will be configurable per
  tenant.
- India will be the initial market, with INR, GST-ready fields, Indian address formats and PIN codes supported.
- Actual subsidy rules, taxation, invoicing obligations and privacy notices must be reviewed with qualified legal and
  tax professionals before production launch.

## 5. Stakeholders

| Stakeholder              | Responsibility                                                  |
|--------------------------|-----------------------------------------------------------------|
| Platform Owner           | Product strategy, vendor onboarding and platform governance     |
| Platform Super Admin     | Vendor lifecycle, global configuration, support and audit       |
| Vendor Admin             | Vendor organization, employees, catalogue, sales and operations |
| Sales Manager/Executive  | Lead, quotation and order management                            |
| Inventory Manager        | Product stock, reservation, adjustment and warehouse operations |
| Operations Manager       | Site survey, installation scheduling and fulfillment            |
| Service Manager/Engineer | Service tickets, visits, warranty and resolution                |
| Accountant               | Invoice, payment and outstanding-payment management             |
| Content Manager          | Blogs, banners, FAQs, testimonials and public content           |
| Customer                 | Product discovery, quotations, orders, payments and service     |
| Support/Auditor          | Authorized troubleshooting and audit investigation              |

## 6. Architecture Principles

1. **Tenant isolation by default:** Tenant scope must be derived from the authenticated identity, never trusted from a
   browser-supplied query parameter.
2. **Backend authorization:** React visibility rules improve UX but do not replace API authorization.
3. **Modular monolith first:** Domain modules must have clear boundaries and communicate through application services or
   domain events.
4. **API-first design:** All web clients consume documented versioned APIs.
5. **Configuration over hard-coding:** Package attributes, statuses, notification templates and vendor branding must be
   configurable.
6. **Auditability:** Security-sensitive and financial operations must preserve who changed what and when.
7. **Idempotency:** Payment callbacks, order conversion, document generation and notification processing must safely
   handle retries.
8. **Secure by design:** Apply OWASP ASVS-aligned controls and test against OWASP Top 10 risks.
9. **Operational readiness:** Health checks, metrics, logs, traces, backups, alerts and recovery procedures are release
   requirements.

## 7. High-Level Architecture

```text
Users
  │
  ├── Public Customer
  ├── Registered Customer
  ├── Vendor Employee
  └── Platform Administrator
  │
  ▼
CDN / WAF / TLS Termination
  │
  ▼
React Web Application
  │ HTTPS + JSON
  ▼
Spring Boot API
  ├── Authentication and Authorization
  ├── Tenant Context and Isolation
  ├── Vendor and User Management
  ├── Catalogue and Solar Packages
  ├── CRM, Surveys and Quotations
  ├── Orders, Inventory and Installations
  ├── Invoices, Payments and Service
  ├── CMS, Notifications and Reporting
  └── Audit and Administration
  │
  ├── MySQL 8.x
  ├── Redis-compatible cache, optional
  ├── S3-compatible object storage
  ├── Email/SMS/WhatsApp providers
  └── Monitoring and centralized logging
```

### 7.1 Recommended deployment topology

- React static assets behind a CDN.
- Spring Boot application deployed as at least two stateless instances in production.
- Managed MySQL with automated backups, encryption and point-in-time recovery where supported.
- Object storage for images, invoices, quotations, service evidence and installation documents.
- Redis-compatible cache for rate limiting, short-lived data and distributed coordination when multiple application
  instances are used.
- Reverse proxy, load balancer or API gateway for TLS termination, request limits and routing.
- Separate development, test, staging and production environments.

### 7.2 Modular monolith package structure

```text
com.saumyasolar
├── shared
├── identity
├── tenant
├── vendor
├── customer
├── catalog
├── packageconfig
├── crm
├── survey
├── quotation
├── order
├── inventory
├── installation
├── invoice
├── payment
├── service
├── warranty
├── notification
├── cms
├── reporting
├── document
└── audit
```

Each module should own its domain model, repositories, services, API controllers and events. Direct access to another
module's repository is prohibited unless explicitly approved as a shared-kernel dependency.

## 8. Tenant-Isolation Strategy

### 8.1 Tenant model

- A tenant represents one independent vendor/dealer organization.
- Each vendor receives one immutable `tenant_id`.
- Tenant-owned entities must include `tenant_id NOT NULL`.
- Platform-owned global reference data may omit `tenant_id` and must be explicitly classified as global.
- Each user account must be associated with one tenant, except platform-level administrators.
- A customer may have separate customer identities for different vendor storefronts during the MVP. Cross-vendor
  customer unification is not required.

### 8.2 Tenant resolution

For an authenticated request:

1. Validate the access token.
2. Resolve `user_id`, `tenant_id`, platform role and vendor roles from trusted token claims or server-side session data.
3. Populate an immutable request-scoped `TenantContext`.
4. Apply tenant scope to every repository query and command.
5. Verify the target resource belongs to the active tenant before returning or modifying it.
6. Record tenant context in audit logs and application logs.

For public storefront requests, the tenant may be resolved from a verified subdomain or store slug, for example:

```text
vendor-a.saumyasolar.example
/shops/vendor-a/products
```

The resolved slug must be mapped server-side to an active tenant. The API must not trust an arbitrary submitted
`tenant_id`.

### 8.3 Database controls

- Include `tenant_id` in tenant-owned primary access paths and indexes.
- Use composite unique constraints such as `UNIQUE(tenant_id, sku)`.
- Repository methods must require tenant context.
- Bulk update/delete operations must include `tenant_id` predicates.
- Direct native SQL must pass code review and have automated tenant-isolation tests.
- Cache keys and object-storage paths must include tenant identity.
- Export jobs must be tenant-scoped.
- Background jobs must carry explicit, validated tenant context.

### 8.4 Defense-in-depth tests

Automated tests must verify:

- Vendor A cannot access Vendor B records by changing a URL identifier.
- Vendor A cannot infer Vendor B data through search, reports, exports, counts or error messages.
- Customer A cannot access another customer's quotation, order, invoice or ticket.
- Cached responses never cross tenants.
- Background jobs process only their designated tenant.
- Platform support access is logged and restricted.

## 9. Roles and Permission Model

Use role-based access control with fine-grained permissions. Vendor Admin may create custom roles from permitted
vendor-level permissions but cannot grant platform permissions.

### 9.1 Standard roles

| Role                   | Scope           | Summary                                               |
|------------------------|-----------------|-------------------------------------------------------|
| `PLATFORM_SUPER_ADMIN` | Platform        | Full platform administration                          |
| `PLATFORM_SUPPORT`     | Platform        | Restricted support access with audit                  |
| `VENDOR_ADMIN`         | Tenant          | Full vendor workspace administration                  |
| `SALES_MANAGER`        | Tenant          | Leads, quotations, customers, sales reports           |
| `SALES_EXECUTIVE`      | Tenant/assigned | Assigned leads, quotations and orders                 |
| `INVENTORY_MANAGER`    | Tenant          | Products, stock, warehouses and transactions          |
| `OPERATIONS_MANAGER`   | Tenant          | Surveys, installation teams and schedules             |
| `SERVICE_MANAGER`      | Tenant          | Service requests, assignment and SLA monitoring       |
| `SERVICE_ENGINEER`     | Assigned        | Assigned visits, notes, evidence and resolution       |
| `ACCOUNTANT`           | Tenant          | Invoices, payments, credit/refund records and reports |
| `CONTENT_MANAGER`      | Tenant          | Storefront content, blog, FAQ and banners             |
| `CUSTOMER`             | Self            | Own profile, quotations, orders, invoices and tickets |

### 9.2 Permission catalogue

```text
TENANT_VIEW, TENANT_UPDATE
USER_VIEW, USER_CREATE, USER_UPDATE, USER_DISABLE
ROLE_VIEW, ROLE_CREATE, ROLE_UPDATE, ROLE_ASSIGN
CUSTOMER_VIEW, CUSTOMER_CREATE, CUSTOMER_UPDATE, CUSTOMER_EXPORT
PRODUCT_VIEW, PRODUCT_CREATE, PRODUCT_UPDATE, PRODUCT_ARCHIVE
PACKAGE_VIEW, PACKAGE_CREATE, PACKAGE_UPDATE, PACKAGE_PUBLISH
LEAD_VIEW, LEAD_CREATE, LEAD_UPDATE, LEAD_ASSIGN, LEAD_EXPORT
SURVEY_VIEW, SURVEY_CREATE, SURVEY_ASSIGN, SURVEY_COMPLETE
QUOTATION_VIEW, QUOTATION_CREATE, QUOTATION_UPDATE, QUOTATION_SEND,
QUOTATION_APPROVE_DISCOUNT, QUOTATION_CONVERT
ORDER_VIEW, ORDER_CREATE, ORDER_UPDATE, ORDER_CANCEL, ORDER_ASSIGN
INVENTORY_VIEW, INVENTORY_RECEIVE, INVENTORY_RESERVE,
INVENTORY_TRANSFER, INVENTORY_ADJUST, INVENTORY_EXPORT
INSTALLATION_VIEW, INSTALLATION_SCHEDULE, INSTALLATION_ASSIGN,
INSTALLATION_UPDATE, INSTALLATION_COMMISSION
INVOICE_VIEW, INVOICE_CREATE, INVOICE_SEND, INVOICE_VOID
PAYMENT_VIEW, PAYMENT_RECORD, PAYMENT_REFUND
SERVICE_VIEW, SERVICE_CREATE, SERVICE_ASSIGN, SERVICE_UPDATE,
SERVICE_RESOLVE, SERVICE_CLOSE
REPORT_VIEW, REPORT_EXPORT
CMS_VIEW, CMS_CREATE, CMS_UPDATE, CMS_PUBLISH
AUDIT_VIEW
```

### 9.3 Critical authorization rules

- Only Platform Super Admin can create, suspend or close a tenant.
- Vendor Admin cannot assign platform roles.
- Service Engineers can update only assigned service visits unless granted broader permission.
- Sales Executives see assigned leads by default; Sales Managers can see all leads within their tenant.
- Inventory adjustment requires a reason code and audit entry.
- Invoice void, payment refund and order cancellation require explicit permissions and mandatory reasons.
- Customers may access only their own records under the vendor tenant.

## 10. Functional Modules and Requirements

### 10.1 Identity and Access Management

- `IAM-001`: Users shall register, sign in, sign out and reset passwords.
- `IAM-002`: The platform shall support email verification and optional mobile verification.
- `IAM-003`: Privileged users shall support configurable multi-factor authentication.
- `IAM-004`: Vendor Admin shall invite, activate, disable and role-assign vendor employees.
- `IAM-005`: Access tokens shall be short-lived; refresh tokens shall support rotation and revocation.
- `IAM-006`: Repeated failed logins shall trigger progressive throttling or temporary lockout.
- `IAM-007`: Password changes, role changes, login failures and account disabling shall be audited.

### 10.2 Vendor and Subscription Management

- `VEN-001`: Platform Admin shall create and onboard vendors.
- `VEN-002`: Vendor profiles shall include legal name, display name, store slug, logo, GST/tax identifier, contact
  information, address and status.
- `VEN-003`: Vendor lifecycle shall support `PENDING`, `VERIFIED`, `ACTIVE`, `SUSPENDED` and `CLOSED`.
- `VEN-004`: Suspended vendors shall not perform write operations; storefront behavior shall be configurable.
- `VEN-005`: Subscription plans shall define user, storage, branch, warehouse and feature limits.
- `VEN-006`: The platform shall prevent usage beyond configured plan limits or require administrator override.

### 10.3 Customer and CRM

- `CRM-001`: Public visitors shall submit enquiries without authentication.
- `CRM-002`: The system shall create leads with source, priority, status, assigned user and follow-up date.
- `CRM-003`: Vendor users shall maintain customer profiles, addresses, notes and communication history.
- `CRM-004`: Lead stages shall include `NEW`, `CONTACTED`, `SITE_VISIT_SCHEDULED`, `SURVEY_COMPLETED`, `QUOTATION_SENT`,
  `NEGOTIATION`, `WON` and `LOST`.
- `CRM-005`: Lost leads shall require a configurable reason.
- `CRM-006`: Authorized users shall export tenant-scoped lead/customer data.

### 10.4 Product and Solar Package Management

- `CAT-001`: Vendors shall manage product categories, manufacturers and products.
- `CAT-002`: Products shall support SKU, model, description, technical attributes, tax category, purchase price, selling
  price, images, documents and status.
- `CAT-003`: Solar packages shall include capacity, package type, components, quantities, price calculation, estimated
  generation, warranty and installation options.
- `CAT-004`: Package attributes shall be configurable and not hard-coded to fixed capacities.
- `CAT-005`: Products and packages shall support `DRAFT`, `ACTIVE`, `INACTIVE` and `ARCHIVED` states.
- `CAT-006`: Public APIs shall expose only active, published and tenant-owned catalogue records.

### 10.5 Site Survey

- `SRVY-001`: Authorized users shall schedule and assign site surveys.
- `SRVY-002`: Surveys shall capture property type, roof type, usable area, shading, sanctioned load, electricity bill
  range, location, notes and photographs.
- `SRVY-003`: Survey completion shall support a checklist and customer acknowledgement.
- `SRVY-004`: A completed survey may be linked to one or more quotations.

### 10.6 Quotation Management

- `QUO-001`: Authorized users shall create quotations from products, packages or custom line items.
- `QUO-002`: Quotations shall calculate subtotal, discounts, taxes, installation charges, grand total and payment
  schedule.
- `QUO-003`: Discount approval shall be required when a configurable threshold is exceeded.
- `QUO-004`: Each sent revision shall be immutable and versioned.
- `QUO-005`: The platform shall generate a branded quotation PDF.
- `QUO-006`: Customers shall accept or reject a valid quotation.
- `QUO-007`: Accepted quotations shall be convertible to orders exactly once unless an authorized correction workflow is
  used.
- `QUO-008`: Quotation validity and expiry shall be enforced.

### 10.7 Order Management

- `ORD-001`: Orders shall be created manually or from accepted quotations.
- `ORD-002`: Order status transitions shall follow a configured state machine.
- `ORD-003`: Invalid status transitions shall return a conflict response and shall not alter data.
- `ORD-004`: Orders shall support sales assignment, customer address, line items, taxes, discounts, payment status and
  installation status.
- `ORD-005`: Status history shall be immutable and include actor, timestamp, old status, new status and reason.
- `ORD-006`: Cancellation shall require permission and reason; fulfilled orders cannot be silently deleted.
- `ORD-007`: Order creation shall support an idempotency key.

Recommended lifecycle:

```text
DRAFT → QUOTATION_ACCEPTED → CONFIRMED → PAYMENT_PENDING
→ PAYMENT_PARTIAL/PAID → MATERIAL_RESERVED → INSTALLATION_SCHEDULED
→ INSTALLATION_IN_PROGRESS → INSTALLED → COMMISSIONED → CLOSED
```

Exceptional states:

```text
ON_HOLD, CANCELLED, REFUND_PENDING, REFUNDED
```

### 10.8 Inventory and Warehouse

- `INV-001`: Vendors shall manage one or more warehouses subject to plan limits.
- `INV-002`: Stock shall distinguish on-hand, reserved, available and damaged quantities.
- `INV-003`: Transactions shall support receipt, reservation, release, issue, transfer, adjustment, return and damage.
- `INV-004`: Inventory transactions shall be immutable; corrections shall use reversing transactions.
- `INV-005`: Stock shall never become negative unless an explicit tenant policy permits controlled backorders.
- `INV-006`: Low-stock alerts shall use a configurable reorder level.
- `INV-007`: Serialized products shall track component serial numbers through installation.
- `INV-008`: Concurrent reservations shall use database locking or optimistic concurrency to prevent overselling.

### 10.9 Installation Management

- `INS-001`: Operations users shall create installation jobs from eligible orders.
- `INS-002`: Jobs shall include site, schedule, assigned team, checklist, products, serial numbers, photographs and
  notes.
- `INS-003`: Engineers shall update only assigned jobs unless granted elevated permission.
- `INS-004`: Commissioning shall require mandatory checklist completion and customer sign-off.
- `INS-005`: Installation completion shall create or activate applicable warranties.
- `INS-006`: Rescheduling and cancellation reasons shall be retained.

### 10.10 Invoice and Payment Management

- `FIN-001`: Authorized users shall generate invoices from order milestones.
- `FIN-002`: Invoice numbers shall be unique per tenant and fiscal sequence.
- `FIN-003`: Issued invoices shall not be edited in place; corrections shall use void/reissue or credit-note workflows.
- `FIN-004`: Payments shall support offline/manual records and future gateway callbacks.
- `FIN-005`: Duplicate payment callbacks shall not create duplicate payments.
- `FIN-006`: Customers shall download their own invoice PDFs and payment receipts.
- `FIN-007`: Financial operations shall be audited.

### 10.11 Warranty and Service Management

- `SVC-001`: Customers and vendor users shall create service requests.
- `SVC-002`: Tickets shall include category, priority, description, installation, product/serial number, warranty status
  and attachments.
- `SVC-003`: Service Managers shall assign engineers and schedule visits.
- `SVC-004`: Ticket states shall follow `OPEN`, `ASSIGNED`, `VISIT_SCHEDULED`, `IN_PROGRESS`, `RESOLVED`,
  `CUSTOMER_CONFIRMED`, `CLOSED` or `CANCELLED`.
- `SVC-005`: Reopening shall be supported according to tenant policy.
- `SVC-006`: Service SLA targets shall be configurable by priority.
- `SVC-007`: Closure shall preserve resolution notes, parts used, evidence and customer confirmation.

### 10.12 Notification Management

- `NOT-001`: The system shall generate notifications for configured domain events.
- `NOT-002`: Channels shall include in-app and email, with pluggable SMS and WhatsApp adapters.
- `NOT-003`: Templates shall be configurable per tenant while platform defaults remain available.
- `NOT-004`: Notification processing shall be asynchronous where practical.
- `NOT-005`: Delivery attempts, provider references, failures and retry counts shall be recorded.
- `NOT-006`: Provider failures shall not roll back completed business transactions.

### 10.13 CMS and Blog

- `CMS-001`: Authorized users shall manage pages, banners, FAQs, testimonials and blogs.
- `CMS-002`: Content lifecycle shall support `DRAFT`, `REVIEW`, `PUBLISHED` and `ARCHIVED`.
- `CMS-003`: Only published content shall be publicly visible.
- `CMS-004`: Content shall support SEO title, description, slug and social image.
- `CMS-005`: Storefront content shall be tenant-scoped.

### 10.14 Reporting and Dashboard

- `RPT-001`: Vendor dashboards shall show tenant-scoped leads, quotations, orders, installations, tickets, stock,
  revenue and outstanding payments.
- `RPT-002`: Platform dashboards shall show vendor status, subscription usage and aggregate platform metrics.
- `RPT-003`: Reports shall support date, status, branch, warehouse, employee and product filters as applicable.
- `RPT-004`: Authorized users shall export CSV/XLSX/PDF according to report type.
- `RPT-005`: Large exports shall execute asynchronously and expire after a configurable period.
- `RPT-006`: Report and export authorization shall use the same tenant and permission rules as transactional APIs.

### 10.15 Audit and Administration

- `AUD-001`: Audit records shall capture tenant, actor, action, resource type, resource identifier, timestamp, outcome
  and correlation identifier.
- `AUD-002`: Sensitive updates shall include old and new values with secrets and payment credentials redacted.
- `AUD-003`: Audit records shall be append-only for normal application users.
- `AUD-004`: Authorized auditors shall filter audit records by tenant, actor, action, resource and date.
- `AUD-005`: Platform support impersonation, if introduced, shall require justification, time-limited access and
  prominent audit logging.

## 11. Screen Catalogue

### 11.1 Public screens

| Screen                    | Primary capabilities                                                      |
|---------------------------|---------------------------------------------------------------------------|
| Home                      | Company information, packages, benefits, testimonials and calls to action |
| Products                  | Search, filters, categories, pricing and product details                  |
| Solar Packages            | Capacity, components, estimated generation, pricing and quotation request |
| Solar Calculator          | Basic requirement/savings estimate with disclaimer                        |
| Blog                      | Published articles and categories                                         |
| About Us                  | Vendor/company information                                                |
| Contact Us                | Contact details, map and enquiry form                                     |
| Enquiry/Quotation Request | Customer and site requirement capture                                     |
| Login/Register            | Customer authentication and verification                                  |
| Legal Pages               | Privacy, terms, cancellation and refund policies                          |

### 11.2 Customer portal screens

- Dashboard
- Profile and addresses
- My enquiries
- My quotations and quotation details
- Quotation acceptance/rejection
- My orders and timeline
- My invoices and receipts
- My payments
- My installations and schedules
- My solar systems and warranties
- Service requests and service timeline
- Documents
- Notifications

### 11.3 Vendor screens

- Vendor dashboard
- Leads pipeline and lead details
- Customers and customer details
- Site surveys and schedule
- Quotations, revisions and PDF preview
- Orders and order timeline
- Products, categories and solar packages
- Warehouses, stock and transactions
- Installation calendar and job details
- Invoices, payments and outstanding amounts
- Service dashboard, tickets and visits
- Team, users, roles and permissions
- Reports and export center
- Blog and CMS
- Notification templates
- Vendor settings, branding, numbering and taxes
- Audit log

### 11.4 Platform administration screens

- Platform dashboard
- Vendor list, onboarding and vendor details
- Subscription plans and limits
- Global templates and catalogue references
- Platform users and support roles
- Feature flags and configuration
- Audit and security events
- Notification provider configuration
- Job and integration monitoring
- System health and usage

## 12. Core Workflows

### 12.1 Enquiry to commissioning

```text
Public enquiry
→ Lead created
→ Sales assignment
→ Customer contacted
→ Site survey scheduled
→ Survey completed
→ Quotation created
→ Approval if discount threshold exceeded
→ Quotation sent
→ Customer accepts
→ Order created idempotently
→ Payment recorded
→ Inventory reserved
→ Installation scheduled
→ Installation completed
→ Commissioning and customer sign-off
→ Warranty activated
→ Order closed
```

### 12.2 Quotation revision

```text
Draft quotation
→ Calculate prices and taxes
→ Internal approval when required
→ Create immutable revision
→ Generate PDF
→ Send to customer
→ Customer requests change
→ Clone latest revision into new draft
→ Approve and send new revision
→ Previous revision remains available but cannot be accepted if superseded
```

### 12.3 Inventory reservation

```text
Order confirmed
→ Validate available quantity
→ Lock affected stock rows
→ Create reservation transactions
→ Update reserved and available balances
→ Commit
→ If installation/order is cancelled, create release transactions
```

### 12.4 Service request

```text
Customer creates ticket
→ Warranty and priority evaluated
→ Service Manager assigns engineer
→ Visit scheduled
→ Customer notified
→ Engineer records diagnosis, parts and evidence
→ Ticket resolved
→ Customer confirms or ticket reopens
→ Ticket closed
```

### 12.5 Vendor onboarding

```text
Platform Admin creates vendor
→ Vendor is PENDING
→ Documents and contact are verified
→ Store slug and tenant settings created
→ Initial Vendor Admin invited
→ Subscription/limits assigned
→ Vendor activated
→ Vendor configures branding, users, products and packages
```

## 13. REST API Design

### 13.1 Standards

- Base path: `/api/v1`
- JSON request and response bodies
- OAuth2/JWT bearer authentication for protected APIs
- UUID or ULID external identifiers to reduce enumeration risk
- Pagination: `page`, `size`, `sort`
- Filtering through explicit allow-listed query parameters
- Request correlation header: `X-Correlation-ID`
- Idempotency header for critical create operations: `Idempotency-Key`
- Optimistic concurrency through `version` and optionally `If-Match`
- UTC timestamps in ISO 8601 format
- Standard error response using RFC 9457 Problem Details format

Example error:

```json
{
  "type": "https://api.saumyasolar.example/problems/invalid-status-transition",
  "title": "Invalid order status transition",
  "status": 409,
  "detail": "Order cannot move from INSTALLED to DRAFT.",
  "instance": "/api/v1/orders/01J.../status",
  "code": "ORDER_INVALID_STATUS_TRANSITION",
  "correlationId": "c2e7...",
  "fieldErrors": []
}
```

### 13.2 Authentication APIs

| Method | Endpoint                | Purpose                                |
|--------|-------------------------|----------------------------------------|
| POST   | `/auth/register`        | Register customer                      |
| POST   | `/auth/login`           | Authenticate user                      |
| POST   | `/auth/refresh`         | Rotate refresh token                   |
| POST   | `/auth/logout`          | Revoke current session                 |
| POST   | `/auth/forgot-password` | Request reset                          |
| POST   | `/auth/reset-password`  | Complete reset                         |
| POST   | `/auth/verify-email`    | Verify email                           |
| GET    | `/me`                   | Current user and effective permissions |

### 13.3 Vendor and user APIs

| Method    | Endpoint                                | Purpose                  |
|-----------|-----------------------------------------|--------------------------|
| GET/POST  | `/platform/vendors`                     | List/create vendors      |
| GET/PATCH | `/platform/vendors/{vendorId}`          | View/update vendor       |
| POST      | `/platform/vendors/{vendorId}/activate` | Activate vendor          |
| POST      | `/platform/vendors/{vendorId}/suspend`  | Suspend vendor           |
| GET/PATCH | `/vendor/profile`                       | Current vendor profile   |
| GET/POST  | `/users`                                | List/invite tenant users |
| GET/PATCH | `/users/{userId}`                       | View/update tenant user  |
| POST      | `/users/{userId}/disable`               | Disable tenant user      |
| GET/POST  | `/roles`                                | List/create roles        |
| PATCH     | `/roles/{roleId}`                       | Update role permissions  |

### 13.4 Catalogue and package APIs

| Method    | Endpoint                              | Purpose              |
|-----------|---------------------------------------|----------------------|
| GET/POST  | `/products`                           | List/create products |
| GET/PATCH | `/products/{productId}`               | View/update product  |
| POST      | `/products/{productId}/archive`       | Archive product      |
| GET/POST  | `/product-categories`                 | Manage categories    |
| GET/POST  | `/solar-packages`                     | List/create packages |
| GET/PATCH | `/solar-packages/{packageId}`         | View/update package  |
| POST      | `/solar-packages/{packageId}/publish` | Publish package      |
| GET       | `/public/stores/{storeSlug}/products` | Public products      |
| GET       | `/public/stores/{storeSlug}/packages` | Public packages      |

### 13.5 CRM, survey and quotation APIs

| Method    | Endpoint                                     | Purpose                    |
|-----------|----------------------------------------------|----------------------------|
| POST      | `/public/stores/{storeSlug}/enquiries`       | Submit public enquiry      |
| GET/POST  | `/leads`                                     | List/create leads          |
| GET/PATCH | `/leads/{leadId}`                            | View/update lead           |
| POST      | `/leads/{leadId}/assign`                     | Assign lead                |
| POST      | `/leads/{leadId}/transition`                 | Change lead stage          |
| GET/POST  | `/site-surveys`                              | List/create surveys        |
| POST      | `/site-surveys/{surveyId}/complete`          | Complete survey            |
| GET/POST  | `/quotations`                                | List/create quotation      |
| GET/PATCH | `/quotations/{quotationId}`                  | View/update draft          |
| POST      | `/quotations/{quotationId}/submit-approval`  | Request approval           |
| POST      | `/quotations/{quotationId}/send`             | Freeze revision and send   |
| POST      | `/customer/quotations/{quotationId}/accept`  | Customer accepts           |
| POST      | `/customer/quotations/{quotationId}/reject`  | Customer rejects           |
| POST      | `/quotations/{quotationId}/convert-to-order` | Convert accepted quotation |

### 13.6 Order, inventory and installation APIs

| Method    | Endpoint                                     | Purpose                      |
|-----------|----------------------------------------------|------------------------------|
| GET/POST  | `/orders`                                    | List/create orders           |
| GET/PATCH | `/orders/{orderId}`                          | View/update permitted fields |
| POST      | `/orders/{orderId}/transition`               | Change order status          |
| POST      | `/orders/{orderId}/cancel`                   | Cancel order                 |
| GET       | `/orders/{orderId}/history`                  | Order timeline               |
| GET/POST  | `/warehouses`                                | Manage warehouses            |
| GET       | `/inventory/items`                           | View stock balances          |
| POST      | `/inventory/receipts`                        | Receive stock                |
| POST      | `/inventory/reservations`                    | Reserve stock                |
| POST      | `/inventory/transfers`                       | Transfer stock               |
| POST      | `/inventory/adjustments`                     | Adjust stock with reason     |
| GET/POST  | `/installations`                             | List/create jobs             |
| POST      | `/installations/{installationId}/assign`     | Assign team                  |
| POST      | `/installations/{installationId}/reschedule` | Reschedule job               |
| POST      | `/installations/{installationId}/complete`   | Complete checklist           |
| POST      | `/installations/{installationId}/commission` | Commission system            |

### 13.7 Invoice, payment and service APIs

| Method    | Endpoint                               | Purpose                   |
|-----------|----------------------------------------|---------------------------|
| GET/POST  | `/invoices`                            | List/create invoices      |
| GET       | `/invoices/{invoiceId}/pdf`            | Download invoice          |
| POST      | `/invoices/{invoiceId}/send`           | Send invoice              |
| POST      | `/invoices/{invoiceId}/void`           | Void invoice              |
| GET/POST  | `/payments`                            | List/record payments      |
| POST      | `/payments/{paymentId}/refund`         | Record/process refund     |
| POST      | `/payment-webhooks/{provider}`         | Receive provider callback |
| GET/POST  | `/service-requests`                    | List/create tickets       |
| GET/PATCH | `/service-requests/{ticketId}`         | View/update ticket        |
| POST      | `/service-requests/{ticketId}/assign`  | Assign engineer           |
| POST      | `/service-requests/{ticketId}/visits`  | Schedule visit            |
| POST      | `/service-requests/{ticketId}/resolve` | Resolve ticket            |
| POST      | `/service-requests/{ticketId}/close`   | Close ticket              |

### 13.8 Reporting, CMS and audit APIs

| Method   | Endpoint                           | Purpose                         |
|----------|------------------------------------|---------------------------------|
| GET      | `/dashboard/vendor`                | Vendor metrics                  |
| GET      | `/platform/dashboard`              | Platform metrics                |
| GET      | `/reports/sales`                   | Sales report                    |
| GET      | `/reports/inventory`               | Inventory report                |
| POST     | `/exports`                         | Start asynchronous export       |
| GET      | `/exports/{exportId}`              | Export status/download link     |
| GET/POST | `/cms/blogs`                       | Manage blogs                    |
| POST     | `/cms/blogs/{blogId}/publish`      | Publish blog                    |
| GET      | `/public/stores/{storeSlug}/blogs` | Published blogs                 |
| GET      | `/audit-logs`                      | Search authorized audit records |

## 14. Database Design

### 14.1 Conventions

- Use `BINARY(16)` UUID or a consistent sortable identifier strategy for primary keys.
- Every tenant-owned table includes `tenant_id`, `created_at`, `created_by`, `updated_at`, `updated_by` and `version`
  where appropriate.
- Store timestamps in UTC.
- Monetary values use `DECIMAL(19,4)` and an ISO currency code, not floating-point types.
- Use soft deletion only where business recovery or traceability requires it; financial and transaction records must not
  be physically deleted through normal APIs.
- Encrypt or tokenize sensitive values where warranted.
- Use normalized transactional tables; reporting projections may be added later.

### 14.2 Principal entities

| Entity                   | Key fields and relationships                                            |
|--------------------------|-------------------------------------------------------------------------|
| `tenant`                 | `id`, legal/display name, slug, tax ID, status, plan, locale, time zone |
| `user_account`           | `id`, `tenant_id`, email, phone, password hash, status, last login      |
| `role`                   | `id`, `tenant_id`, name, system flag                                    |
| `permission`             | Global permission code catalogue                                        |
| `user_role`              | User-to-role mapping scoped to tenant                                   |
| `role_permission`        | Role-to-permission mapping                                              |
| `customer`               | `id`, `tenant_id`, account link, name, contact, status                  |
| `address`                | Owner type/id, tenant, address fields and coordinates                   |
| `lead`                   | Customer/prospect, source, stage, priority, assignee, follow-up         |
| `lead_activity`          | Calls, notes, emails, tasks and stage events                            |
| `site_survey`            | Lead/customer, site, survey data, schedule and result                   |
| `product_category`       | Tenant category hierarchy                                               |
| `product`                | SKU, category, manufacturer, model, pricing, tax and status             |
| `product_attribute`      | Flexible product specification key/value                                |
| `solar_package`          | Capacity, type, pricing, generation, warranty and status                |
| `package_item`           | Package-to-product relationship, quantity and optionality               |
| `quotation`              | Customer, survey, revision, status, validity and totals                 |
| `quotation_item`         | Product/package/custom line and calculated values                       |
| `order_header`           | Customer, quotation, status, totals and assignments                     |
| `order_item`             | Snapshot of ordered items and prices                                    |
| `order_status_history`   | Immutable status changes                                                |
| `warehouse`              | Tenant, branch, name and address                                        |
| `inventory_balance`      | Warehouse/product, on-hand, reserved, damaged and version               |
| `inventory_transaction`  | Immutable movement type, quantity, reference and reason                 |
| `installation`           | Order, schedule, team, site, status and commissioning                   |
| `installation_component` | Product/serial numbers installed                                        |
| `installation_checklist` | Checklist item, expected response and completion                        |
| `warranty`               | Customer asset/component, start/end, coverage and status                |
| `invoice`                | Order, number, status, tax totals, issue/due dates                      |
| `invoice_item`           | Item snapshot and financial values                                      |
| `payment`                | Invoice/order, amount, method, provider reference and status            |
| `service_request`        | Customer asset, category, priority, assignee and status                 |
| `service_visit`          | Ticket, engineer, schedule, outcome and parts used                      |
| `notification`           | Event, recipient, channel, status and attempts                          |
| `document`               | Tenant, owner, object key, MIME type, checksum and classification       |
| `blog_post`              | Tenant, slug, content, SEO data and publication status                  |
| `audit_log`              | Tenant, actor, action, resource, values, IP, result and correlation ID  |

### 14.3 Essential constraints and indexes

```sql
UNIQUE tenant(slug)
UNIQUE user_account(tenant_id, email)
UNIQUE product(tenant_id, sku)
UNIQUE solar_package(tenant_id, code)
UNIQUE quotation(tenant_id, quotation_number, revision)
UNIQUE order_header(tenant_id, order_number)
UNIQUE warehouse(tenant_id, code)
UNIQUE inventory_balance(tenant_id, warehouse_id, product_id)
UNIQUE invoice(tenant_id, invoice_number)
UNIQUE payment(tenant_id, provider, provider_reference)
UNIQUE blog_post(tenant_id, slug)
```

Recommended indexes include:

- `(tenant_id, status, created_at)` on leads, quotations, orders, installations and service requests.
- `(tenant_id, assigned_user_id, status)` on leads and service requests.
- `(tenant_id, customer_id, created_at)` on quotations, orders and invoices.
- `(tenant_id, product_id, warehouse_id)` on inventory tables.
- `(tenant_id, actor_user_id, occurred_at)` and `(tenant_id, resource_type, resource_id)` on audit logs.

### 14.4 Relationship overview

```text
Tenant 1 ── * User ── * Role ── * Permission
Tenant 1 ── * Customer 1 ── * Lead 1 ── * SiteSurvey
Customer 1 ── * Quotation 1 ── * QuotationItem
Quotation 0..1 ── 1 Order 1 ── * OrderItem
Order 1 ── * Invoice 1 ── * Payment
Order 1 ── 0..1 Installation ── * InstalledComponent
Product 1 ── * PackageItem * ── 1 SolarPackage
Warehouse 1 ── * InventoryBalance ── 1 Product
ServiceRequest * ── 1 Customer/Installation
ServiceRequest 1 ── * ServiceVisit
```

## 15. Security Requirements

### 15.1 Authentication and session security

- Use Spring Security and a standards-based OAuth2/OIDC-compatible design.
- Hash passwords with Argon2id or BCrypt using a reviewed work factor.
- Do not store access or refresh tokens in browser local storage when a secure HttpOnly, Secure and SameSite cookie
  model is feasible.
- Rotate refresh tokens and revoke the token family after reuse detection.
- Require MFA for Platform Super Admin and make it configurable for vendor privileged roles.
- Re-authenticate for high-risk operations such as role changes, refunds and sensitive profile updates where
  appropriate.

### 15.2 Authorization

- Enforce permission and tenant checks at controller/service boundaries and repository scope.
- Apply deny-by-default policies.
- Prevent insecure direct object references by validating ownership of every resource identifier.
- Do not expose internal numeric sequences where avoidable.
- Test all privileged endpoints with missing, incorrect and cross-tenant identities.

### 15.3 Application and API security

- Validate requests using allow lists and Bean Validation.
- Use parameterized ORM queries and prohibit string-built SQL.
- Apply CSRF protection when cookie-based authentication is used.
- Configure strict CORS allow lists per environment.
- Apply rate limits to login, password reset, public enquiry, exports and webhook endpoints.
- Set security headers including CSP, HSTS, X-Content-Type-Options and frame restrictions.
- Return generic errors without stack traces or internal identifiers.
- Use idempotency and signature verification for external callbacks.

### 15.4 File-upload security

- Allow-list file types, extensions and MIME signatures.
- Enforce size limits and tenant quotas.
- Rename files using generated object keys.
- Store outside the application web root.
- Scan uploaded content for malware before making it available.
- Use short-lived signed URLs for authorized downloads.
- Record checksum, uploader, tenant, classification and scan status.

### 15.5 Data protection

- Use TLS 1.2 or later in transit.
- Enable database and object-storage encryption at rest.
- Store secrets in a managed secret service, never in Git or application logs.
- Mask contact, tax and financial information in logs.
- Define retention and deletion policies for customer data, documents and audit logs.
- Provide controlled data export/deletion workflows subject to legal and financial retention obligations.

### 15.6 Audit and monitoring

Security events must include:

- Successful and failed authentication
- Password reset and MFA changes
- User activation, disabling and role changes
- Vendor activation and suspension
- Cross-tenant access denials
- Stock adjustments
- Quotation discount approvals
- Order cancellation
- Invoice void and payment refund
- Export generation
- Administrative configuration changes

## 16. Non-Functional Requirements

### 16.1 Performance

- `NFR-PERF-001`: 95% of normal read APIs shall respond within 500 ms under the agreed baseline load, excluding large
  reports and external-provider latency.
- `NFR-PERF-002`: 95% of normal write APIs shall respond within 1 second under baseline load.
- `NFR-PERF-003`: Public pages shall target a Largest Contentful Paint below 2.5 seconds on representative mobile
  networks after production optimization.
- `NFR-PERF-004`: Lists shall use server-side pagination with a default size of 20 and configurable maximum of 100.
- `NFR-PERF-005`: Reports exceeding 30 seconds shall execute asynchronously.

### 16.2 Availability and recovery

- `NFR-AVL-001`: Initial production availability target shall be 99.9% monthly, excluding approved maintenance.
- `NFR-AVL-002`: Production shall use at least two stateless API instances where infrastructure permits.
- `NFR-AVL-003`: Target recovery point objective shall be 15 minutes when supported by the managed database.
- `NFR-AVL-004`: Target recovery time objective shall be 4 hours for the initial commercial release.
- `NFR-AVL-005`: Backup restoration shall be tested at least quarterly.

### 16.3 Scalability

- The service must remain stateless at the API tier.
- Horizontal application scaling must not require code changes.
- Cache, queue and scheduler behavior must work correctly with multiple instances.
- Tenant-aware indexes must support growth without full-table scans.
- Initial capacity tests should cover at least 100 active tenants, 1,000 concurrent user sessions and 100 API requests
  per second, then be adjusted using commercial forecasts.

### 16.4 Maintainability and quality

- Use Flyway or Liquibase for all schema changes.
- Enforce module-boundary tests and static analysis.
- Critical domain services require unit tests.
- Tenant isolation, authorization and workflows require integration tests.
- APIs require OpenAPI documentation and contract tests.
- A release must not contain unresolved critical or high-severity vulnerabilities without documented risk acceptance.

### 16.5 Observability

- Use structured JSON logs with timestamp, level, service, environment, tenant where allowed, correlation ID and trace
  ID.
- Expose liveness, readiness and dependency health endpoints through Spring Boot Actuator with restricted detail.
- Capture metrics for request latency, error rate, JVM, database pool, jobs, notifications and business workflows.
- Use distributed tracing for external calls and asynchronous processing where supported.
- Alerts should cover elevated 5xx errors, login abuse, database saturation, provider failure, queue backlog and backup
  failure.

### 16.6 Accessibility and usability

- Target WCAG 2.2 AA for public and core transactional screens.
- Support keyboard navigation, labels, focus visibility and sufficient color contrast.
- Use responsive layouts for desktop, tablet and mobile.
- Confirm destructive actions and clearly display validation errors.

## 17. Integration Requirements

Use ports/adapters so providers can be changed without altering domain logic.

| Integration     | Interface responsibility                                           |
|-----------------|--------------------------------------------------------------------|
| Email           | Send templated emails and return provider message ID/status        |
| SMS             | Send transactional SMS and capture delivery status                 |
| WhatsApp        | Send approved templates and capture provider status                |
| Payment gateway | Create payment request, verify callback signature and query status |
| Object storage  | Upload, scan-state update, signed download and delete/retain       |
| Maps/geocoding  | Site coordinates and address assistance                            |
| Accounting      | Future invoice/customer/payment synchronization                    |

External calls must have timeouts, bounded retries, circuit-breaking where appropriate and dead-letter or manual-retry
handling.

## 18. Background Jobs and Events

Recommended domain events:

```text
LeadCreated
QuotationSent
QuotationAccepted
OrderCreated
OrderStatusChanged
InventoryReserved
PaymentRecorded
InstallationScheduled
InstallationCommissioned
WarrantyActivated
ServiceRequestCreated
ServiceRequestResolved
InvoiceIssued
```

Recommended background jobs:

- Quotation expiry
- Notification delivery and retry
- Low-stock evaluation
- SLA breach evaluation
- Asynchronous report/export generation
- Document cleanup based on retention
- Payment reconciliation
- Stale session/token cleanup

Every job must be idempotent, tenant-aware and observable.

## 19. Validation and Business Rules

- Email addresses must be normalized and validated.
- Indian phone numbers and PIN codes should support configurable validation rather than irreversible formatting
  assumptions.
- Product SKU must be unique per tenant.
- Package component quantity must be positive.
- Discount cannot exceed configured limits without approval.
- Quotation acceptance is allowed only for the latest sent, non-expired revision.
- Order line prices are snapshots and must not change when catalogue prices change.
- Stock reservation cannot exceed available stock unless backorders are enabled.
- Invoice and payment totals use decimal arithmetic and explicit rounding rules.
- Installation commissioning requires mandatory checklist items and serial-number validation when applicable.
- Service closure requires resolution details.
- All destructive or financial actions require a reason where specified.

## 20. Acceptance Criteria

### 20.1 Tenant isolation

```gherkin
Scenario: Vendor cannot access another vendor's order
  Given Vendor A and Vendor B have independent tenants
  And a user is authenticated under Vendor A
  When the user requests an order identifier belonging to Vendor B
  Then the API returns 404 or an approved non-disclosing response
  And no Vendor B data is returned
  And the denied attempt is recorded where security policy requires it
```

```gherkin
Scenario: Tenant isolation applies to reports
  Given Vendor A has 10 orders and Vendor B has 20 orders
  When Vendor A requests the order dashboard and export
  Then only Vendor A's 10 orders are counted or exported
```

### 20.2 Roles and permissions

```gherkin
Scenario: Inventory Manager cannot refund a payment
  Given a user has only the INVENTORY_MANAGER role
  When the user calls the payment refund API
  Then the API returns 403
  And no payment record is changed
```

```gherkin
Scenario: Vendor Admin cannot grant platform permissions
  Given a Vendor Admin creates a custom role
  When the request includes PLATFORM_SUPER_ADMIN permission
  Then the request is rejected
  And the attempted privilege escalation is audited
```

### 20.3 Quotation and order

```gherkin
Scenario: Accepted quotation converts to one order
  Given the latest quotation revision is SENT, valid and accepted
  When the conversion endpoint is called twice with the same idempotency key
  Then exactly one order is created
  And both successful responses identify the same order
```

```gherkin
Scenario: Expired quotation cannot be accepted
  Given a quotation validity date has passed
  When the customer attempts acceptance
  Then the API returns 409
  And no order is created
```

### 20.4 Inventory

```gherkin
Scenario: Concurrent orders cannot oversell stock
  Given five units are available
  When two concurrent requests each reserve four units
  Then only one reservation succeeds
  And the final available quantity is never negative
```

```gherkin
Scenario: Inventory correction is traceable
  Given an authorized Inventory Manager adjusts stock
  When the adjustment is submitted
  Then a reason is mandatory
  And an immutable inventory transaction and audit entry are created
```

### 20.5 Installation

```gherkin
Scenario: Installation requires completion evidence
  Given an installation has mandatory checklist items
  When an engineer attempts completion without required responses or evidence
  Then the API rejects completion with field-level errors
```

### 20.6 Service

```gherkin
Scenario: Customer accesses only own ticket
  Given two customers have separate tickets under the same vendor
  When Customer A requests Customer B's ticket
  Then no ticket details are disclosed
```

### 20.7 Security

```gherkin
Scenario: Uploaded executable is rejected
  Given an authenticated user submits a prohibited executable file
  When the upload is validated
  Then the file is rejected
  And it is not published or made downloadable
```

```gherkin
Scenario: Refresh token reuse is detected
  Given a refresh token has already been rotated
  When the old refresh token is reused
  Then the token family is revoked
  And the security event is recorded
```

### 20.8 Performance and availability

- A representative baseline load test demonstrates the agreed p95 API targets with an error rate below 1%, excluding
  deliberately rejected requests.
- Backup restoration is demonstrated in a non-production environment.
- Readiness fails when the application cannot safely accept traffic.
- One application instance can be removed without losing authenticated business transactions when another healthy
  instance is available.

## 21. Testing Strategy

### 21.1 Unit tests

- Pricing and tax calculation
- Discount approval rules
- Order state machine
- Inventory balance calculations
- Warranty dates
- SLA calculations
- Permission evaluation

### 21.2 Integration tests

Use Testcontainers for MySQL and the actual persistence mappings.

- Repository tenant filtering
- Optimistic/pessimistic concurrency
- Unique constraints
- Transaction rollback
- Idempotency
- Database migrations
- REST authentication and authorization

### 21.3 End-to-end tests

Use Playwright or Cypress for:

- Public enquiry to lead
- Quotation acceptance to order
- Customer order tracking
- Vendor inventory reservation
- Installation completion
- Customer service request
- Vendor role restrictions

### 21.4 Security tests

- SAST and dependency scanning in CI
- Secret scanning
- Container image scanning
- DAST against staging
- OWASP access-control tests
- Cross-tenant penetration tests
- File-upload tests
- Rate-limit and brute-force tests

### 21.5 Release gates

A production release requires:

- Successful automated tests
- Successful migration test against a production-like database copy
- No unresolved critical security findings
- Reviewed backup and rollback procedure
- Updated OpenAPI specification
- Smoke tests in staging
- Product-owner acceptance of committed criteria

## 22. DevOps and Environment Requirements

- Source control with protected main/release branches and mandatory pull-request review.
- CI stages: compile, unit test, static analysis, integration test, frontend test, dependency scan, image build, image
  scan and artifact publication.
- CD stages: development, test, staging, approval and production.
- Build immutable versioned artifacts once and promote the same artifacts through environments.
- Use environment-specific configuration and managed secrets.
- Apply zero-downtime-compatible database migrations using expand-and-contract techniques.
- Define documented rollback for application and database changes.
- Create production runbooks for deployment, database recovery, provider outage, security incident and tenant-access
  investigation.

## 23. Data Migration and Seed Data

- Migrations shall be repeatable, version-controlled and forward compatible.
- Production seed data shall include only required global permissions, system roles, statuses and configuration.
- Demo tenants and test users must never be included in production.
- Product/customer imports shall provide validation preview, row-level errors and tenant-scoped processing.
- Imports must be idempotent or support safely identifiable duplicate handling.

## 24. MVP Delivery Plan

### Phase 1: Commercial MVP

Target modules:

1. Tenant and vendor management
2. Authentication, users, roles and permissions
3. Customer and lead management
4. Product and solar-package management
5. Site survey basics
6. Quotations and branded PDF
7. Orders and status timeline
8. Basic inventory and reservations
9. Invoice generation and manual payment records
10. Customer portal
11. Email and in-app notifications
12. Vendor dashboard and basic reports
13. Audit logs and operational monitoring

Suggested sequence: six to eight two-week sprints for a focused team, followed by hardening and user acceptance. Actual
duration depends on team size, design maturity, integrations and compliance review.

### Phase 2: Operations

- Multiple warehouses and transfers
- Advanced installation planning
- Warranty and AMC
- Full service-management SLA
- SMS and WhatsApp adapters
- Payment gateway
- Advanced dashboards and exports
- Branch management

### Phase 3: Enterprise SaaS

- Subscription billing and metering
- White-label domains and advanced branding
- Approval workflow designer
- Accounting integrations
- Mobile/PWA enhancements
- IoT monitoring
- Advanced analytics
- Selective service decomposition based on measured scale

## 25. Definition of Done

A feature is complete only when:

- Functional requirements and acceptance criteria are implemented.
- Tenant and permission checks are present and tested.
- API and UI validation are implemented.
- Automated unit and integration tests pass.
- Relevant end-to-end tests pass.
- OpenAPI and user-facing documentation are updated.
- Audit, logging and metrics requirements are implemented.
- Accessibility checks are completed for affected screens.
- Database migrations are reviewed and tested.
- Security findings are resolved or formally accepted.
- Product Owner accepts the feature in staging.

## 26. Open Decisions Before Implementation

The following decisions should be finalized during product inception:

1. Authentication provider: embedded Spring Security identity or external OIDC provider.
2. Hosting provider and managed MySQL/object-storage services.
3. Tenant URL model: shared domain path, subdomain or custom domain.
4. Required GST invoice structure and fiscal numbering rules.
5. Payment gateway and settlement model.
6. Email, SMS and WhatsApp providers.
7. Subscription plans, limits and billing policy.
8. Data-retention, deletion and legal-hold policy.
9. Supported languages and currency expansion.
10. Whether one customer identity can belong to multiple vendor tenants.
11. Whether inventory can support controlled negative stock/backorders.
12. Discount and refund approval thresholds.

## 27. Recommended Immediate Next Steps

1. Approve MVP scope and explicitly defer Phase 2/3 capabilities.
2. Finalize roles, permission matrix and order/service state machines.
3. Create low-fidelity screen flows for public, customer, vendor and platform views.
4. Convert the database design into an ER diagram and Flyway baseline migration.
5. Produce the OpenAPI contract for identity, tenant, product, lead, quotation and order modules.
6. Build a vertical slice: login → tenant context → product → quotation → order, including cross-tenant security tests.
7. Set up CI, Testcontainers, observability, vulnerability scanning and staging before expanding modules.

---

## Appendix A: Recommended Technology Baseline

| Layer             | Recommendation                                                        |
|-------------------|-----------------------------------------------------------------------|
| Backend           | Java 21, Spring Boot 3.x, Spring Security, Spring Data JPA            |
| Build             | Maven with version catalog and dependency locking                     |
| Frontend          | React, TypeScript, React Router and a tested component library        |
| Database          | MySQL 8.x with Flyway or Liquibase                                    |
| API               | REST, OpenAPI 3.1 and RFC 9457 errors                                 |
| Testing           | JUnit 5, Mockito, Testcontainers, REST Assured and Playwright/Cypress |
| Storage           | S3-compatible object storage with signed URLs                         |
| Cache             | Redis-compatible service when operationally required                  |
| Observability     | Spring Boot Actuator, Micrometer, OpenTelemetry and centralized logs  |
| Security pipeline | SAST, dependency, secret, DAST and container-image scanning           |
| Packaging         | OCI container images with non-root runtime                            |

## Appendix B: Naming Examples

```text
Tenant: TEN-000001
Customer: CUS-2026-000001
Lead: LED-2026-000001
Quotation: QUO-2026-000001-R01
Order: ORD-2026-000001
Invoice: INV-2026-000001
Installation: INS-2026-000001
Service Request: SRV-2026-000001
```

Identifiers shown to users should be unique within the intended tenant/fiscal scope, while internal identifiers remain
globally unique.
