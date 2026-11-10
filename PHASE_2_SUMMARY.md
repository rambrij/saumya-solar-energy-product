# Phase 2 — Tenant Isolation and Data Integrity Summary

**Repository:** rambrij/saumya-solar-energy-product  
**Branch:** `audit/phase-2-implementation-gap-analysis`  
**Base:** `audit/implementation-gap-analysis`  
**Date:** 2026-11-10

## Delivered

- Removed the global SLA scan. SLA evaluation now requires a tenant ID and queries only that tenant's service requests.
- Subscription expiry now enumerates tenants and uses a tenant-qualified expiry query.
- Warranty expiry/reminders now enumerate tenants and use tenant-qualified repository methods.
- Integration sync discovery, atomic job claim and reload are tenant-qualified.
- Added `TenantExecutionContext`, a ThreadLocal scope restored/cleared with try-with-resources. SLA processing additionally rejects a missing or mismatched tenant scope.
- Added scheduler/context tests and MySQL SQL assertions that verify V57 rejects cross-tenant category, package and product references.
- Added V58 with 89 composite tenant-aware foreign keys across sales, surveys, quotations, orders, inventory, installations, warranties, invoices, payments/refunds, service, private evidence, offline sync, integrations, branch memberships, engineer profiles, approval actors, audit branches and tenant notifications. V58 includes fail-closed orphan/cross-tenant preflight checks and composite lookup indexes.
- Added `tenant-isolation-v58.sql` and wired V58 plus V57/V58 cross-tenant assertions into MySQL 8.4 CI.
- Expanded endpoint negative tests for customer A/B addresses, orders, invoices and service tickets; generic vendor/customer list scoping across 13 routes plus cross-tenant catalogue update/delete and customer order-create checks; foreign branch membership/analytics/update/deactivate/assignment; foreign survey/service-attachment parents; report CSV scope and platform-export denial.
- Added `BRANCH_MANAGE` to the runtime `DataSeeder` permission catalogue so the seeded vendor-admin permissions align with V51's migrated role grants.
- Fixed two pre-existing CI failures discovered at baseline: role-escalation audit events now survive the expected `ResponseStatusException` rollback while retaining the caller transaction for actor foreign-key integrity, and the responsive navigation test selects among duplicate mobile/desktop links.
- Added Flyway V57 with fail-closed preflight checks and composite tenant foreign keys for:
  - `product(tenant_id, category_id)` → `product_category(tenant_id, id)`
  - `package_item(tenant_id, package_id)` → `solar_package(tenant_id, id)`
  - `package_item(tenant_id, product_id)` → `product(tenant_id, id)`
- Extended the MySQL 8.4 workflow to apply/assert V57 and enabled CI for this branch.
- Updated `IMPLEMENTATION_GAP_ANALYSIS.md` with a Phase 2 audit table, implementation evidence, deferred gaps, conflicts, verification baseline, migration notes and open questions.

## Not yet complete

- The 222-mapping controller inventory is documented, but the automated negative matrix does not yet exercise every mapping and every verb. Remaining cases include full CRUD variants for each domain resource, platform lifecycle endpoints, warranty-claim attachment downloads, direct attachment-ID tampering, and XLSX/PDF report content assertions.
- Full branch-assigned record filtering for all record families, including the policy for records with `branch_id IS NULL`, remains to be verified.
- V58 covers 89 tenant-aware foreign keys. Production rollout still requires running its fail-closed data-integrity preflight against a production snapshot and resolving any mismatched rows.
- DB-backed multi-tenant concurrency tests for SLA, subscription expiry, warranty expiry and integration sync remain.
- Async/event listeners and future queue consumers must establish and clear `TenantExecutionContext` explicitly.
- Browser-level tests for client filter tampering and 403/404 UX remain.

The implementation materially expands tenant isolation, but these remaining cases prevent a blanket claim that every cross-tenant path is closed. The endpoint inventory and per-family coverage matrix in `IMPLEMENTATION_GAP_ANALYSIS.md` distinguish tested paths from deferred ones.

## Baseline and CI

The latest base-branch CI observed before Phase 2 changes (commit `295e284900d7aa7f85dc002f8aa2f905d4ec36df`) had:
- Backend: 128 tests, 1 pre-existing failure (blocked custom-role permission escalation audit assertion).
- Frontend: 22 tests, 1 pre-existing failure (duplicate responsive navigation link queried with `getByRole`).
- MySQL migration validation and Spring Boot startup: passed.

