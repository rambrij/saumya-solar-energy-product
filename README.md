# Saumya Solar Energy Platform — Release 1 & 2

This branch is an incremental hardening and completion branch for the existing Release 1 identity/tenant foundation and Release 2 sales lifecycle. It preserves the existing Spring Boot, MySQL, React, and Docker Compose architecture.

> **Validation honesty:** Commit `a833e33dc036f244e4d7ccf23f99f78222a5a019` passed backend Maven tests, frontend production build, and MySQL 8.4 migration validation through V10. The current branch head adds documentation only after that successful code validation. Release 1/2 still have the explicitly listed limitations below; this is not a production-readiness claim.

## Technology

- Backend: Java 21, Spring Boot 3.5.7, Spring Security, Spring Data JPA, Flyway
- Database: MySQL 8.4
- Frontend: React, TypeScript, Vite
- PDF: OpenPDF
- Local orchestration: Docker Compose

## Local setup

Prerequisites:

- Docker Desktop or Rancher Desktop with Docker-compatible CLI
- Docker Compose v2
- For running checks outside Docker: Java 21, Maven, Node.js 22 and npm

Start the local stack:

```powershell
docker compose up --build
```

Open:

- Frontend: http://localhost:3000
- Backend health: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui/index.html

The optional `.`/start-clean.ps1` script and `docker compose down -v` remove the local MySQL volume. They permanently delete local development data. Do not use them when you need to retain local records.

The Compose file enables demo seeding with `SEED_DATA_ENABLED=true` for local development. Application default is false, so production does not create demo accounts. Production must use its own database/JWT/CORS secrets and must not enable demo seeding. Do not expose the Compose MySQL port to an untrusted network.

For a new production database, run Flyway migrations first. Then bootstrap the first platform administrator once using deployment secrets: set `PLATFORM_BOOTSTRAP_ENABLED=true`, `PLATFORM_BOOTSTRAP_EMAIL`, and `PLATFORM_BOOTSTRAP_PASSWORD` (minimum 16 characters), start the backend, verify the account can sign in, and immediately set `PLATFORM_BOOTSTRAP_ENABLED=false` and remove the bootstrap password. The bootstrap is disabled by default and fails if the email already exists.

## Local demo accounts

These credentials are for a disposable local environment only:

| Role | Email | Password |
|---|---|---|
| Platform Super Admin | platform@saumya.local | Platform@123 |
| Vendor Admin | admin@saumya.local | Admin@123 |
| Vendor Sales User | sales@saumya.local | Sales@123 |
| Customer | customer@saumya.local | Customer@123 |

Change or remove seeded credentials before deploying outside local development.

## Role navigation

- Platform account: platform tenant list and tenant creation with initial vendor-admin provisioning.
- Vendor account: catalogue, customers, enquiries, leads, surveys, quotations, orders and employee lookup, subject to the permissions carried in the session.
- Customer account: active catalogue categories/products/packages, request-a-quotation form, own enquiries, quotations with accept/reject actions, and own orders.
- Vendor Admin employee creation: role choices include SALES_EXECUTIVE, SALES_MANAGER, MANAGER, INVENTORY_MANAGER, SERVICE_ENGINEER, ACCOUNTANT, INSTALLER and OPERATIONS_MANAGER. Privileged platform and vendor-admin roles cannot be assigned through the employee form.
- Sales roles receive catalogue read permission separately from catalogue management permission.

The current frontend is a compact role-aware portal, not a complete set of independently routed production layouts. Hiding a menu is not an authorization control; backend checks are required and remain authoritative.

## Business workflow supported by the current code

1. Platform Admin creates a tenant and its initial Vendor Admin; the vendor creates categories, products, packages, customers and enquiries.
2. Customer can browse the active tenant catalogue and submit an enquiry; vendor can also capture an enquiry. Vendor converts an enquiry into a lead, assigns a same-tenant employee, and tracks lead stage/follow-up and assignment history.
3. Vendor schedules a site survey for a same-tenant lead.
4. Vendor creates a quotation with a customer, optional lead/survey and catalogue line references.
5. Each vendor configures its own discount approval threshold in Vendor Settings. Quotations above that threshold enter pending approval; a user with DISCOUNT_APPROVE approves or rejects.
6. Vendor sends a draft quotation.
7. The customer can accept or reject a quotation visible in their tenant/customer scope.
8. Vendor converts an accepted quotation to an order with an `Idempotency-Key`.


## Implementation matrix

Statuses describe the code currently present in this branch, not an assumption based on the release plan. "Partial" means at least one part exists but the complete workflow/acceptance criteria are not met. "Missing" means no complete implementation was found. Database status covers the current Flyway schema only.

| Feature | Backend | Frontend | Database | Issues / evidence |
|---|---|---|---|---|
| Tenant and vendor management | Partial | Partial | Implemented | `Tenant`, `TenantRepository`, `POST/GET /api/v1/tenants`; platform permission required; tenant creation transaction provisions an initial tenant-scoped VENDOR_ADMIN and platform can activate/suspend tenants. Full tenant editing and broader management UI remain absent. |
| Customer registration | Implemented | Partial | Implemented | `POST /api/v1/auth/register`, `AuthLifecycleService.register`, customer registration form; email verification and duplicate profile recovery are absent. |
| Access and refresh tokens | Implemented | Partial | Implemented | `JwtService`, `JwtFilter`, `AuthLifecycleService`, `refresh_token`; refresh rotates, reuse revokes remaining refresh sessions, and token-version checks invalidate stale access JWTs. Frontend automatic refresh/session-expiry UX and secure cookie storage remain outstanding. |
| Logout and token revocation | Implemented | Implemented | Implemented | `POST /api/v1/auth/logout` revokes all refresh sessions and increments the user's token version; `JwtFilter` rejects previously issued access JWTs. Logout currently has global per-user session semantics. |
| Password reset | Partial | Partial | Implemented | `PasswordResetToken`, forgot/reset endpoints and UI; single-use and expiry checks, reset revokes refresh sessions and invalidates access JWTs. Email delivery is absent; development reset token is returned by API. |
| Roles and permissions | Partial | Implemented | Implemented | `Role`, `Permission`, `DataSeeder`, `GET /api/v1/roles`; tenant roles include SALES_EXECUTIVE, SALES_MANAGER, MANAGER, INVENTORY_MANAGER, SERVICE_ENGINEER, ACCOUNTANT, INSTALLER and OPERATIONS_MANAGER. Vendor Admin selects a role from the UI; privileged roles are excluded. Fine-grained role/permission administration UI and complete endpoint authorization review remain outstanding. |
| Vendor employee management | Partial | Implemented | Implemented | `POST/GET/PATCH/DELETE /api/v1/users`, role lookup and enable/disable; vendor UI supports create, edit and disable/archive. Invitation/email onboarding and a full role/permission administration console remain absent. |
| Platform/vendor/customer layouts | Partial | Partial | N/A | `frontend/src/main.tsx`; account-type-specific views exist, but they are compact conditional views rather than complete route/layout experiences. |
| Tenant-context enforcement | Partial | Partial | Partial | Tenant ID comes from authenticated user state; JWT filter rechecks account enabled state, tenant status and current role/permission data on every request. Tenant-filtered queries and cross-reference checks cover key sales references; remaining endpoints and tenant-composite FK constraints need broader coverage. |
| Cross-tenant integration tests | Partial | N/A | Partial | `Release12TenantIntegrationTest` exercises HTTP authorization denial, foreign category reference rejection, tenant-filtered product reads, foreign employee assignment rejection and customer registration. H2 is used for the integration profile; update/delete/ID-guessing and MySQL migration integration coverage remain outstanding. |
| Product categories | Implemented | Implemented | Implemented | Tenant categories are seeded for every vendor. Vendor can create, edit and soft-archive categories through `Controller`, `CatalogAdminService`, `Crud` and `RecordEditor`; V5/V6 seed categories and make product category mandatory. |
| Products | Implemented | Implemented | Implemented | Vendor can create, edit and soft-archive products; backend validates same-tenant active category and price/tax. Technical attributes, manufacturer/model, technology, GST, HSN, discount, warranty, image and datasheet are stored. |
| Configurable solar packages | Partial | Implemented | Implemented | UI supports multiple product components, positive quantities and optional components. Backend validates active same-tenant products and persists package items transactionally. Package edit/delete and detailed technical attributes remain absent. |
| Customers | Partial | Implemented | Implemented | Vendor customer create/list/edit/soft-archive UI and backend exist; customer search, duplicate merging and richer profile detail remain outstanding. Customer self-service catalogue/enquiry/quotation/order views exist. |
| Enquiries and leads | Partial | Partial | Implemented | Vendor enquiry capture and customer self-service enquiry submission; one-time conversion creates a customer profile when needed; lead stage/follow-up update and history APIs exist. Search, structured edit forms and lead pipeline board remain absent. |
| Lead assignment | Partial | Partial | Implemented | Same-tenant enabled employee validation, assignment/reassignment persistence and assignment history (`LeadHistory`, V8 migration, GET /leads/{id}/history). UI offers employee choices and a history view; assignment UI still uses a simple prompt. |
| Site surveys | Partial | Partial | Implemented | Survey create/list, same-tenant lead/employee checks and scheduled/completed/cancelled state. Lead dropdown refreshes after enquiry conversion; calendar, attachments/photos, survey edit and detailed completion checklist remain absent. |
| Versioned quotations | Partial | Partial | Implemented | `QuotationRevisionService` clones revision line snapshots; server derives product prices/tax from tenant catalogue; tenant-scoped `GET /quotations/{id}/revisions` and a history action exist. Full quote editing/terms and browser workflow tests remain absent. |
| Discount approval | Partial | Implemented | Implemented | Tenant-specific threshold is stored on Tenant and configurable in Vendor Settings via GET/PATCH /tenant/settings; quotations above it require DISCOUNT_APPROVE. Multi-level approval matrix and approval audit history remain absent. V9 adds the threshold and permission. |
| Quotation PDF generation | Partial | Implemented | N/A | Authenticated `GET /api/v1/quotations/{id}/pdf` includes customer details, quotation revision, line items, quantities, unit prices, tax and totals. Vendor branding, GSTIN, warranty, payment schedule and configurable legal terms remain absent. |
| Customer quotation acceptance/rejection | Implemented | Implemented | Implemented | Customer ownership, SENT state and validity are checked; decision actor/time are persisted by V3 migration against the exact quotation revision. Full browser workflow automation remains outstanding. |
| Idempotent quotation-to-order conversion | Partial | Partial | Implemented | Idempotency key is bound to its quotation, duplicate conversion is checked, unique DB constraints exist and a pessimistic quotation lock serializes concurrent conversion. Dedicated concurrency/retry integration tests remain outstanding. |

## Security hardening committed on this branch

- Added permission checks to vendor list/create workflows and quotation revision creation.
- Added tenant ownership validation for product category references, package components, customer references, lead/survey relationships and assigned vendor employees.
- Prevented converting an enquiry more than once.
- Added catalogue-derived quotation prices and tax instead of trusting browser-supplied price/tax values.
- Added quotation action state checks and approval permission checks.
- Bound idempotency keys to the quotation they originally created and guarded duplicate order creation.
- Added refresh-token reuse detection, refresh-session revocation and token-version-based access-token invalidation on logout/password reset/account disable.
- JWT requests revalidate enabled account, active tenant and current database roles/permissions; suspending a tenant invalidates existing access tokens for that tenant.
- Added platform-only tenant creation that atomically provisions the initial tenant-scoped Vendor Admin, plus tenant activate/suspend controls.
- Added vendor-admin-only employee provisioning and enable/disable for the SALES_EXECUTIVE role, with tenant scoping, password hashing and an additive Flyway permission migration.
- Added customer registration and password-reset forms, plus lead assignment/follow-up and site-survey scheduling fields.
- Added configurable package component selection with per-product quantities.
- Rejected disabled accounts at login and applied DTO validation annotations in authentication controllers.
- Frontend logout now calls the refresh-token logout endpoint before clearing the browser session; the session type includes the optional refresh token returned by the authentication API.
- Added a GitHub Actions workflow for Maven verification, frontend production build and MySQL 8.4 migration validation through V9.
- Added tenant-provisioned employee roles and a role selector; privileged roles are excluded.
- Added idempotent solar category bootstrap and required product-category validation.
- Added customer catalogue browsing, customer quotation requests, own enquiries, quotation decisions and own orders.
- Refreshed vendor lookup dropdown data after create/convert actions so new leads, surveys, products and customers become selectable without reloading the page.
- Added tenant-specific configurable discount approval thresholds and a Vendor Settings screen.
- Added lead assignment audit fields, history API and a UI history view.

These changes are a hardening step, not proof that every Release 1/2 acceptance criterion is complete.

## Build and test instructions

Backend:

```powershell
cd backend
mvn clean verify
```

Frontend:

```powershell
cd frontend
npm install
npm run build
```

Docker:

```powershell
docker compose build --no-cache
docker compose up
```

A GitHub Actions workflow is included at `.github/workflows/release-validation.yml`. It is configured to run backend `mvn clean verify`, frontend `npm run build`, and MySQL 8.4 migration validation through V14. A historical run for commit `a833e33dc036f244e4d7ccf23f99f78222a5a019` passed the earlier backend/frontend/V1–V10 gates; that result does not validate the later Release 3 changes. The newly added direct-order integration test and V14 migration must pass in a fresh run before this branch is considered verified.

## Remaining release blockers

1. Complete authorization coverage for every controller action, including platform tenant operations and customer endpoints.
2. Add vendor employee profile editing and configurable role assignment.
3. Add registration, forgot/reset-password and session-expiry/refresh UX; deliver reset links through a configured email provider.
4. Move refresh tokens from localStorage to HttpOnly Secure SameSite cookies and implement automatic refresh/session-expiry UX; access tokens are currently invalidated globally per user on logout/password reset via token version.
5. Add database-level tenant-composite constraints for all tenant-owned relationships; the V2–V4 migrations are validated against MySQL 8.4 in CI.
6. Add full CRUD and complete workflows for packages, leads, surveys and quotations.
7. Make discount policy configurable and add approval history.
8. Generate complete quotation PDFs from immutable revision snapshots.
9. Persist customer decision actor/time and accepted revision evidence.
10. Make order conversion safe under concurrent requests with transactional locking/unique constraints and tests.
11. Extend integration tests to tenant update/delete and identifier-guessing cases, all four personas, quotation lifecycle and order-concurrency retries.
12. Run and record Maven, frontend, Docker/MySQL migration and end-to-end workflow results before production sign-off.

## Release 3 --- Operations

Release 3 code is present in this branch. The following table describes the implementation inspected and the hardening added in this change. A status of **Implemented (code)** is not a production sign-off: the automated validation gates below still need to pass in the target environment.

| Feature | Backend | Frontend | Database | Evidence / remaining validation |
|---|---|---|---|---|
| Order state machine and history | Implemented (code) | Partial | Implemented | `OperationsService`, `Controller`, `customer_order`, `order_status_history`; validate every transition and role combination in integration tests. |
| Direct customer product order | Added | Added | Existing order/order-item tables plus V14 tax snapshot | `POST /api/v1/customer/product-orders`; customer selects Order now; creates PENDING_VENDOR_ACCEPTANCE order. |
| Vendor order acceptance/rejection | Added | Added | Existing order status/history tables | `POST /api/v1/orders/{id}/vendor-decision`; requires ORDER_CREATE and same-tenant order; decision reason is recorded. |
| Warehouses | Implemented (code) | Partial | Implemented | `GET/POST /api/v1/warehouses`; update/archive service methods exist; UI create/list. |
| Inventory balances and ledger | Implemented (code) | Implemented (code) | Implemented + V14 triggers | Receipts, returns, issues, damage, adjustment, transfer, reservations; V14 rejects UPDATE/DELETE on inventory_transaction. |
| Concurrent stock reservation | Implemented (code) | Implemented (code) | Implemented | Warehouse and balance pessimistic locks; concurrency behavior still requires a MySQL Testcontainers race test. |
| Installation planning and engineer assignment | Implemented (code) | Implemented (code) | Implemented | Same-tenant engineer/team validation; reservation required before schedule. |
| Installation checklist | Implemented (code) | Implemented (code) | Implemented | Mandatory checklist and evidence fields; field execution advances the order to INSTALLATION_IN_PROGRESS. |
| Commissioning | Implemented (code) | Implemented (code) | Implemented | Requires completed mandatory checklist, customer sign-off and commissioning notes. |
| Warranty activation | Implemented (code) | Implemented (code) | Implemented | Commissioning activates warranty; optional package components are excluded from warranty quantity calculation. |
| Detailed solar catalogue | Implemented (code) | Implemented (code) | Implemented | Demo products have technology, HSN, GST, discount, warranty, images, datasheet and JSON technical specifications. Values and generic image assets are illustrative, not manufacturer-certified. |

### Direct customer ordering workflow

1. Customer signs in to the selected vendor store and browses active catalogue products.
2. Product page shows product description, manufacturer/model, technology, technical attributes, HSN, GST, discount, warranty, image and tax-inclusive display price.
3. Customer selects **Order now**. The backend derives price, discount and GST from the tenant's active catalogue; browser-supplied pricing is never accepted.
4. The order is persisted with status `PENDING_VENDOR_ACCEPTANCE` and an order-history event.
5. Vendor users with `ORDER_CREATE` see pending customer orders in Operations → Orders.
6. Vendor accepts (order becomes `CONFIRMED`) or rejects (order becomes `REJECTED`); the reason and actor are retained in order history.
7. Accepted orders enter the existing inventory reservation and installation workflow. Rejected orders cannot reserve stock or be scheduled.
8. Customer can see the resulting order in Customer Portal → My Orders.

### Release 3 migration

Flyway migration `V14__order_tax_snapshot_and_immutable_inventory_ledger.sql` adds an order-line GST snapshot and database triggers that reject inventory transaction updates and deletes. Apply migrations through Flyway; do not manually edit balances or ledger rows.

### Known Release 3 validation limitations

- This repository change has not been executed in a local Maven/Node/MySQL runtime in this session. Do not treat compilation, build, integration, concurrency, browser, or tenant-isolation checks as passed until CI reports green.
- Add Testcontainers coverage for simultaneous reservation requests, missing/foreign tenant identifiers, vendor decision authorization, and direct order pricing/tax snapshots.
- The product demo catalogue uses illustrative sample specifications and generic Unsplash images. Replace them with approved manufacturer datasheets, genuine product photography, confirmed HSN/GST classifications and supplier warranty terms before commercial publication.
- Customer product ordering currently creates a single-product order with quantity one from the catalogue card. Multi-item cart, payment capture and order amendments belong to subsequent workflows/releases.
- Inventory reservation expiry/background reconciliation is not yet implemented. Operations must release abandoned reservations explicitly using the authorized reservation-release endpoint.
- Warranty dates currently begin on commissioning date and use product warranty years (minimum one year); manufacturer-specific warranty terms, registration evidence and pro-rata rules require configuration before production.


---

# Tenant access, settings, registration and solar package images

This section documents the access-control and public storefront changes on `feature/release-3-operations`.

## 403 investigation and root causes

The repository code audit identified the following request-path problems. The original browser/network trace was not included in the issue, so the status explanations below are derived from the checked-in controller/security/service code and are now covered by integration tests.

| Request | Root cause in the previous implementation | Correction |
|---|---|---|
| `POST /api/v1/tenants` | The endpoint used the shared `Models.Input` record, which mixed tenant fields with product, quotation and operations fields. The controller required `accountType=PLATFORM` and `PLATFORM_MANAGE`; a missing global role-permission mapping caused a real 403. A display value such as `Janhvi Soalr` was also not a valid canonical slug and could only fail later as a validation error. | Added a dedicated `TenantCreate` request DTO, explicit `PLATFORM_SUPER_ADMIN` role plus `PLATFORM_MANAGE` permission checks, V16 permission-mapping repair, and canonical slug normalization. Tenant creation still provisions the tenant-scoped Vendor Admin atomically. |
| `GET /api/v1/settings` | The frontend called `/tenant/settings`, while the requested URL was `/settings`. The controller did not expose the latter route. The service intentionally restricts settings to vendor users with `DISCOUNT_POLICY_MANAGE`. | Added `/api/v1/settings` as an alias for the existing tenant settings handler and aligned the frontend to it. Both GET and PATCH remain protected by the existing vendor permission check. |
| `POST /api/v1/auth/register` | Registration was already declared `permitAll()` and CSRF is disabled for this stateless bearer-token API. However, the JWT filter attempted to parse any supplied bearer token and swallowed failures, leaving a request anonymous; the security layer could then report 403 instead of a clear 401. Store lookup accepted only the exact slug, not an exact display name. | Public auth paths now bypass JWT parsing, invalid bearer tokens on protected APIs return 401, and registration resolves a canonical slug or an exact case-insensitive store display name. Unknown/inactive stores return 404; invalid registration fields return 400. |

A 403 is still correct when a valid authenticated account lacks the required role or permission. The implementation does not grant platform privileges to vendor or customer accounts.

## API and role permission matrix

| Endpoint | Platform Super Admin | Vendor Admin | Vendor employee | Customer / public |
|---|---|---|---|---|
| `POST /api/v1/tenants` | Allowed with `PLATFORM_SUPER_ADMIN` and `PLATFORM_MANAGE` | 403 | 403 | 403 |
| `GET /api/v1/settings` | 403 | Allowed with `DISCOUNT_POLICY_MANAGE` | Only if explicitly granted that permission | 403 |
| `PATCH /api/v1/settings/discount-threshold` | 403 | Allowed with `DISCOUNT_POLICY_MANAGE` | Only if explicitly granted that permission | 403 |
| `POST /api/v1/auth/register` | Public route; no JWT required | Public route; no JWT required | Public route; no JWT required | Public route; no JWT required |
| `GET /api/v1/public/{storeSlug}/packages` | Public active-store catalogue | Public active-store catalogue | Public active-store catalogue | Public active-store catalogue |
| `POST /api/v1/public/{storeSlug}/enquiries` | Public enquiry intake | Public enquiry intake | Public enquiry intake | Public enquiry intake |
| `POST /api/v1/packages/{id}/image` | 403 | Allowed with `CATALOG_MANAGE` and same-tenant package | Only if explicitly granted `CATALOG_MANAGE` | 403 |
| `PATCH/DELETE /api/v1/packages/{id}` | 403 | Allowed with `CATALOG_MANAGE` and same-tenant package | Only if explicitly granted `CATALOG_MANAGE` | 403 |

Public registration creates only a `CUSTOMER` account and a tenant-owned customer profile. It cannot create or assign privileged roles.

## Database migration

V16 adds the missing global platform permission mapping and an optional package image URL:

- `V16__repair_platform_permission_and_package_images.sql`
- Inserts `PLATFORM_MANAGE` if it is missing.
- Links it to the global `PLATFORM_SUPER_ADMIN` role idempotently.
- Adds nullable `solar_package.image_url VARCHAR(1024)`.

Existing solar packages remain valid; a null image uses the frontend placeholder. Do not edit previously applied Flyway migrations. Let Flyway apply V16 normally. Back up production before applying schema changes.

## Solar package image storage

Images are uploaded as multipart field `file` to:

`POST /api/v1/packages/{packageId}/image`

The endpoint requires `CATALOG_MANAGE` and checks that the package belongs to the authenticated vendor tenant. Supported formats are JPEG, PNG and WebP; maximum size is 5 MB. MIME type and file signature are checked, and JPEG/PNG image dimensions are validated. The generated UUID filename is stored outside the application artifact.

Configuration:

| Variable | Default | Purpose |
|---|---|---|
| `UPLOAD_DIR` | `./data/uploads` | Filesystem directory used by Spring Boot |
| `PUBLIC_BASE_URL` | empty | Optional public backend base URL prepended to generated image paths |
| `VITE_API_BASE_URL` | empty | Browser API origin; leave empty for same-origin Vite/Nginx proxy |
| `VITE_PUBLIC_STORE_SLUG` | `saumya-solar` | Store slug shown on the public landing page |

Docker Compose mounts the named `solar_uploads` volume at `/var/lib/saumya/uploads`, so rebuilding containers does not remove package images. Do not use `docker compose down -v` if you need to retain this volume.

For a Vercel frontend with a separately hosted Spring Boot API, set `VITE_API_BASE_URL` to the backend origin at frontend build time, set `PUBLIC_BASE_URL` to the same backend origin, and include the exact Vercel origin in backend `CORS_ORIGINS`. Configure a persistent disk at `UPLOAD_DIR` on the backend host; ephemeral container filesystems are not suitable for production uploads. A future object-storage adapter (for example S3) can replace the filesystem provider without changing package image URLs.

## Frontend routing and proxy

- Local Vite development: `frontend/vite.config.ts` proxies both `/api` and `/uploads` to `http://localhost:8080`.
- Docker production frontend: `frontend/nginx.conf` proxies `/api/` and `/uploads/` to the Compose service `backend:8080`.
- React sends the bearer token in `Authorization: Bearer <accessToken>` for protected JSON and multipart calls. It deliberately does not set a JSON content type for multipart uploads.
- A 401 clears the stale browser session and returns the user to sign-in; a 403 is shown as an authorization error. Settings has explicit loading, retry and error states.

