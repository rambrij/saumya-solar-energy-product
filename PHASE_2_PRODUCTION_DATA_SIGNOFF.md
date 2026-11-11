# Phase 2 production schema and tenant-data sign-off

This record must be completed from the protected production preflight workflow artifact. Do not copy CI/disposable-MySQL counts into this record.

## Production execution

| Field | Recorded value |
|---|---|
| Repository | `rambrij/saumya-solar-energy-product` |
| Release branch / commit | |
| GitHub Actions workflow run URL / ID | |
| Workflow attempt | |
| Production database/schema identifier | |
| Execution timestamp (UTC) | |
| DBA execution identity (name, not credentials) | |
| Protected environment reviewer | |
| Current backup / change-ticket reference | |
| Evidence artifact name and retention location | |
| Evidence manifest SHA-256 | |

## Mandatory preflight results

| Check | Required output | Result / evidence |
|---|---|---|
| V57 catalogue tenant-integrity | PASS | |
| V58 operational tenant-integrity | PASS | |
| V60 tenant-owned tenant_id NOT NULL | PASS | |
| Full base-table inventory | Every base table classified | |
| Exact tenant_id NULL-row counts | Exact counts reviewed, not estimated TABLE_ROWS | |
| Foreign-key inventory | Every tenant relationship reconciled | |
| Unique-index inventory | Tenant-natural-key exceptions justified | |
| Tables without tenant_id | Each owner-approved as platform-global or has documented safe scope | |
| Mixed-scope tables | Platform-vs-tenant row rules verified | |

## Row classification and exceptions

Attach the complete `tenant-schema-audit-output.txt` artifact. The data owner must classify every base table as one of:

- `TENANT_OWNED`
- `PLATFORM_GLOBAL`
- `MIXED`
- `USER_SCOPED`
- `JOIN_TABLE_INHERITED_SCOPE`

For each `TENANT_OWNED` table, confirm `tenant_id IS_NULLABLE = NO` and `null_tenant_rows = 0`. For nullable/mixed tables, record the permitted row types and supporting ownership query. For any table without `tenant_id`, record the authoritative ownership path and explain why a tenant column is not required. Do not use MySQL `TABLE_ROWS` estimates as exact row counts.

| Table | Owner classification | Row count | NULL tenant_id count | FK/index review | Exception / rationale | Data owner |
|---|---|---:|---:|---|---|---|
| | | | | | | |

## Approval

- DBA name and approval reference:
- Data owner name and approval reference:
- Security reviewer:
- Release owner:
- Unresolved exceptions / remediation tickets:
- Approval date (UTC):
- Final decision: `APPROVED` / `REJECTED`

**Release gate:** Phase 2 remains NOT APPROVED until all PASS markers are present, every table and relationship is classified, the full audit output and manifest are archived, and the DBA, data owner, security reviewer and release owner approve this record. A successful CI run against disposable MySQL is not production evidence.
