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
- Added V58 with 88 composite tenant-aware foreign keys across sales, surveys, quotations, orders, inventory, installations, warranties, invoices, payments/refunds, service, private evidence, offline sync, integrations, branch memberships, engineer profiles, approval actors, audit branches and tenant notifications. V58 includes fail-closed orphan/cross-tenant preflight checks and composite lookup indexes.
- Added `tenant-isolation-v58.sql` and wired V58 plus V57/V58 cross-tenant assertions into MySQL 8.4 CI.
- Expanded endpoint negative tests for customer A/B addresses, orders, invoices and service tickets; foreign branch membership/analytics/update/deactivate/assignment; foreign survey/service-attachment parents; report CSV scope and platform-export denial.
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
- V58 covers 88 tenant-aware foreign keys. Production rollout still requires running its fail-closed data-integrity preflight against a production snapshot and resolving any mismatched rows.
- DB-backed multi-tenant concurrency tests for SLA, subscription expiry, warranty expiry and integration sync remain.
- Async/event listeners and future queue consumers must establish and clear `TenantExecutionContext` explicitly.
- Browser-level tests for client filter tampering and 403/404 UX remain.

The implementation materially expands tenant isolation, but these remaining cases prevent a blanket claim that every cross-tenant path is closed. The endpoint inventory and per-family coverage matrix in `IMPLEMENTATION_GAP_ANALYSIS.md` distinguish tested paths from deferred ones.

## Baseline and CI

The latest base-branch CI observed before Phase 2 changes (commit `295e284900d7aa7f85dc002f8aa2f905d4ec36df`) had:
- Backend: 128 tests, 1 pre-existing failure (blocked custom-role permission escalation audit assertion).
- Frontend: 22 tests, 1 pre-existing failure (duplicate responsive navigation link queried with `getByRole`).
- MySQL migration validation and Spring Boot startup: passed.

A local clone/build was not possible because this execution environment could not resolve github.com. Full GitHub Actions run `42779608893` on commit `969ec08c171861b7929086b4e73d8cbf269d9387` passed all jobs: backend 136 tests / 0 failures / 0 errors; frontend 22 tests / 0 failures plus production build; MySQL 8.4 V1–V58 migration plus V57/V58 cross-tenant FK rejection assertions; Spring Boot startup against MySQL 8.4 and Mailpit. Later commits add CSV export assertions and audit documentation; the latest run for those changes must pass before merge. Do not treat queued/in-progress CI as a pass.

## Migration / rollback

V57 and V58 are additive and ordered after V56. V57 checks catalogue relationships; V58 checks tenant-owned sales, operational, finance, service, evidence, offline, integration and actor relationships before adding 88 composite foreign keys. If any preflight fails, fix the underlying data and retry; do not bypass it. CI migration checks use an isolated MySQL 8.4 database, not a production snapshot.

Flyway Community does not automatically reverse applied versioned migrations. Rollback must be a reviewed forward migration: drop the V58 `fk_*_tenant` constraints and V58 `uq_*_tenant_id` unique keys, then the three V57 foreign keys and their referenced unique keys if rollback of V57 is also required. Preserve pre-existing V37/V43/V49 constraints. Do not restore nullable tenant columns unless a separately reviewed application/schema contract requires it.

## Open Questions

1. Should remaining global/platform identity relationships and non-tenant resource references be brought into Phase 2.1, or are the 88 V58 tenant-owned links the accepted DB scope?
2. Should branch-unassigned legacy records remain visible to all branch memberships, as current behavior permits?
3. Production database preflight has not been run. Who will approve the remediation window if V58 detects legacy tenant mismatches?