## Local setup and verification

Apply the new migration and rebuild without deleting persistent volumes:

```bash
git checkout feature/release-3-operations
git pull origin feature/release-3-operations
docker compose up --build -d
docker compose ps
docker compose logs -f backend
```

Backend tests/build:

```bash
cd backend
mvn clean verify
```

Frontend typecheck/build:

```bash
cd frontend
npm install
npm run build
```

New regression coverage is in `backend/src/test/java/com/saumyasolar/Release4AccessAndPackageTest.java`. It verifies platform-only tenant creation and slug normalization, vendor settings authorization, exact store-name registration and unknown-store 404 behavior, public package listing, package image upload and static image retrieval. The complete CI run must pass before this branch is considered validated.

## Demo users (local seed only)

| Role | Email | Password | Expected access |
|---|---|---|---|
| Platform Super Admin | `platform@saumya.local` | `Platform@123` | Create/list/suspend tenants |
| Vendor Admin | `admin@saumya.local` | `Admin@123` | Vendor catalogue, settings, package image upload and vendor workflows |
| Vendor Sales User | `sales@saumya.local` | `Sales@123` | Sales permissions; no platform tenant creation or vendor settings unless explicitly granted |
| Customer | `customer@saumya.local` | `Customer@123` | Own customer workspace; no vendor settings or package administration |

