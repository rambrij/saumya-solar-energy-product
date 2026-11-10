# Phase 2 production preflight and remediation runbook

## Purpose

Run the V57/V58 tenant-integrity preflight against the production MySQL database before any migration rollout. The SQL checks legacy tenant-owned relationships for orphaned and cross-tenant references. It fails closed on the first detected violation.

## Important safety properties

- The script does not INSERT, UPDATE or DELETE business rows.
- It does create and drop two named stored procedures to express fail-fast checks. The database identity therefore needs CREATE ROUTINE and DROP ROUTINE privileges in addition to SELECT access. Review that privilege grant with the DBA before execution.
- It does not apply V57/V58 indexes, foreign keys or constraints.
- It does not repair rows automatically.
- Do not run V57/V58 migrations after a failed preflight.

## Execution prerequisites

1. Confirm a current production backup and an approved change window.
2. The workflow `.github/workflows/phase-2-production-preflight.yml` uses the protected GitHub environment named `production`.
3. Configure these environment secrets: `PROD_DB_HOST`, `PROD_DB_PORT`, `PROD_DB_NAME`, `PROD_DB_USER`, `PROD_DB_PASSWORD`.
4. Restrict the database identity to the minimum required schema privileges. Confirm the procedure create/drop privileges are permitted by the DBA.
5. The workflow file must exist on the repository default branch before GitHub enables workflow_dispatch. The Phase 2 branch intentionally does not change the default/base branch. Merge the workflow file only after the normal PR review, or have the DBA execute the exact SQL from an approved secure workstation.

## Run and retain evidence

1. Dispatch `Phase 2 production tenant-integrity preflight` with ref `audit/phase-2-implementation-gap-analysis` after the workflow is available on the default branch.
2. Complete any required protected-environment reviewer approval.
3. Download the artifact named `phase2-production-preflight-<run_id>`.
4. Require both output markers:
   - `V57 catalogue tenant-integrity preflight: PASS`
   - `V58 operational tenant-integrity preflight: PASS`
5. Record the run URL, run ID, database/schema identifier, execution timestamp, operator and reviewer approval in the change ticket. Do not record credentials.

## Failure and remediation procedure

- Preserve the failed workflow artifact and the exact V57/V58 SIGNAL message.
- Stop the migration rollout. Do not bypass or disable the failing check.
- Have the data owner and DBA identify affected row IDs using a separately reviewed SELECT diagnostic scoped by tenant. Do not export personal/customer data into the CI log.
- Prepare a tenant-aware repair script with before/after counts, expected row IDs, transaction/backup plan, and rollback procedure.
- Obtain data-owner and DBA approval before executing any repair DML.
- Re-run the unchanged preflight after repair. Both PASS markers are required.
- Obtain explicit production change approval before applying V57/V58 migrations.

## Current execution record

**Not executed in this branch session.** No production database connection or protected environment secrets were available to this session. No production data was read or modified. A passing MySQL 8.4 CI migration/scheduler run is not a substitute for this production data preflight.