A local clone/build was not possible because this execution environment could not resolve github.com. Full GitHub Actions run `42780928514` on commit `862198c5f40baa86ca69629520d108b561384ea3` passed all jobs: backend 137 tests / 0 failures / 0 errors; frontend 22 tests / 0 failures plus production build; MySQL 8.4 V1–V58 migration plus V57/V58 cross-tenant FK rejection assertions (including customer-profile/user mismatch); Spring Boot startup against MySQL 8.4 and Mailpit. Subsequent commits update documentation only.

## Migration / rollback

V57 and V58 are additive and ordered after V56. V57 checks catalogue relationships; V58 checks tenant-owned sales, operational, finance, service, evidence, offline, integration and actor relationships before adding 89 composite foreign keys. If any preflight fails, fix the underlying data and retry; do not bypass it. CI migration checks use an isolated MySQL 8.4 database, not a production snapshot.

Flyway Community does not automatically reverse applied versioned migrations. Rollback must be a reviewed forward migration: drop the V58 `fk_*_tenant` constraints and V58 `uq_*_tenant_id` unique keys, then the three V57 foreign keys and their referenced unique keys if rollback of V57 is also required. Preserve pre-existing V37/V43/V49 constraints. Do not restore nullable tenant columns unless a separately reviewed application/schema contract requires it.

## Open Questions

1. Should remaining global/platform identity relationships and non-tenant resource references be brought into Phase 2.1, or are the 89 V58 tenant-owned links the accepted DB scope?
2. Should branch-unassigned legacy records remain visible to all branch memberships, as current behavior permits?
3. Production database preflight has not been run. Who will approve the remediation window if V58 detects legacy tenant mismatches?


## Production approval continuation — 2026-11-10

### Additional changes committed

- Added warranty-claim attachment service tests for foreign-tenant claim ID guessing, cross-customer claim access, attachment-ID/parent-claim mismatch, and successful authorized download.
- Added warranty claim lifecycle integration coverage: customer A cannot list or create claims against customer B/another tenant's warranty; vendor A cannot list or approve a foreign-tenant claim.
- Added operations export integration tests for CSV, XLSX and PDF. The test creates a foreign-tenant high-value order and verifies authenticated tenant order aggregates and generated export contents exclude it. XLSX is parsed with Apache POI; PDF text is extracted with OpenPDF.
- Added branch filter tests for assigned branch, other branch, tenant mismatch, vendor-admin bypass and the existing null-branch legacy visibility behavior.
- Added H2 database-backed integration tests: concurrent queue workers race for one integration sync job (exactly one claim succeeds); cross-tenant claim attempts fail; subscription expiry updates expired rows only within each owning tenant.
- Added Playwright/Chromium browser-origin tests that forge tenant/customer/branch query filters against seven list endpoints and assert that returned rows remain scoped to the authenticated principal.
- Added a manual, protected production preflight workflow and a SQL script that executes V57/V58 integrity checks only. It does not apply FK/index DDL or alter business data.

### Still outstanding

- Full CRUD negative tests for every resource family and every supported HTTP verb are not complete. The controller inventory contains 222 mappings; newly added tests cover selected high-risk routes, not every route.
- Warranty attachment upload/download still needs a controller-level multipart test with a real private storage directory and actual HTTP response headers.
- Branch filtering needs endpoint-level integration tests for each branch-bearing resource family, not only service-level filter tests.
- Browser tests currently exercise real browser-origin API requests, not the complete React filter/dropdown/route UX with a second tenant's seeded resources. Add full UI interaction and 403/404 behavior coverage.
- Database-backed concurrency tests use H2; run queue claim and scheduler concurrency scenarios on MySQL 8.4. SLA and warranty expiry database-backed multi-tenant tests remain.
- Production preflight is not executed. Configure the protected GitHub production environment with PROD_DB_HOST, PROD_DB_PORT, PROD_DB_NAME, PROD_DB_USER, and PROD_DB_PASSWORD, then manually run .github/workflows/phase-2-production-preflight.yml. The workflow executes the preflight procedures only, not the V57/V58 constraints.

### CI and production decision

Intermediate CI runs caught an OpenPDF text extractor compile error; the test was updated to use the OpenPDF instance API. The final verification status must be taken from GitHub Actions for the latest branch HEAD, not an earlier successful commit. Until the final backend/frontend/MySQL/Playwright jobs pass and the protected production preflight reports PASS, this branch is **not approved for production**.


### Latest verification evidence