These are disposable local-only seeded accounts. Do not enable demo seeding or use these credentials in production.


---

# Release 4 — Platform Administration, Finance and Service

This branch is stacked on `feature/release-3-operations`. Requirement-level status and source references are maintained in [RELEASE_4_TRACEABILITY.md](RELEASE_4_TRACEABILITY.md).

## Platform Super Admin dashboard

After platform login, the existing account-type dispatch opens the Platform Dashboard rather than a tenant-creation form. The logo reloads the application root and the authenticated account type dispatches to its own home (Platform, Vendor or Customer); public visitors return to the public landing page.

The platform dashboard includes:
- Total, active, inactive and suspended tenant counters. The current tenant lifecycle persists `ACTIVE` and `SUSPENDED`; inactive is shown as zero unless that state is introduced in a future lifecycle migration.
- Recently created tenants and a searchable, paginated tenant directory.
- Tenant administrator name/email from the tenant's `VENDOR_ADMIN` user role.
- View, edit display name, activate and suspend actions.
- A separate Create Tenant workflow. On success it returns to the dashboard and refreshes the directory.
- CSV, XLSX and PDF downloads use the active search and status filters.

Platform APIs (all require `PLATFORM_SUPER_ADMIN` plus `PLATFORM_MANAGE`):
- `GET /api/v1/platform/tenants?search=&status=ALL&page=0&size=20`
- `GET /api/v1/platform/tenants/{id}`
- `PATCH /api/v1/platform/tenants/{id}` — display name only. Slug and initial administrator identity are immutable through this endpoint.
- `GET /api/v1/platform/tenants/export?search=&status=ALL` (CSV)
- `GET /api/v1/platform/tenants/export.xlsx?search=&status=ALL`
- `GET /api/v1/platform/tenants/export.pdf?search=&status=ALL`
- Existing `POST /api/v1/tenants` and `PATCH /api/v1/tenants/{id}/status` retain their platform-only guards.

