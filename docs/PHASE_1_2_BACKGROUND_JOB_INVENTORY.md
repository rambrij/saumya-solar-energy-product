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

1. The repository-wide static scan is complete for checked-in source and deployment configuration. The remaining discovery item is external-runtime confirmation: inspect the deployed Render/AWS/other cloud project, platform scheduled jobs, managed queues, and environment-specific worker overrides with the infrastructure owner. Those resources are not represented in this repository.
2. Added `TenantExecutionContextTest` nested exception and reused-single-thread cleanup cases; continue cancellation and all worker exit-path tests.
3. Notification retries now use an atomic tenant-qualified claim. Add MySQL multi-instance duplicate-delivery tests and idempotency tests for every integration/notification adapter.
4. Do not pass a tenant ID from a browser or untrusted payload directly into the context; resolve it from a trusted tenant row/job record first.

## Acceptance

Repository-wide checked-in-source discovery is MET based on CI artifact phase2-async-worker-inventory (run 42911955413, artifact 13806429908). This is not proof about external cloud resources, production-only environment registrations, or runtime configuration. Worker isolation, retry, cancellation and idempotency correctness remain separate tests; external-runtime inventory remains open until the infrastructure owner supplies deployment evidence.


## Repository-wide scanner and CI evidence — 2026-11-10

- `scripts/audit/scan-async-workers.sh` scans the repository root across Java/Spring, JavaScript/TypeScript, Python/Go/Ruby, cloud queue/event client signatures, and deployment/workflow configuration. It excludes generated/dependency folders, test source, and its own scanner file.
- CI run `42911955413`, artifact `13806429908`, was downloaded and reviewed. It reports exactly seven `@Scheduled` triggers: `AuthenticationRateLimitCleanup`, `ExportService.recoverAndExpire`, `IntegrationSyncScheduler`, `NotificationService.retryFailedDeliveries`, `ServiceSlaScheduler`, `SubscriptionExpiryScheduler`, and `WarrantyExpiryScheduler`.
- The scan identifies two `@Async` application listeners (`ExportEventListener`, `BusinessNotificationEventListener`) and one bounded `ThreadPoolTaskExecutor` (`applicationEventExecutor`, 2 core / 4 max / queue capacity 100 / `CallerRunsPolicy`).
- No checked-in third-party message-queue consumer or producer client was found. The durable integration queue is database-backed (`IntegrationSyncJob`), populated by `IntegrationManagementService.enqueue` and `enqueueAccountingEvent`, and consumed by `IntegrationSyncScheduler.dispatch` using tenant-qualified atomic `claimIfDue`.
- No Python/Go/Ruby worker primitives or BullMQ/Agenda/node-cron worker clients were found. The frontend hit in `Phase2AdminPanel.tsx` is a browser `setTimeout` used to poll an export's status; it is not a server worker. `EnterpriseSaasPanel` enqueues integration jobs and `OfflineSyncPanel` stores user actions in the browser; neither is a worker process.
- `docker-compose.yml` declares only `mysql`, `mailpit`, `backend`, and `frontend`; it does not declare a separate worker container. No Kubernetes, Terraform, Render Blueprint, or Serverless deployment file is checked in. This cannot prove that the cloud provider has no separately configured cron jobs, queues, or worker services.
- Startup runners: `DataSeeder.seedData` and `PlatformAdminBootstrap.bootstrapPlatformAdmin`. Both default to disabled in `application.yml`; local `docker-compose.yml` explicitly sets `SEED_DATA_ENABLED=true` for demo development. Production must override it to `false`. `PLATFORM_BOOTSTRAP_ENABLED` must also remain false unless a reviewed one-time bootstrap is explicitly approved.
- The static inventory is complete for repository-controlled code/config. Production/runtime configuration outside Git remains an owner verification task.

## Repository scan findings — CI artifact 13806429908

The reviewed repository-root scan confirms the seven scheduled triggers, two asynchronous application listeners, one bounded in-process executor and two startup runners described above. It finds no checked-in third-party message queue client or separate deployment worker service. Browser polling and user-triggered queue actions are identified as UI behavior, not server workers.

The scan does not inspect live cloud resources, platform cron settings, secret-driven environment overrides, or provider-managed queues. These remain an external operations sign-off item. Do not infer their absence from the repository scan.

### Tenant-first notification retry correction — 2026-11-10

NotificationService.retryFailedDeliveries previously selected FAILED delivery entities globally before opening each tenant context. It now selects only distinct tenant IDs globally, opens a tenant scope, and retrieves that tenant's due delivery payloads through a tenant-qualified query before the atomic claim. Multi-tenant context isolation and thread-local cleanup are covered by NotificationServiceTest. See GAP-P2-background-job-scoping.md for the code/test evidence.
