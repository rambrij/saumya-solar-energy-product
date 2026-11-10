# GAP-P2-TEN-003 — Tenant context propagation and cleanup in scheduled/asynchronous jobs

- **Gap ID:** P2-TEN-003
- **Phase:** Phase 2 — Tenant isolation and data integrity
- **Severity:** High
- **Status:** Open

## Requirement reference

- SRS §8.3 — Export jobs and background jobs must be tenant-scoped and carry explicit validated tenant context.
- SRS §18 — Every background job must be idempotent, tenant-aware and observable.
- SRS §16.3 — Multi-instance scheduler/queue behavior must be safe.
- SRS §20.1 — Tenant isolation acceptance criteria.

## Current state and evidence

- `TenantExecutionContext.open/requireTenantId` uses a ThreadLocal scope and returns an AutoCloseable scope that restores/removes the prior value.
- `ServiceSlaScheduler.evaluate`, `WarrantyExpiryScheduler.evaluateWarrantyExpiry` and `SubscriptionExpiryScheduler.expireSubscriptions` iterate trusted tenant records and establish context with try-with-resources.
- `IntegrationSyncJobRepository.claimIfDue` includes both job ID and tenant ID in the atomic claim predicate.
- `TenantSchedulerConcurrencyIntegrationTest` covers selected integration job claim and subscription/SLA/warranty scheduler behavior.
- The full inventory of scheduled methods, `@Async` methods, queue consumers, export workers and notification workers was not completed in this audit. A ThreadLocal context does not automatically propagate to executor threads; each asynchronous boundary must explicitly establish and clear its tenant scope.

## Expected state

- Every scheduled or asynchronous job obtains tenant identity from a trusted database job record or validated server-side tenant enumeration, never client input.
- Each job opens a tenant execution scope and closes it in `finally`/try-with-resources, including exception and cancellation paths.
- Repository commands require the tenant ID and use tenant-qualified predicates.
- Job claiming is atomic and tenant-qualified; retries and duplicate deliveries are idempotent.
- Logs/metrics include safe tenant and correlation context; no job leaks context between pooled threads.

## Existing code to extend

- Extend `TenantExecutionContext`, existing scheduler components, `IntegrationSyncScheduler`, export/report workers, notification delivery and any existing async executor/queue consumer.
- Reuse tenant-qualified repository methods and `IntegrationSyncJobRepository.claimIfDue`.
- Reuse `TenantSchedulerConcurrencyIntegrationTest` and existing job entities; do not introduce a second tenant-context abstraction.

## Implementation plan

1. Enumerate all methods annotated with `@Scheduled`, `@Async`, executor submissions and queue/worker entry points.
2. Trace each job's tenant source and all repository/service calls it invokes.
3. Add explicit tenant context opening/cleanup at each async boundary; avoid inheritable ThreadLocal as a substitute for validated propagation.
4. Make each claim/update/delete predicate include tenant ID and idempotency key where applicable.
5. Add exception-safe context cleanup and observable failure/retry state.
6. Extend MySQL concurrency tests for duplicate claims and multi-tenant parallel workers.

## Test plan

- Unit: context is absent outside scope; nested scope restores prior tenant; exceptions always clear scope.
- Integration: each scheduler processes only its tenant's rows; concurrent workers cannot claim the same job twice; retry/dead-letter paths preserve tenant.
- Negative/security: forged tenant ID in job payload is ignored/rejected; job A cannot process job B's data; worker thread reused after exception has no stale tenant context.
- Regression: scheduled tasks remain idempotent and do not duplicate notifications or state transitions.

## Acceptance criteria

- A checked-in job inventory lists each scheduled/async worker and tenant source.
- Every worker explicitly establishes and clears tenant context.
- All job repository mutations are tenant-qualified and idempotent.
- Cross-tenant and thread-reuse tests pass on MySQL 8.4 and CI.
- No job accepts an untrusted tenant ID from a browser or external payload without server-side validation.

## Inventory artifact

See `docs/PHASE_1_2_BACKGROUND_JOB_INVENTORY.md` for the evidence-based scheduled/worker inventory. `NotificationService.retryFailedDeliveries` now opens `TenantExecutionContext` for each persisted tenant-owned delivery and skips null-tenant records. `TenantExecutionContextTest` tests nested exception and single-thread executor reuse cleanup; `NotificationServiceTest` verifies notification retry sees the correct tenant context and clears it afterward. Commits `f11581a3`, `597ebb3f`, `7d65478e`, `df73efd3`, `ed0780af`.

Still open: complete all `@Scheduled`, `@Async`, executor, queue, export and report worker discovery; add atomic tenant-qualified claim/idempotency to notification retries to prevent duplicate multi-instance sends; verify cancellation, retry and all failure paths.

## Status

**PARTIAL — scheduled tenant context is implemented in the four identified tenant-aware schedulers and selected MySQL concurrency tests exist.** The full asynchronous/worker inventory and exception/thread-reuse context-leak coverage remain open. Latest-head workflow 42826862684 for commit `7d65478e` is running; no passing result is claimed yet.


Latest-head verification update (2026-11-10): workflow 42821756695 passed backend (163 tests), frontend production build and MySQL 8.4 migration validation; Spring Boot/browser Playwright job remains in progress.


CI update (2026-11-10): workflow 42821756695 passed all four jobs, including 163 backend tests, frontend build, MySQL migration/integrity validation and Playwright tenant-filter tampering. Reset-token throttling code/tests are being verified in workflow 42822434704.