## Release 4 database migrations

Flyway applies these forward-only migrations; do not edit previously deployed migrations:

| Migration | Purpose |
|---|---|
| V17 | Tenant created/updated timestamps and status index |
| V18 | Invoice snapshots, fiscal-year invoice sequence, payments, webhook event deduplication, refunds, service requests, visits, SLA policies and service history; GSTIN and reopen policy configuration |
| V19 | `PAYMENT_REFUND` permission for Vendor Admin and Accountant |
| V20 | Invoice CGST/SGST/IGST component amounts and immutable credit-note records |
| V21 | Configurable SLA working-day calendar |
| V22 | Private tenant-authorized service evidence attachments |

Before production migration, take a verified MySQL backup. Existing tenant records receive timestamps from the migration defaults. No table is dropped.

## Finance APIs and workflows

- `GET /api/v1/finance/orders-awaiting-invoice`: finance-authorized eligible order selector.
- `GET/POST /api/v1/invoices`: list invoices or issue an invoice from an eligible confirmed order milestone.
- `GET /api/v1/invoices/{id}/items` and `GET /api/v1/invoices/{id}/pdf`: tenant/customer-authorized immutable invoice snapshots and PDF.
- `POST /api/v1/invoices/{id}/void`: reason-required void; a paid invoice must be refunded first.
- `POST /api/v1/invoices/{id}/credit-notes` and `GET /api/v1/invoices/{id}/credit-notes`: issue/query bounded credit notes.
- `GET/POST /api/v1/payments`: list payments and record offline/manual payment against an invoice. Overpayments are rejected.
- `GET /api/v1/payments/{id}/receipt.pdf`: authorized receipt download.
- `POST /api/v1/payments/{id}/refund` and `GET /api/v1/refunds`: audited manual refunds; `PAYMENT_REFUND` is required.
- `POST /api/v1/payment-webhooks/{provider}`: unauthenticated only at the JWT layer, but HMAC-SHA256 signed with `X-Payment-Signature`. Provider/event ID is unique and duplicate callbacks are idempotently acknowledged.

