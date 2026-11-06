# Saumya Solar Energy Platform — Release 1 & 2

This branch is an incremental hardening and completion branch for the existing Release 1 identity/tenant foundation and Release 2 sales lifecycle. It preserves the existing Spring Boot, MySQL, React, and Docker Compose architecture.

> **Validation honesty:** Commit `7b7610e09eb2ad76948800c54384fd36c6bd8e4a` passed backend Maven tests, frontend production build, and MySQL 8.4 migration validation through V9. The dropdown-refresh frontend change at commit `1c14af5033b91476f7d7cdfbcfbecfa37ae19097` also passed all three CI jobs; the current branch head adds documentation only after that validation. Release 1/2 still have the explicitly listed limitations below; this is not a production-readiness claim.

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
| Vendor employee management | Partial | Partial | Implemented | `POST/GET /api/v1/users`, `GET /api/v1/roles`, `PATCH /api/v1/users/{id}/enabled`; vendor admin can create employees with supported tenant roles and enable/disable same-tenant accounts. Profile editing, role changes after creation and employee invitation/email remain absent. |
| Platform/vendor/customer layouts | Partial | Partial | N/A | `frontend/src/main.tsx`; account-type-specific views exist, but they are compact conditional views rather than complete route/layout experiences. |
| Tenant-context enforcement | Partial | Partial | Partial | Tenant ID comes from authenticated user state; JWT filter rechecks account enabled state, tenant status and current role/permission data on every request. Tenant-filtered queries and cross-reference checks cover key sales references; remaining endpoints and tenant-composite FK constraints need broader coverage. |
| Cross-tenant integration tests | Partial | N/A | Partial | `Release12TenantIntegrationTest` exercises HTTP authorization denial, foreign category reference rejection, tenant-filtered product reads, foreign employee assignment rejection and customer registration. H2 is used for the integration profile; update/delete/ID-guessing and MySQL migration integration coverage remain outstanding. |
| Product categories | Implemented | Implemented | Implemented | Tenant categories are seeded for every vendor: Solar Panels, Inverters, Batteries, Mounting Structures, Cables, AC/DC Distribution Boxes and Solar Accessories. Vendor can list/create categories; edit/delete UI remains absent. V5/V6 seed categories and make product category mandatory. |
| Products | Partial | Implemented | Implemented | Product creation UI requires a category selector; backend requires an active category belonging to the same tenant and validates price/tax. Product update/delete, SKU editing and stock attributes remain absent. |
| Configurable solar packages | Partial | Implemented | Implemented | UI supports multiple product components, positive quantities and optional components. Backend validates active same-tenant products and persists package items transactionally. Package edit/delete and detailed technical attributes remain absent. |
| Customers | Partial | Partial | Implemented | Vendor customer create/list, registration-linked customer profiles and customer catalogue/enquiry/quotation/order views exist. Customer search, edit/detail, duplicate merging and profile management remain absent. |
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

A GitHub Actions workflow is included at `.github/workflows/release-validation.yml`. The run for commit `7b7610e09eb2ad76948800c54384fd36c6bd8e4a` completed successfully for all three jobs: backend `mvn clean verify` (including H2-backed HTTP integration tests for registration, tenant provisioning, employee roles, category/product/package creation, customer enquiry, survey, quotation acceptance and idempotent order conversion), frontend `npm run build`, and V1–V10 migration application/verification on MySQL 8.4. The subsequent lookup-refresh frontend change at commit `1c14af5033b91476f7d7cdfbcfbecfa37ae19097` also passed all three CI jobs.

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
