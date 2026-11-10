# Phase 1/2 Background and Scheduled Job Inventory

**Branch:** `audit/phase-2-implementation-gap-analysis`  
**Review date:** 2026-11-10  
**Scope:** Phase 2 tenant context and data isolation; global cleanup jobs are listed to make the inventory explicit.

| Component / method | Trigger | Tenant source | Context behavior | Repository/claim scoping | Status |
|---|---|---|---|---|---|
| `IntegrationSyncScheduler.dispatch` | `@Scheduled(fixedDelayString)` | Trusted active tenant records enumerated server-side | Opens `TenantExecutionContext` in try-with-resources for each tenant | Claims each sync job with job ID + tenant ID using `IntegrationSyncJobRepository.claimIfDue` | PARTIAL — context and atomic claim exist; duplicate delivery/retry and every adapter path need complete tests |
| `ServiceSlaScheduler.evaluate` | `@Scheduled(fixedDelayString)` | Trusted active tenant enumeration | Opens tenant scope in try-with-resources | SLA evaluation called inside tenant scope | PARTIAL — MySQL concurrency test covers selected behavior; exception/thread-reuse cleanup not isolated in a dedicated test |
| `WarrantyExpiryScheduler.evaluateWarrantyExpiry` | `@Scheduled(cron)` | Trusted active tenant enumeration | Opens tenant scope in try-with-resources | Expiry query/update scoped to tenant | PARTIAL — MySQL concurrency test covers selected behavior; exception/thread-reuse cleanup not isolated in a dedicated test |
| `SubscriptionExpiryScheduler.expireSubscriptions` | `@Scheduled(cron)` | Trusted active tenant enumeration | Opens tenant scope in try-with-resources | Expiry query/update scoped to tenant | PARTIAL — MySQL concurrency test covers selected behavior; exception/thread-reuse cleanup not isolated in a dedicated test |
| `AuthenticationRateLimitCleanup.removeExpiredBuckets` | `@Scheduled(cron)` | Global authentication abuse buckets; no tenant-owned business rows | No tenant context required | Deletes expired global rate-limit buckets only | MET for its stated global cleanup purpose; no tenant data is read or mutated |

## Evidence and tests

- `backend/src/main/java/com/saumyasolar/security/TenantExecutionContext.java`: scoped tenant context with close/restore behavior.
- `backend/src/main/java/com/saumyasolar/service/IntegrationSyncScheduler.java`: explicit per-tenant scope.
- `backend/src/main/java/com/saumyasolar/service/ServiceSlaScheduler.java`: explicit per-tenant scope.
- `backend/src/main/java/com/saumyasolar/service/WarrantyExpiryScheduler.java`: explicit per-tenant scope.
- `backend/src/main/java/com/saumyasolar/service/SubscriptionExpiryScheduler.java`: explicit per-tenant scope.
- `backend/src/main/java/com/saumyasolar/service/AuthenticationRateLimitCleanup.java`: global expired-bucket cleanup.
- `backend/src/test/java/com/saumyasolar/TenantSchedulerConcurrencyIntegrationTest.java`: four MySQL 8.4 integration tests for concurrent integration claims and scheduler behavior.
- Latest code workflow `42821756695`: backend 163 tests passed, frontend build passed, MySQL migration validation passed; Spring Boot/browser job was still running at the time of inventory.

## Remaining inventory work

1. Complete a source-wide scan for executor submissions, application events with async listeners, export/report workers, notification delivery workers, and third-party queue consumers. The repository tree identifies the four scheduled tenant-aware components above; a reliable exhaustive `@Async`/executor symbol inventory is not established yet.
2. Add dedicated nested-scope, exception, cancellation and reused-thread tests proving the ThreadLocal is restored/cleared on every exit path.
3. Add duplicate delivery/retry/idempotency tests for integration sync adapters and any discovered asynchronous worker.
4. Do not pass a tenant ID from a browser or untrusted payload directly into the context; resolve it from a trusted tenant row/job record first.

## Acceptance

This inventory is a partial, evidence-based list, not a claim that no other asynchronous work exists. Phase 2 job-scoping cannot be marked MET until the source-wide worker inventory and context-cleanup tests are complete.
