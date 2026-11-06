# Saumya Solar Energy Platform — Release 1 & 2

This branch is an incremental hardening and completion branch for the existing Release 1 identity/tenant foundation and Release 2 sales lifecycle. It preserves the existing Spring Boot, MySQL, React, and Docker Compose architecture.

> **Validation honesty:** Source changes are committed, but a successful Maven build, frontend build, database migration run, and end-to-end test run have not yet been observed for this branch. Do not treat any row below as production-ready solely because an entity, API, or screen exists.

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

The Compose file contains development-only database credentials and JWT secret. Replace all of them before any shared or production deployment. Do not expose the Compose MySQL port to an untrusted network.

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
- Customer account: quotations and own orders.

The current frontend is a compact role-aware portal, not a complete set of independently routed production layouts. Hiding a menu is not an authorization control; backend checks are required and remain authoritative.

## Business workflow supported by the current code

1. Platform Admin creates a tenant and its initial Vendor Admin; the vendor creates categories, products, packages, customers and enquiries.
2. Vendor converts an enquiry into a lead, assigns a same-tenant employee, and tracks lead stage/follow-up.
3. Vendor schedules a site survey for a same-tenant lead.
4. Vendor creates a quotation with a customer, optional lead/survey and catalogue line references.
5. Discounts above the current 10% threshold enter pending approval; an authorized user approves or rejects.
6. Vendor sends a draft quotation.
7. The customer can accept or reject a quotation visible in their tenant/customer scope.
8. Vendor converts an accepted quotation to an order with an `Idempotency-Key`.

The 10% threshold is currently hard-coded. A configurable discount policy is still required before production.

## Implementation matrix

Statuses describe the code currently present in this branch, not an assumption based on the release plan. "Partial" means at least one part exists but the complete workflow/acceptance criteria are not met. "Missing" means no complete implementation was found. Database status covers the current Flyway schema only.

| Feature | Backend | Frontend | Database | Issues / evidence |
|---|---|---|---|---|
| Tenant and vendor management | Partial | Partial | Implemented | `Tenant`, `TenantRepository`, `POST/GET /api/v1/tenants`; platform permission required; tenant creation transaction provisions an initial tenant-scoped VENDOR_ADMIN and platform can activate/suspend tenants. Full tenant editing and broader management UI remain absent. |
| Customer registration | Implemented | Partial | Implemented | `POST /api/v1/auth/register`, `AuthLifecycleService.register`, customer registration form; email verification and duplicate profile recovery are absent. |
| Access and refresh tokens | Partial | Partial | Implemented | `JwtService`, `JwtFilter`, `AuthLifecycleService`, `refresh_token`; refresh rotates and reuse revokes remaining refresh sessions. Every request reloads enabled user, tenant status and current role/permission data; individual access-token logout revocation and refresh UX remain outstanding. |
| Logout and token revocation | Partial | Partial | Implemented | `POST /api/v1/auth/logout`; frontend now calls it before clearing local session. Existing access JWT remains usable until expiry. |
| Password reset | Partial | Partial | Implemented | `PasswordResetToken`, forgot/reset endpoints and UI; single-use and expiry checks, reset revokes refresh sessions. Email delivery is absent; development reset token is returned by API. |
| Roles and permissions | Partial | Partial | Implemented | `Role`, `Permission`, `SecurityConfig`, `AppService`; permission checks added to several vendor operations. Not all endpoints/actions are consistently permission-guarded; role administration is absent. |
| Vendor employee management | Partial | Partial | Implemented | `POST/GET /api/v1/users` and `PATCH /api/v1/users/{id}/enabled`; vendor admin can provision, list and enable/disable same-tenant SALES_EXECUTIVE accounts. Edit and broader configurable role assignment remain absent. `V2__vendor_employee_management_permission.sql` grants USER_MANAGE to existing vendor admin roles. |
| Platform/vendor/customer layouts | Partial | Partial | N/A | `frontend/src/main.tsx`; account-type-specific views exist, but they are compact conditional views rather than complete route/layout experiences. |
| Tenant-context enforcement | Partial | Partial | Partial | Tenant ID comes from authenticated user state; JWT filter rechecks account enabled state, tenant status and current role/permission data on every request. Tenant-filtered queries and cross-reference checks cover key sales references; remaining endpoints and tenant-composite FK constraints need broader coverage. |
| Cross-tenant integration tests | Partial | N/A | Partial | `Release12TenantIntegrationTest` exercises HTTP authorization denial, foreign category reference rejection, tenant-filtered product reads, foreign employee assignment rejection and customer registration. H2 is used for the integration profile; update/delete/ID-guessing and MySQL migration integration coverage remain outstanding. |
| Product categories | Partial | Partial | Implemented | Entity/repository, tenant-filtered list and create form. Update/delete, uniqueness/validation UX and lifecycle screens absent. |
| Products | Partial | Partial | Implemented | Entity/repository, category tenant check, create/list. No full update/delete UI; input validation and lifecycle controls incomplete. |
| Configurable solar packages | Partial | Partial | Implemented | Package and package-item entities; API validates same-tenant active products and stores optional-item metadata. UI configures multiple product components and quantities; package edit/delete and optional-component editing remain absent. |
| Customers | Partial | Partial | Implemented | Customer create/list and customer profiles exist. Search, update, detail, duplicate handling and complete customer self-service onboarding are absent. |
| Enquiries and leads | Partial | Partial | Implemented | Enquiry create/list, one-time conversion, lead update/history. UI stage editing is prompt-based; search, full assignment form and history view are absent. |
| Lead assignment | Partial | Partial | Implemented | Backend validates assigned employee belongs to tenant; UI does not provide a complete assignment/reassignment workflow or history screen. |
| Site surveys | Partial | Partial | Implemented | Survey create/list and same-tenant lead/employee checks. Full edit, scheduling calendar, evidence/attachments and detailed completion workflow absent. |
| Versioned quotations | Partial | Partial | Implemented | `QuotationRevisionService` clones revision line snapshots; server derives product prices/tax from tenant catalogue; tenant-scoped `GET /quotations/{id}/revisions` and a history action exist. Full quote editing/terms and browser workflow tests remain absent. |
| Discount approval | Partial | Partial | Implemented | Hard-coded >10% threshold and approval permission check. Configurable threshold/authority matrix and complete approval history are absent. |
| Quotation PDF generation | Partial | Partial | N/A | Authenticated `GET /api/v1/quotations/{id}/pdf`; current PDF only contains quotation number, revision, status and total, not a complete customer-facing line-item/tax/terms document. |
| Customer quotation acceptance/rejection | Implemented | Implemented | Implemented | Customer ownership, SENT state and validity are checked; decision actor/time are persisted by V3 migration against the exact quotation revision. Full browser workflow automation remains outstanding. |
| Idempotent quotation-to-order conversion | Partial | Partial | Implemented | Idempotency key is bound to its quotation, duplicate conversion is checked, unique DB constraints exist and a pessimistic quotation lock serializes concurrent conversion. Dedicated concurrency/retry integration tests remain outstanding. |