Invoice numbering uses a transactionally incremented per-tenant fiscal-year sequence (India financial-year boundary: 1 April) and tenant invoice prefix. Invoice items snapshot description, HSN, quantity, unit price, tax rate, taxable amount and tax amount. Tax treatment may be `IGST`, `CGST_SGST` or `UNSPECIFIED`; the PDF carries the GSTIN fields and component totals available in the source records.

**Statutory invoice warning:** this is a GST-ready data model and printable draft invoice, not a legal/tax compliance certification. Confirm vendor/customer GSTIN, place of supply, HSN/SAC, tax rate, invoice series, reverse-charge/e-invoice applicability and tax treatment with the vendor's accountant before statutory production use. The platform does not infer intra-state/inter-state treatment from incomplete address data.


### Private service evidence storage (V22)

- `POST /api/v1/service-requests/{ticketId}/attachments` with multipart field `file`.
- `GET /api/v1/service-requests/{ticketId}/attachments` lists authorized evidence metadata.
- `GET /api/v1/service-requests/{ticketId}/attachments/{attachmentId}` streams the file only after ticket ownership/assignment authorization.
- JPEG, PNG, WebP and PDF are accepted; maximum size is 10 MB.
- Files are stored outside the public `/uploads` resource handler. Docker Compose mounts `solar_private_service_attachments` at `/var/lib/saumya/private-service-attachments`.
- Configure `SERVICE_ATTACHMENT_DIR` to a persistent private disk path in production. Do not map this directory to a public static web server.

