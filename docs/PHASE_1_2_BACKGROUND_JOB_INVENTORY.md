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
| `NotificationService.retryFailedDeliveries` | `@Scheduled(fixedDelayString)` | Tenant IDs selected from persisted tenant-owned deliveries | Opens `TenantExecutionContext` per tenant; global platform MFA/password-reset rows are not included in business retry selection | Atomic `claimFailedDelivery(id, tenantId, now)` update prevents two instances claiming the same FAILED row; retry persists final state | PARTIAL — tenant context and atomic claim implemented; provider retry/idempotency and multi-instance failure recovery still need integration tests |
| `ExportService.recoverAndExpire` | `@Scheduled(fixedDelayString)` | Distinct tenant IDs from pending/processing/expired export jobs | Recovery and expiry now run inside `TenantExecutionContext` for each tenant | Recovery query and atomic claim are tenant-qualified; expired export query and storage deletion are tenant-qualified | PARTIAL — tenant scope and claim tests added; latest CI and object-storage cleanup failure/retry evidence pending |
| `ExportEventListener.onApplicationEvent` | `@Async("applicationEventExecutor")` | Tenant ID carried by persisted `ExportRequestedEvent` | Opens `TenantExecutionContext` with try-with-resources | `ExportService.process` claims and reloads by `(export_id, tenant_id)` | PARTIAL — unit tests cover tenant-aware processing; async executor saturation and listener recovery need CI review |
| `BusinessNotificationEventListener.onApplicationEvent` | `@Async("applicationEventExecutor")` | Tenant ID carried by persisted `BusinessNotificationRequestedEvent` | Opens `TenantExecutionContext` with try-with-resources | `NotificationService.processPendingDelivery` claims and reloads by `(delivery_id, tenant_id)` | PARTIAL — tenant scope and recovery scheduler exist; delivery retry/idempotency still needs multi-instance integration evidence |
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
- The repository-wide `scripts/audit/scan-async-workers.sh` scans all backend Java packages, application event publishers/listeners, `@Async`, executors, queue consumers/producers, startup runners, and workflow/resource scheduler definitions. Its CI artifact must be reviewed for every match; a grep inventory alone is not proof of tenant isolation.
- Workflow `42828934287` passed all four jobs, including 171 backend tests, MySQL 8.4 schema inventory and Playwright browser security tests.

## Remaining inventory work

1. Complete the remaining scan outside the service package: all backend Java packages, application event listeners, Spring executor configuration, export/report paths, and external queue consumers. The 41-file service-package scan found no `@Async` or executor submissions, but this is not yet a repository-wide proof.
2. Added `TenantExecutionContextTest` nested exception and reused-single-thread cleanup cases; continue cancellation and all worker exit-path tests.
3. Notification retries now use an atomic tenant-qualified claim. Add MySQL multi-instance duplicate-delivery tests and idempotency tests for every integration/notification adapter.
4. Do not pass a tenant ID from a browser or untrusted payload directly into the context; resolve it from a trusted tenant row/job record first.

## Acceptance

This inventory is a partial, evidence-based list, not a claim that no other asynchronous work exists. Phase 2 job-scoping cannot be marked MET until the repository-wide worker inventory and all worker failure/cancellation/idempotency tests are complete.


## Repository-wide scanner added — 2026-11-10

- Added scripts/audit/scan-async-workers.sh to scan backend Java source for @Scheduled, @Async, executor declarations/submissions, CompletableFuture, application event listeners/producers, messaging/queue consumer annotations and startup runners. It also scans backend resource and GitHub workflow configuration for scheduler/worker declarations.
- release-validation.yml runs the scanner on every push to this audit branch and uploads its output as a 30-day artifact. The current source review identifies exactly seven scheduled methods/classes: AuthenticationRateLimitCleanup, ExportService.recoverAndExpire, IntegrationSyncScheduler, NotificationService.retryFailedDeliveries, ServiceSlaScheduler, SubscriptionExpiryScheduler and WarrantyExpiryScheduler. It also identifies two @Async application listeners (ExportEventListener and BusinessNotificationEventListener) using the bounded applicationEventExecutor.
- A checked-in scanner and uploaded artifact improve reproducibility but do not themselves prove that every hit is tenant-safe. The artifact must be reviewed and each discovered boundary added to the table above. Queue consumers, event listeners and framework-managed async boundaries remain unverified until that scan output is reviewed.


## Repository scan findings — CI run 42843505138

The checked-in scanner is configured to execute across backend/src/main/java and the resource/workflow configuration. The next successful CI artifact is required to confirm the full hit count and review every boundary:

- Scheduled triggers found: exactly the six rows already listed above.
- The current Java source includes @Async application listeners and a bounded ThreadPoolTaskExecutor in ApplicationEventConfiguration. These listeners explicitly open TenantExecutionContext and delegate to tenant-qualified processing/claim paths. CI scan output remains the authoritative full-repository inventory.
- No application event listeners/producers or Kafka/Rabbit/JMS listener/template consumers found in backend Java source. The scan output has no false-positive business-method submit entries after the pattern refinement.
- Startup runners found: DataSeeder.seedData (guarded by app.seed.enabled, default false) and PlatformAdminBootstrap.bootstrapPlatformAdmin (guarded by app.bootstrap.platform-admin.enabled, default false). These are startup initialization paths, not background workers; their data-seeding and credential policy must be audited separately against the no-production-bootstrap-user rule.
- The initial scanner version used ripgrep, which is not installed on the runner; that scan artifact was invalid. The scanner was changed to portable grep. The corrected CI scan at 42841931283 is the first usable result. A follow-up pattern refinement removes false positives for ordinary business methods named submit.

The scan covers checked-in backend Java source and declared YAML/properties/XML schedules. It found seven scheduled triggers, two @Async event listeners and one bounded ThreadPoolTaskExecutor; no Kafka/Rabbit/JMS consumer or producer was found in checked-in Java source. It also found two startup runners: DataSeeder.seedData (app.seed.enabled, default false) and PlatformAdminBootstrap.bootstrapPlatformAdmin (app.bootstrap.platform-admin.enabled, default false). These must remain disabled in production unless separately approved. The scan cannot detect dynamically registered tasks, library-internal asynchronous work, external infrastructure queues configured outside this repository, or production-only runtime integrations. Re-run and review the artifact after any worker-related change.


### Tenant-first notification retry correction — 2026-11-10

NotificationService.retryFailedDeliveries previously selected FAILED delivery entities globally before opening each tenant context. It now selects only distinct tenant IDs globally, opens a tenant scope, and retrieves that tenant's due delivery payloads through a tenant-qualified query before the atomic claim. Multi-tenant context isolation and thread-local cleanup are covered by NotificationServiceTest. See GAP-P2-background-job-scoping.md for the code/test evidence.