## Security hardening committed on this branch

- Added permission checks to vendor list/create workflows and quotation revision creation.
- Added tenant ownership validation for product category references, package components, customer references, lead/survey relationships and assigned vendor employees.
- Prevented converting an enquiry more than once.
- Added catalogue-derived quotation prices and tax instead of trusting browser-supplied price/tax values.
- Added quotation action state checks and approval permission checks.
- Bound idempotency keys to the quotation they originally created and guarded duplicate order creation.
- Added refresh-token reuse detection and refresh-session revocation after password reset.
- JWT requests revalidate enabled account, active tenant and current database roles/permissions; suspending a tenant invalidates existing access tokens for that tenant.
- Added platform-only tenant creation that atomically provisions the initial tenant-scoped Vendor Admin, plus tenant activate/suspend controls.
- Added vendor-admin-only employee provisioning and enable/disable for the SALES_EXECUTIVE role, with tenant scoping, password hashing and an additive Flyway permission migration.
- Added customer registration and password-reset forms, plus lead assignment/follow-up and site-survey scheduling fields.
- Added configurable package component selection with per-product quantities.
- Rejected disabled accounts at login and applied DTO validation annotations in authentication controllers.
- Frontend logout now calls the refresh-token logout endpoint before clearing the browser session; the session type includes the optional refresh token returned by the authentication API.
- Added a GitHub Actions workflow for Maven verification and frontend production build.

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

A GitHub Actions workflow is included at `.github/workflows/release-validation.yml`. The run for commit `06e6c142ca4bd2b52a7ce2037eeb1a0dba579421` completed successfully for backend `mvn clean verify` (including H2-backed HTTP integration tests) and frontend `npm run build`. Subsequent JWT database revalidation changes are being revalidated by the workflow on the current branch head. A successful compile/build does not replace API integration, tenant-isolation, concurrency or full workflow tests.

## Remaining release blockers

1. Complete authorization coverage for every controller action, including platform tenant operations and customer endpoints.
2. Add vendor employee profile editing and configurable role assignment.
3. Add registration, forgot/reset-password and session-expiry/refresh UX; deliver reset links through a configured email provider.
4. Revoke or otherwise invalidate access tokens on logout/password reset; consider HttpOnly secure cookies for refresh tokens instead of localStorage.
5. Add database-level tenant-composite constraints for all tenant-owned relationships.
6. Add full CRUD and complete workflows for packages, leads, surveys and quotations.
7. Make discount policy configurable and add approval history.
8. Generate complete quotation PDFs from immutable revision snapshots.
9. Persist customer decision actor/time and accepted revision evidence.
10. Make order conversion safe under concurrent requests with transactional locking/unique constraints and tests.
11. Extend integration tests to tenant update/delete and identifier-guessing cases, all four personas, quotation lifecycle and order-concurrency retries.
12. Run and record Maven, frontend, Docker/MySQL migration and end-to-end workflow results before production sign-off.