## Payment gateway configuration

Set `PAYMENT_WEBHOOK_SECRET` in the backend environment. The webhook signature is the lowercase/uppercase-insensitive hexadecimal HMAC-SHA256 of the exact raw request body. Keep this secret server-side only.

A provider-neutral `PaymentGatewayAdapter` port defines create, webhook verification, status lookup and refund operations. No real gateway was selected in the requirements, so no Razorpay/PayU/Stripe credentials, checkout flow or provider-specific behavior is fabricated. Current usable payments are manual/offline records; gateway webhook processing updates a pre-existing provider payment record and is not a substitute for gateway checkout integration. Gateway refunds remain disabled until a selected provider adapter is configured.

## Service management APIs and workflows

- `GET/POST /api/v1/service-requests`: vendor service workspace list/create and customer-owned self-service create/list.
- `GET /api/v1/service-requests/{id}`: tenant/customer-owned ticket details.
- `POST /api/v1/service-requests/{id}/assign`: Service Manager/Vendor Admin assigns an enabled same-tenant engineer.
- `POST /api/v1/service-requests/{id}/visits`: schedule a future visit for the assigned engineer.
- `POST /api/v1/service-requests/{id}/visits/{visitId}/start` and `/complete`: engineer field execution and resolution.
- `POST /api/v1/service-requests/{id}/confirm`: customer confirmation.
- `POST /api/v1/service-requests/{id}/close`: manager closure after customer confirmation.
- `POST /api/v1/service-requests/{id}/reopen`: customer reopen within the vendor-configured window.
- `GET /api/v1/service-requests/{id}/visits` and `/history`: visit and append-only lifecycle history.
- `GET/POST /api/v1/service-sla-policies`: configure priority response/resolution minutes, IANA timezone, business hours, working weekdays and holiday dates.
- `POST /api/v1/service-requests/{id}/sla/pause` and `/resume`: reason-required SLA pause and deadline adjustment.
- `POST /api/v1/service-settings/reopen-policy`: Vendor Admin configures 0–90 reopen days (zero disables reopen).
- `GET /api/v1/service-customers` and `/service-engineers`: service-permission-scoped tenant lookups.

