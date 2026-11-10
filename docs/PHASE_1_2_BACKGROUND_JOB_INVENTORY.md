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
| `NotificationService.retryFailedDeliveries` | `@Scheduled(fixedDelayString)` | Tenant ID on persisted `NotificationDelivery` record | Opens `TenantExecutionContext` per record; null tenant rows are skipped | Atomic `claimFailedDelivery(id, tenantId, now)` update prevents two instances claiming the same FAILED row; retry persists final state | PARTIAL — tenant context and atomic claim implemented; provider retry/idempotency and multi-instance failure recovery still need integration tests |
| `AuthenticationRateLimitCleanup.removeExpiredBuckets` | `@Scheduled(cron)` | Global authentication abuse buckets; no tenant-owned business rows | No tenant context required | Deletes expired global rate-limit buckets only | MET for its stated global cleanup purpose; no tenant data is read or mutated |

## Evidence and tests

- `backend/src/main/java/com/saumyasolar/security/TenantExecutionContext.java`: scoped tenant context with close/restore behavior.
- `backend/src/main/java/com/saumyasolar/service/IntegrationSyncScheduler.java`: explicit per-tenant scope.
- `backend/src/main/java/com/saumyasolar/service/ServiceSlaScheduler.java`: explicit per-tenant scope.
- `backend/src/main/java/com/saumyasolar/service/WarrantyExpiryScheduler.java`: explicit per-tenant scope.
- `backend/src/main/java/com/saumyasolar/service/SubscriptionExpiryScheduler.java`: explicit per-tenant scope.
- `backend/src/main/java/com/saumyasolar/service/NotificationService.java`: tenant-scoped notification retry and atomic claim.
- `backend/src/main/java/com/saumyasolar/repo/NotificationDeliveryRepository.java`: `claimFailedDelivery(id, tenantId, now)` atomic tenant-qualified claim.
- `backend/src/main/java/com/saumyasolar/service/AuthenticationRateLimitCleanup.java`: global expired-bucket cleanup.
- `backend/src/test/java/com/saumyasolar/TenantSchedulerConcurrencyIntegrationTest.java`: four MySQL 8.4 integration tests for concurrent integration claims and scheduler behavior.
- `backend/src/test/java/com/saumyasolar/security/TenantExecutionContextTest.java`: nested-scope, exception and single-thread executor reuse cleanup.
- `backend/src/test/java/com/saumyasolar/service/NotificationServiceTest.java`: tenant context during retry and duplicate atomic-claim skip.
- A source scan of all 41 files in `backend/src/main/java/com/saumyasolar/service/` found six `@Scheduled` methods/classes listed above and no `@Async`, `ExecutorService`, `ThreadPoolTaskExecutor`, `CompletableFuture` or `new Thread(...)` usage in that service package. This does not yet include all non-service components, event listeners, framework configuration or external queue consumers.
- Workflow `42828934287` passed all four jobs, including 171 backend tests, MySQL 8.4 schema inventory and Playwright browser security tests.

## Remaining inventory work

1. Complete the remaining scan outside the service package: all backend Java packages, application event listeners, Spring executor configuration, export/report paths, and external queue consumers. The 41-file service-package scan found no `@Async` or executor submissions, but this is not yet a repository-wide proof.
2. Added `TenantExecutionContextTest` nested exception and reused-single-thread cleanup cases; continue cancellation and all worker exit-path tests.
3. Notification retries now use an atomic tenant-qualified claim. Add MySQL multi-instance duplicate-delivery tests and idempotency tests for every integration/notification adapter.
4. Do not pass a tenant ID from a browser or untrusted payload directly into the context; resolve it from a trusted tenant row/job record first.

## Acceptance

This inventory is a partial, evidence-based list, not a claim that no other asynchronous work exists. Phase 2 job-scoping cannot be marked MET until the repository-wide worker inventory and all worker failure/cancellation/idempotency tests are complete.