- GitHub Actions run **42785193481** for code commit **2eaddfc6c0b35459190ddc392dea12e022b886c3**: backend **149 tests / 0 failures / 0 errors**; frontend typecheck and production build **passed**; MySQL 8.4 V1–V58 migration validation **passed**. The MySQL application-startup job, Chromium installation and Playwright browser test also passed; all four jobs succeeded.
- The browser job itself completed successfully in run **42784999436** on commit 084782c8bda9edf5ae88e320b44abe4b322cb35a: Spring Boot started against MySQL 8.4, Chromium installed, and the Playwright tenant-filter test passed. That run's backend suite had one warranty-claim fixture assertion failure; the fixture was corrected in 2eaddfc, and the latest backend suite now passes all 149 tests.
- The 149-test suite includes the new warranty claim lifecycle, attachment service, CSV/XLSX/PDF export, branch-filter and H2 database concurrency tests.
- Production V57/V58 preflight remains unexecuted because no production DB connection/secrets were available. No production rows were read or modified.


## Further release-blocker continuation — 2026-11-10

### Additional branch isolation changes

- Added shared branch membership filtering to vendor invoice lists, service ticket lists and installation job lists. The filter is applied after existing role/assignment restrictions, so branch membership cannot broaden a user's existing scope.
- Extended the branch integration fixture to cover branch A vs branch B for enquiries, leads, site surveys, quotations, orders, invoices, service requests, installation jobs, warehouses, inventory balances and inventory ledger rows.
- Added branch analytics authorization assertions: the assigned branch is readable and a same-tenant but unassigned branch returns 404.
- Branch test fixtures now run transactionally so temporary employee/role/branch data does not leak into other integration tests.

### Endpoint security matrix

- Generated `PHASE_2_ENDPOINT_MATRIX.md` from the 17 Spring MVC controller classes: 222 method-mapping declarations and 224 expanded route patterns (multi-path aliases included).
- `EndpointAuthenticationMatrixTest` enumerates the runtime handler mapping and requires authentication for every non-public HTTP handler. It does not falsely claim that an authentication check proves endpoint-specific tenant CRUD isolation.
- Endpoint-specific negative CRUD coverage remains incomplete and is itemized in the generated matrix.

### Concurrent MySQL scheduler coverage

- The MySQL 8.4 CI job now runs the integration sync, subscription expiry, SLA breach and warranty expiry concurrency tests against a separate migrated database.
- Test fixtures now include tenant-owned integration connections, plans, customer profiles, orders, installation jobs and products to satisfy the V57/V58 composite relationships.
- SLA evaluation uses a tenant-scoped pessimistic write query to prevent two concurrent scheduler workers from emitting duplicate SLA breach transitions for the same service request.
- Run 42789991955 reported backend 154 tests passing and its dedicated MySQL scheduler step passing. Its separate browser step failed before the React login interaction stabilized; the UI test was adjusted and the latest-head browser result remains pending.

### React two-tenant UI test

- Added `frontend/e2e/tenant-react-ui.spec.ts`: creates a second tenant through the platform API, creates a tenant-B-only category/product, signs into tenant A using the real React login and Catalog > Products UI, verifies the foreign product is absent, and checks foreign product update/delete IDOR responses. It then signs into tenant B through React and verifies its own product is visible.
- The test runs against the actual Vite frontend proxy and Spring Boot/MySQL application. It is not yet a complete browser workflow for every resource family or every filter/dropdown.

### Production preflight and remediation evidence

- Added `PHASE_2_PRODUCTION_PREFLIGHT_RUNBOOK.md` with backup/change-window, privilege, dispatch, artifact, failure and remediation-approval requirements.
- The protected preflight workflow now retains its output artifact even on failure.
- **Not executed against production:** no protected database credentials or production reviewer approval were available in this session. No production data was read or modified. MySQL CI is not a substitute for production preflight.

### Current decision

The new branch filters, controller-level warranty attachment test, MySQL scheduler suite and two-tenant browser test have been added. Do not approve production until the latest-head CI run is fully green, the endpoint-specific CRUD matrix is closed or formally accepted, and production V57/V58 preflight has two PASS markers plus recorded DBA/data-owner approval.


### Latest CI status and browser test diagnosis

- Run **42793775946**: backend **154 tests, 0 failures/errors**; frontend typecheck/build and MySQL V1–V58 migration validation passed. Its MySQL application/browser job was still running when this note was written.
- The MySQL 8.4 job has successfully executed the SLA, warranty, integration and subscription scheduler tests, and the V57/V58 preflight against the migrated CI database in earlier completed runs.
- The API-origin Playwright filter-tampering test passes. The new React UI test exposed that the sign-in form submission did not produce the expected login response in Chromium. It has been changed to use the form's native `requestSubmit()`, resets stale cookies/CSRF state between fixture setup and UI login, and forces the Vite API base to same-origin proxy mode. The run for this latest browser change is pending; do not mark the React UI test passed until its Actions job succeeds.
- Run **42793418195**: the updated V57/V58 CI preflight, including the failure-cleanup trap for its named stored procedures, completed successfully against the MySQL CI database. This is not a production database execution.