Service ticket states follow `OPEN → ASSIGNED → VISIT_SCHEDULED → IN_PROGRESS → RESOLVED → CUSTOMER_CONFIRMED → CLOSED`. A customer may reopen a closed ticket only when the tenant has enabled a reopen window. Ticket creation snapshots active warranty status when the product/serial can be matched. Service parts are consumed inside the visit-completion transaction using a row-locked inventory balance and a new immutable `SERVICE_CONSUMPTION` inventory transaction linked to the ticket.

SLA deadlines are calculated from configured timezone, business start/end, working weekdays and holiday dates. If business hours are not configured, the SLA is elapsed 24×7 minutes. A scheduled worker records each response/resolution breach once. Paused intervals extend deadlines.

## Role and endpoint matrix

| Role | Finance | Service |
|---|---|---|
| Platform Super Admin | No vendor invoice/payment data access | No vendor service-ticket access |
| Vendor Admin | Invoice issue/void/credit note, manual payment/refund, reports | Full tenant service administration, assignment, SLA and reopen policy |
| Accountant | Finance operations and refunds when mapped with `FINANCE_MANAGE` / `PAYMENT_REFUND` | No service access unless explicitly granted |
| Service Manager | No finance unless separately granted | Tenant tickets, assignment, visits, closure, SLA and policy |
| Service Engineer / Technician / Installer | No finance unless separately granted | Assigned tickets, visits, field notes, resolution and parts |
| Customer | Own invoices, payments, receipts and credit notes | Own ticket creation, tracking, confirmation and policy-allowed reopen |

