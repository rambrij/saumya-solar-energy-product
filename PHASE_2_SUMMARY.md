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
