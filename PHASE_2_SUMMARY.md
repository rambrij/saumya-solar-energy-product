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
- Fixed two pre-existing CI failures discovered at baseline: role-escalation audit events now survive the expected `ResponseStatusException` rollback while retaining the caller transaction for actor foreign-key integrity, and the responsive navigation test selects among duplicate mobile/desktop links.
- Added Flyway V57 with fail-closed preflight checks and composite tenant foreign keys for:
  - `product(tenant_id, category_id)` → `product_category(tenant_id, id)`
  - `package_item(tenant_id, package_id)` → `solar_package(tenant_id, id)`
  - `package_item(tenant_id, product_id)` → `product(tenant_id, id)`
- Extended the MySQL 8.4 workflow to apply/assert V57 and enabled CI for this branch.
- Updated `IMPLEMENTATION_GAP_ANALYSIS.md` with a Phase 2 audit table, implementation evidence, deferred gaps, conflicts, verification baseline, migration notes and open questions.

## Not yet complete

- Exhaustive negative tests for every endpoint and resource (list/get/create/update/delete/export/download).
- Full customer-to-customer and branch-to-branch IDOR matrix.
- Composite tenant-aware FKs for all remaining tenant-owned relationships across the schema.
- DB-backed multi-tenant scheduler tests for SLA, subscription expiry, warranty expiry and integration sync.
- Production-snapshot execution of V57's integrity preflight.
- Runtime verification of frontend filters, branch access, exports and binary downloads across tenants.

These items are explicitly classified as partial/deferred in the gap analysis. The current changes do not justify a claim that every cross-tenant path has been proven closed. The endpoint inventory lists 222 HTTP mappings and the repository inventory lists all 69 repository interfaces; both are marked Partial pending per-resource negative tests.

## Baseline and CI

The latest base-branch CI observed before Phase 2 changes (commit `295e284900d7aa7f85dc002f8aa2f905d4ec36df`) had:
- Backend: 128 tests, 1 pre-existing failure (blocked custom-role permission escalation audit assertion).
- Frontend: 22 tests, 1 pre-existing failure (duplicate responsive navigation link queried with `getByRole`).
- MySQL migration validation and Spring Boot startup: passed.

A local clone/build was not possible because this execution environment could not resolve github.com. The MySQL 8.4 migration job passed V57 and its catalogue cross-tenant rejection assertions on commit `8aa08480d7195da7095ea9054a11132eabbdff13`. Full GitHub Actions run `42773745740` on commit `0d41b66e4a25f048bbe74c67bc62a9816f1c35b5` passed all jobs: backend 132 tests / 0 failures / 0 errors; frontend 22 tests / 0 failures plus production build; MySQL 8.4 V1–V57 migration and cross-tenant FK rejection assertions; Spring Boot startup against MySQL 8.4 and Mailpit. The latest changes after that tested code commit are documentation/workflow-name only. Do not treat queued/in-progress CI as a pass.

## Migration / rollback

V57 is additive and ordered after V56. It checks for tenant-mismatched/orphaned catalogue relationships and null package-item tenant IDs before adding constraints. If preflight fails, fix the underlying data and retry; do not bypass it.

Flyway Community does not automatically reverse applied versioned migrations. A rollback must be a reviewed forward migration dropping the three new foreign keys and unique indexes. Restoring nullable `package_item.tenant_id` is not recommended after application code depends on it being non-null.

## Open Questions

1. Should all remaining legacy tenant-owned single-column FKs be included in this Phase 2 acceptance, or delivered in a separately approved Phase 2.1 migration set?
2. Should branch-unassigned legacy records remain visible to all branch memberships, as current behavior permits?
3. Which frontend browser versions are supported? This affects UX compatibility, not server-side authorization.