All vendor-side operations derive tenant ID from the authenticated principal. Customer reads additionally verify the customer's own profile/order relationship. Platform endpoints never expose vendor finance or service records.

## Local setup and verification

Use the existing setup steps above, then:

```bash
git checkout feature/release-4-finance-and-service
git pull origin feature/release-4-finance-and-service
docker compose up --build -d
```

Backend:
```bash
cd backend
mvn --batch-mode clean verify
```

Frontend:
```bash
cd frontend
npm install
npm run build
```

Configure the backend environment:
- `PAYMENT_WEBHOOK_SECRET`: required before accepting payment provider callbacks.
- `SERVICE_SLA_CHECK_INTERVAL_MS`: optional; defaults to 60000 milliseconds.
- `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ORIGINS`: existing required runtime settings.

The Release 4 CI workflow validates backend tests, frontend build, V1–V21 migrations on MySQL 8.4 and Spring Boot startup/schema validation. See the draft stacked PR for the live run status.

## Known Release 4 limitations / decisions still required

1. **Gateway provider not selected:** provider adapter contract and signed event processing exist; no real hosted checkout, gateway payment creation, settlement reconciliation or gateway refund implementation is enabled.
2. **GST statutory configuration:** GSTIN and tax components are snapshotted, but place-of-supply rules, statutory invoice fields/e-invoice/IRN, reverse charge and legally approved invoice numbering must be confirmed by the business/accountant.
3. **Service attachments:** visit evidence URL is persisted, but a private, tenant-authorized upload/download service for customer photos/documents is not yet implemented. Do not place private service evidence in the public `/uploads` folder.
4. **SLA notifications/escalation:** breach events are persisted and exposed in ticket history; outbound email/SMS/push escalation templates and provider integration remain future work.
5. **SLA calendar policy:** working days, holidays and timezone are configurable; tenant owners must supply their actual calendar. The default weekday set is Monday–Friday only when a business-hour window is configured.
6. **Invoice delivery:** PDFs are downloadable; automated email sending and delivery tracking require an approved mail provider/configuration.
7. **Platform inactive state:** current lifecycle supports ACTIVE/SUSPENDED. Inactive count/filter is retained for reporting compatibility but no separate INACTIVE transition is introduced.
8. **Release 5:** reporting/export center beyond platform tenant exports, audit search UI, dashboards, CMS/blog and notification provider management remain outside this Release 4 branch.

## Public storefront routes and product catalogue

The React public storefront uses the shared navigation/header/footer with separate paths:

- `/` — home
- `/products` — public product catalogue with search, category, brand, capacity and price filters
- `/products/{productId}` — public product details
- `/packages` — active published solar packages
- `/about`, `/mounting-structures`, `/solar-technology`, `/contact` — individual information/contact pages

Public product data is read from `GET /api/v1/public/{storeSlug}/products`. The endpoint resolves only an active tenant store, returns only active products whose category is active in that same tenant, and intentionally omits internal purchase-price and reorder-level fields. It is covered by the public-catalogue integration test. The endpoint is public like the existing public package/enquiry routes; vendor catalogue management APIs remain authenticated and permission-protected.

The Nginx SPA configuration serves `index.html` for unknown frontend paths so direct navigation and refreshes on public routes work. Configure `VITE_PUBLIC_STORE_SLUG` to the intended public store slug and `VITE_API_BASE_URL` when the API is hosted separately.
