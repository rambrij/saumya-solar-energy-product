# Phase 3 Enterprise SaaS — Acceptance Gap Analysis

**Repository:** `rambrij/saumya-solar-energy-product`  
**Branch:** `feature/phase-3-Enterprise-SaaS`  
**Review date:** 2026-11-09  
**Purpose:** Record verified gaps before further schema or workflow changes. This is not a production-readiness certification.

## Evidence reviewed

- `README.md` on this branch. Its documented role/endpoint matrix still identifies broad endpoint authorization, tenant-composite constraints, cross-tenant ID-guessing tests, audit search, dashboards and offline work as incomplete or future work.
- `backend/src/main/resources/application.yml`. It configures local filesystem directories for uploads, service evidence, survey evidence and warranty evidence. It contains no Vault/KMS integration or secret-rotation configuration.
- `backend/src/main/resources/db/migration/V43__branch_scope_business_records.sql`. It adds nullable `branch_id` columns and tenant/branch foreign keys to enquiry, lead, survey, quotation, order, invoice, service request and installation job. Existing rows remain NULL.
- `frontend/src/EnterpriseSaasPanel.tsx`. It exposes branch creation/membership, subscription/storage display, integration configuration and audit-event search. It does not render a branch analytics dashboard in the reviewed component.
- `frontend/src/OfflineSyncPanel.tsx`. Its IndexedDB queue supports service comments and survey update/start/submit operations. It queues JSON payloads only; no attachment upload/synchronization workflow is present. The client removes APPLIED results, but does not persist/display a distinct conflict-resolution state for non-APPLIED results.

## Acceptance gaps and required implementation

### 1. Branch assignment and authorization

- Add explicit branch context to create/update flows for every branch-owned business record.
- Ensure direct get/update/delete/action endpoints enforce tenant AND branch scope, not only list filtering.
- Define a documented policy for legacy records with NULL branch_id; do not silently expose them to every branch.
- Ensure related-record references (customer, lead, survey, quotation, order, invoice, service request, installation, inventory and warehouse) cannot cross branch boundaries unless an explicit tenant-admin permission allows it.
- Add cross-branch negative tests for read, update, delete, approve, convert, assign, export and attachment download.
- Add safe migration/backfill tooling only after the business owner defines deterministic legacy-record assignment rules.

### 2. Audit logging and security tests

- Centralize audit capture for successful and denied business actions, authentication/MFA events, permission changes, branch membership changes, subscription changes, secret lifecycle events and integration operations.
- Persist actor, tenant, branch, action, entity type/id, timestamp, outcome, correlation/request ID, source metadata and redacted before/after snapshots.
- Make audit records append-only at the application authorization layer; never store passwords, access tokens, raw secrets or sensitive attachment contents.
- Add security tests for tenant/branch IDOR, privilege escalation, role changes during active sessions, webhook replay, idempotency collisions and unauthorized attachment access.
- Verify audit failure behavior for critical operations (transactional/outbox strategy) and ensure audit search is permission-gated.

### 3. Storage metering and secret lifecycle

- Reconcile actual bytes on disk against a durable tenant usage ledger, including uploads, survey/service/warranty attachments, package/product media and deletions.
- Make quota reservation and release concurrency-safe and idempotent; account for failed uploads, replacement, deletion, orphan cleanup and retries.
- State explicitly that local filesystem storage is single-instance unless a shared durable filesystem is deployed; add operational backup/restore checks.
- Add a production secret-provider abstraction (Vault/KMS or deployment secret manager), encrypted-at-rest integration credentials, least-privilege access, redacted reads, rotation/versioning, revocation and audit events.
- Keep provider-neutral payment/accounting interfaces; do not invent provider credentials or enable a concrete adapter without an explicit provider choice.

### 4. Offline conflict resolution and attachment synchronization

- Extend the IndexedDB schema with operation state, attempt count, server version/ETag, conflict code, last error and resolution metadata.
- Define server conflict semantics (for example, optimistic version mismatch) and return stable per-operation results: APPLIED, DUPLICATE, CONFLICT or REJECTED.
- Add UI to inspect conflicts and choose supported resolutions (refresh/rebase, discard, or retry after user edit); do not silently retry permanent conflicts.
- Add encrypted/authenticated attachment queue metadata, upload resumability/checksum, server-side ownership validation, size/type limits, deduplication and cleanup.
- Ensure logout/account switching purges or isolates local queued data and attachment blobs by user and tenant.
- Add browser tests for offline creation, reconnect replay, duplicate delivery, version conflict, attachment interruption and account switching.

### 5. Dashboards and end-to-end acceptance

- Implement branch dashboards for sales pipeline, quotation conversion, order value, invoice/payment status, inventory, installation workload, service SLA and warranty.
- Apply branch and date filters server-side; aggregate only authorized branch data and test cross-branch totals.
- Add end-to-end journeys for platform provisioning, vendor branch creation/membership, enquiry-to-order, invoice/payment, installation/service/warranty, subscription limits, integration job failure/retry, offline sync and audit review.
- Run backend tests, frontend typecheck/build, Flyway migration validation against MySQL 8.4, Spring Boot startup, and browser E2E tests against the same release commit.
- Publish the exact commit SHA and workflow run IDs; do not mark acceptance complete while any required job is queued, skipped or failing.

## Data-safety constraints

- Do not run `docker compose down -v`, drop the database, truncate business tables or rewrite historical branch ownership.
- All schema changes must be additive and forward-only.
- Backfills must be explicit, repeatable, auditable and separately tested.
- Existing NULL branch ownership must remain intact until a reviewed assignment policy is approved.

## Acceptance status

**Phase 3 is not yet fully accepted.** The branch migration V43 provides nullable branch ownership foundations, and the frontend has initial enterprise administration and offline queue surfaces. The evidence reviewed here does not establish complete branch authorization, universal audit coverage, durable metering/reconciliation, Vault/KMS lifecycle, offline conflict/attachment handling, branch analytics or full E2E acceptance.
