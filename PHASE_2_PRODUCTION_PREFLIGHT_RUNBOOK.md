# Phase 2 production preflight and remediation runbook

## Purpose

Run the V57/V58 relationship-integrity and V60 tenant_id NOT NULL preflight against the production MySQL database before applying the tenant-integrity migrations. V60 tightens nine tenant-owned tables only after this preflight proves there are no NULL tenant IDs. The SQL checks legacy tenant-owned relationships for orphaned and cross-tenant references. It fails closed on the first detected violation.

## Important safety properties

- The V57/V58/V60 integrity script does not INSERT, UPDATE or DELETE business rows. It creates and drops two named stored procedures to express fail-fast checks. Its execution identity therefore needs CREATE ROUTINE and DROP ROUTINE privileges in addition to SELECT access. Both production and CI workflows install an EXIT cleanup trap so the named procedures are removed even when a SIGNAL aborts the SQL script. Review that temporary DDL privilege with the DBA before execution.
- The separate docs/PHASE_1_2_TENANT_SCHEMA_AUDIT.sql script uses INFORMATION_SCHEMA, SELECT and prepared SELECT statements only. It runs with a distinct SELECT-only account and returns exact tenant_id NULL counts, nullability, FK inventory, unique-index inventory and tables without tenant_id.
- It does not apply V57/V58 indexes, foreign keys or V60 NOT NULL constraints.
- It does not repair rows automatically.
- Do not run V57/V58/V60 migrations after a failed preflight.

## Disposable CI preflight (no production credentials)

The branch's .github/workflows/release-validation.yml already provisions an isolated MySQL 8.4 service for CI. It applies migrations V1-V58, starts the application against a separate disposable MySQL database, then executes the same backend/src/test/resources/tenant-isolation-production-preflight.sql checks. The test service uses the intentionally dummy CI-only credentials root / rootpass; the database is created fresh for the workflow and destroyed with the runner. The CI job also uses a test-only JWT secret and test fixtures. These values must never be copied into a production environment.

Use the CI run to validate SQL syntax, migration compatibility and the preflight's behavior against a clean schema. It cannot establish that production legacy rows are clean, because the disposable schema has no production data. A separate production preflight and DBA/release-owner sign-off remain mandatory before V57/V58/V60 rollout.

## Development-credential CI smoke preflight

For this repository's non-production validation, `.github/workflows/release-validation.yml` provisions a fresh disposable MySQL 8.4 database using the same local development settings as `docker-compose.yml`: database `solar`, application user `solar`, password `solarpass`. The application starts with this least-purpose application account and the V57/V58 relationship-integrity preflight is executed against that migrated CI database. The runner is ephemeral; these values are test fixtures only. They are not production credentials and must never be copied into the protected `production` GitHub environment. The separate `phase-2-production-preflight.yml` remains gated on real, protected production secrets and reviewer approval.

## Execution prerequisites

1. Confirm a current production backup and an approved change window.
2. The workflow `.github/workflows/phase-2-production-preflight.yml` uses the protected GitHub environment named `production`.
3. Configure these environment secrets: `PROD_DB_HOST`, `PROD_DB_PORT`, `PROD_DB_NAME`, `PROD_DB_USER`, `PROD_DB_PASSWORD`, `PROD_DB_AUDIT_USER`, `PROD_DB_AUDIT_PASSWORD`.
4. Grant the V57/V58 execution identity the reviewed temporary CREATE ROUTINE / DROP ROUTINE / SELECT privileges. Grant the separate audit identity SELECT-only access to the target schema and INFORMATION_SCHEMA.
5. Restrict each database identity to the minimum required schema privileges. Confirm the procedure create/drop privileges are permitted by the DBA.
6. The workflow file must exist on the repository default branch before GitHub enables workflow_dispatch. The Phase 2 branch intentionally does not change the default/base branch. Merge the workflow file only after the normal PR review, or have the DBA execute the exact SQL from an approved secure workstation.

## Run and retain evidence

1. Dispatch `Phase 2 production tenant-integrity preflight` with ref `audit/phase-2-implementation-gap-analysis` after the workflow is available on the default branch.
2. Complete any required protected-environment reviewer approval.
3. Download the artifact named `phase2-production-preflight-<run_id>`.
4. Require both output markers:
   - `V57 catalogue tenant-integrity preflight: PASS`
   - `V58 operational tenant-integrity preflight: PASS`
   - `V60 tenant-owned tenant_id NOT NULL preflight: PASS`
5. Review the tenant-schema-audit-output.txt result in full. Every base table must be classified as TENANT_OWNED, PLATFORM_GLOBAL or MIXED by the data owner. Every TENANT_OWNED table must show tenant_id IS_NULLABLE=NO and null_tenant_rows=0. Every table without tenant_id and every mixed-scope table must have a documented owner-approved rationale and tenant relationship. Reconcile every FK and unique index.
6. Record the run URL, run ID, database/schema identifier, execution timestamp, operator, reviewer approval and archived audit artifact in the change ticket. Do not record credentials.

## Failure and remediation procedure

- Preserve the failed workflow artifact and the exact V57/V58 SIGNAL message.
- Stop the migration rollout. Do not bypass or disable the failing check.
- Have the data owner and DBA identify affected row IDs using a separately reviewed SELECT diagnostic scoped by tenant. Do not export personal/customer data into the CI log.
- Prepare a tenant-aware repair script with before/after counts, expected row IDs, transaction/backup plan, and rollback procedure.
- Obtain data-owner and DBA approval before executing any repair DML.
- Re-run the unchanged preflight after repair. All three PASS markers are required.
- Obtain explicit production change approval before applying V57/V58/V60 migrations.

## Current execution record

**Not executed in this branch session.** No production database connection or protected environment secrets were available to this session. No production data was read or modified. The production workflow now runs both the V57/V58 relationship preflight and the separate SELECT-only tenant schema/NOT NULL audit using a dedicated read-only account. A passing MySQL 8.4 CI migration/scheduler run is not a substitute for this production data preflight.
