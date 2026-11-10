# GAP-P2-TEN-001 — Complete tenant-aware database relationship constraints

- **Gap ID:** P2-TEN-001
- **Phase:** Phase 2 — Tenant isolation and data integrity
- **Severity:** Critical
- **Status:** Open

## Requirement reference

- Business requirements §13 — Multi-Tenant Architecture (`tenant_id` on tenant-owned business data and authenticated identity/resource tenant equality).
- SRS §8.1 — Tenant model.
- SRS §8.3 — Database controls (tenant-aware unique constraints and tenant predicates).
- SRS §14.2–14.3 — Database conventions, relationships, constraints and indexes.
- SRS §20.1 — Tenant isolation acceptance criteria.

## Current state and evidence

- The repository contains many tenant-owned entities, tenant-qualified repository methods and existing tenant-aware constraints.
- The existing gap analysis describes composite tenant/branch foreign keys in V37/V43 and later tenant-integrity work in V57/V58, including a MySQL integrity preflight. These claims were treated as hypotheses.
- In this inspection, the exact V57/V58 migration paths and complete migration inventory could not be independently located through the available file-fetch interface. Therefore, the completeness of those constraints is **UNVERIFIABLE**, not assumed absent or complete.
- The SRS requires `tenant_id NOT NULL` on tenant-owned entities and composite constraints where applicable. Single-column references between tenant-owned parents/children can permit inconsistent tenant associations if service validation is bypassed.

## Expected state

- Every tenant-owned table has a non-null `tenant_id`; global reference tables are explicitly classified.
- Every tenant-owned parent/child relationship is protected by a tenant-aware composite FK or a documented exception with equivalent integrity controls.
- Tenant-scoped uniqueness is represented by composite unique keys.
- Migrations are additive, safe for existing data, backfill before NOT NULL/constraint changes, and fail closed on inconsistent records.
- No applied migration is edited.

## Existing code to extend

- Extend the existing Flyway migration chain (including the existing tenant/branch constraints) and the production preflight SQL/workflow.
- Reuse existing `tenant_id` columns, composite unique indexes, V37/V43 patterns and repository/service checks.
- Reuse `TenantRepository`, tenant-scoped domain repositories and the current MySQL 8.4 migration CI job.

## Implementation plan

1. Obtain the exact branch tree/migration inventory and inspect all V1–latest migration SQL before proposing DDL.
2. Generate a schema graph of tenant-owned tables, foreign keys and unique indexes.
3. Classify each table as tenant-owned or global using SRS §8.1.
4. Detect orphaned/cross-tenant relationships against existing data; add preflight checks and remediation instructions.
5. Add only missing composite unique keys/FKs in a new Flyway migration after the actual latest migration version is confirmed.
6. Backfill tenant IDs only where the relationship is unambiguous; otherwise fail migration and require operator remediation.
7. Add database integration assertions that deliberately attempt cross-tenant parent-child references.

## Test plan

- Unit/static: schema inventory verifies every tenant-owned entity/table has tenant ID metadata.
- MySQL integration: apply all migrations from an empty database and an upgraded V1–latest database; validate composite FK and unique-index metadata.
- Negative/security: insert child row with tenant A and parent row from tenant B; assert FK failure; attempt tenant mismatch on update; test tenant-scoped unique collisions.
- Regression: run V57/V58 preflight against realistic migrated fixtures and verify it performs no business-row mutation.

## Acceptance criteria

- All tenant-owned tables are inventoried and tenant_id NOT NULL is proven.
- All applicable cross-tenant relationships are rejected by MySQL constraints.
- Existing production data passes the preflight or has an approved remediation plan.
- Fresh and upgrade migration suites pass on MySQL 8.4.
- No existing migration is edited.

## Status

**Open / Unverifiable** — existing composite constraints are present in selected schema areas, but the complete latest migration and FK inventory could not be independently verified in this audit pass.
