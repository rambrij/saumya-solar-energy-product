# Phase 1 and Phase 2 Validation Report

**Repository:** `rambrij/saumya-solar-energy-product`  
**Audit branch:** `audit/phase-2-implementation-gap-analysis`  
**Audit date:** 2026-11-10  
**Sources read:** `saumya-solar-energy-solution-requirements.md`, `saumya-solar-production-ready-srs-hld.md`, and `IMPLEMENTATION_GAP_ANALYSIS.md`.  
**Validation exclusion:** `README.md` was not used, searched, cited, or treated as evidence.

## 1. Executive summary

This is a source-level validation and remediation pass, not a production security certification. Application code and tests have been changed on the requested branch, and CI is being used for backend/frontend/MySQL verification. Existing implementation was extended rather than duplicated. No production database, production ingress/WAF configuration, or live penetration test was accessed.

### Status totals

The status-before column means the state found at the start of the audit/remediation pass. The status-after column reflects the code and tests committed in this remediation pass. MET requires complete implementation and passing latest-head automated test evidence. PARTIAL means meaningful implementation exists but the full requirement or its latest-head proof remains incomplete. UNVERIFIABLE means the audit could not establish the condition from available source evidence.

| Status | Before | After |
|---|---:|---:|
| MET | 0 | 0 |
| PARTIAL | 16 | 18 |
| NOT MET | 0 | 0 |
| UNVERIFIABLE | 2 | 0 |
| **Total** | **18** | **18** |

No NOT MET designation is used where an implementation exists but is incomplete; those are PARTIAL. The two initially UNVERIFIABLE database findings were upgraded to PARTIAL after directly inspecting V57/V58 and their preflight SQL. No item is MET because the complete end-to-end security requirements still have residual coverage gaps. Workflow 42821756695 passed all four jobs, including Playwright tenant-filter tampering. Workflow 42822455302 passed backend (164 tests), frontend build, MySQL validation and Spring Boot health/V57/V58 preflight; the Playwright job was still installing Chromium. Additional fixes/tests from commits 102adc61, 597ebb3f, 128f1fc5, 2c429418, a8837dc4, f11581a3, ea118cd0, 6c29c6fb, df73efd3, ed0780af and 3e2d6d57 are under latest-head workflow 42826862684; its jobs are still running.

## 2. Requirement extraction and traceability matrix

The IDs below are audit IDs created for this report. They are not claimed to be native requirement IDs in the source documents.

| Requirement ID | Source document and section | One-line requirement | Code evidence | Test evidence | Before | After |
|---|---|---|---|---|---|---|
| P1-AUTH-001 | Business requirements §18 Security; SRS §15.3 | Apply abuse throttling by endpoint and client source, with account/user controls for authentication and recovery flows. | `AuthenticationRateLimitFilter.limitFor/shouldNotFilter/doFilterInternal/accountIdentifier/incrementAndGet`; `ClientAddressResolver.resolve`; `AuthLifecycleService.authenticate`; V52 `auth_rate_limit_bucket`. Commits `1a79b562` and `0d2a741d` add HMAC-derived account + endpoint buckets for login/forgot-password, an opaque reset-token + endpoint bucket for reset-password, and independent configurable IP + endpoint buckets for public enquiry, export and payment webhooks. | `AuthenticationRateLimitFilterTest` includes account/IP bucket, reset-token digest non-persistence, body replay, raw identifier non-persistence, oversize body, public enquiry, export and webhook cases; `AuthenticationRateLimitConcurrencyIntegrationTest` exercises concurrent requests against the shared MySQL account bucket; `AuthLifecycleServiceTest` covers persisted lockout and expiry. Run 42821337890 passed all four jobs (162 backend tests). Latest code run 42821756695 passed all four jobs (163 backend tests), frontend build, MySQL validation and Playwright tenant-filter tampering. The subsequent reset-token change is under run 42822434704. | PARTIAL | PARTIAL |
| P1-AUTH-002 | SRS §10.1 IAM-006; §15.1; Business requirements §18 Security | Persist failed password attempts, apply temporary lockout, and define safe expiry/reset behavior. | `AppUser.failedLoginAttempts/lockedUntil`; `AuthLifecycleService.authenticate`; V52. | `AuthLifecycleServiceTest.repeatedFailuresLockAccountAndReturnGenericUnauthorized`; `successfulLoginAfterLockoutExpiryResetsCounter`. Latest-head pass not established here. | PARTIAL | PARTIAL |
| P1-AUTH-003 | SRS §10.1 IAM-005; §13.2; §15.1; §20.7 | Rotate refresh tokens, revoke sessions, detect replay, and revoke the affected token family. | `AuthLifecycleService.rotateRefresh/consumeRefresh/logout`; `RefreshTokenRepository.findByTokenForUpdate/findByFamilyIdForUpdate`; `JwtFilter.doFilterInternal`; V53 family fields as described by the branch gap analysis. | `AuthLifecycleServiceTest` includes refresh-family replay coverage; no current-branch DB-backed concurrent refresh-replay pass was established. | PARTIAL | PARTIAL |
| P1-AUTH-004 | SRS §13.2 Browser session contract; §15.1; §15.3 | Keep access tokens in memory, issue refresh tokens in HttpOnly/Secure/SameSite cookies, and protect cookie-authenticated actions with CSRF validation. | `AuthCookieService.issue/requireCsrf/clear`; `Controller.login/refresh/logout/csrf`; `frontend/src/authenticatedFetch.ts` `rotateSession`; `main.tsx` session bootstrap. | `authenticatedFetch.ts` has no standalone proof here of all browser cookie/CSRF edge cases; React E2E exists but latest-head completion was not confirmed. | PARTIAL | PARTIAL |
| P1-AUTH-005 | Business requirements §12; SRS §9.1–9.3; §20.2 | Enforce the documented role-to-permission matrix, prevent platform privilege escalation, and support vendor-scoped custom roles. | `JwtFilter.doFilterInternal`; `AppService.requirePermission`; `OperationsService.permission`; `FinanceService.permission`; `AuthLifecycleService.createCustomRole/updateCustomRole/deleteCustomRole/assignEmployeeRole`; `Controller.java` lines 250–288; `frontend/src/RoleManagementPanel.tsx`; V53 permission catalogue. Removed role-name-only gates from `Phase2OperationsController.ops` and FinanceService SLA controls; `OperationsService.warranties` now filters SERVICE_VIEW-only users to warranties for their assigned installations. Custom-role CRUD/UI exists; exhaustive action-to-permission mapping and legacy broad-check reconciliation remain open. | `Release2SecurityTest` covers missing permission and assigned-sales revision-list scope; `QuotationRevisionServiceTest` covers assigned-sales denial before revision mutation; `Phase2OperationsControllerTest.customRoleWithInstallationAssignPermissionCanViewEngineerDirectory` verifies custom roles use fine-grained INSTALLATION_ASSIGN rather than hard-coded role names; `Release12TenantIntegrationTest.fineGrainedRoleMatrixDeniesCrossModulePrivilegeEscalation` tests inventory, accountant and service-engineer permission boundaries; `AuthLifecycleServiceTest` covers custom role allowlist and cross-tenant role update/delete; `EndpointAuthenticationMatrixTest` covers unauthenticated handler inventory. Full authenticated role × endpoint matrix remains incomplete. | PARTIAL | PARTIAL |
| P1-AUTH-006 | SRS §13.1; §15.2; §20.2 | Deny unauthenticated and unauthorized requests consistently, returning 401 for missing/invalid identity and 403 for authenticated but forbidden actions. | `SecurityConfig.chain`; `JwtFilter.doFilterInternal`; service-level permission guards. | `EndpointAuthenticationMatrixTest.everyNonPublicHttpHandlerRequiresAuthentication` checks unauthenticated mappings, not all authenticated role/resource combinations. | PARTIAL | PARTIAL |
| P1-AUTH-007 | SRS §10.1 IAM-007; §15.6 | Persist authentication, reset, MFA, role-change and account-disable security events with safe actor/tenant context. | `AuthenticationAuditService.record`; `AuthLifecycleService.auditAuth`; existing append-only `audit_event` storage. | `AuthenticationAuditServiceTest` and auth lifecycle tests are referenced by branch gap notes; latest-head pass not confirmed here. | PARTIAL | PARTIAL |
| P1-AUTH-008 | SRS §11; §13.2; §15.3 | Show safe rate-limit and session-expiry feedback in the frontend without leaking account existence or sensitive state. | `frontend/src/main.tsx` handles generic login errors and 429; `authenticatedFetch.ts` dispatches `saumya:session-expired`; API helper maps 403/429. Account lockout intentionally returns generic 401; no distinct 423 flow was established. | Frontend auth/refresh tests are referenced by branch notes; no 423 lockout UX test. | PARTIAL | PARTIAL |
| P2-TEN-001 | Business requirements §13; SRS §8.1 and §14.2 | Every tenant-owned entity/table must carry a non-null tenant identifier; global reference data must be explicitly classified. | Tenant IDs are present across many domain entities; V57/V58 and `tenant-isolation-production-preflight.sql` cover selected catalogue, sales, operational, finance, service, evidence and identity relationships. Added `docs/PHASE_1_2_TENANT_SCHEMA_INVENTORY.md` and read-only `docs/PHASE_1_2_TENANT_SCHEMA_AUDIT.sql`; `release-validation.yml` now emits and uploads a MySQL 8.4 CI schema/FK/unique-index inventory artifact. The full entity/table inventory and actual production-like database NOT NULL audit remain unexecuted. | `Release12TenantIntegrationTest` and migration validation suites cover selected entities, not every table. | UNVERIFIABLE | PARTIAL |
| P2-TEN-002 | Business requirements §13; SRS §6 and §8.2 | Derive tenant identity from the authenticated principal/server-side trusted resolution, never from a caller-supplied tenant ID. | `JwtFilter.doFilterInternal` reloads user and compares tenant/account type/token version; `Principal`; `TenantExecutionContext`; `AppService` tenant guards. | `Release12TenantIntegrationTest`; `tenant-filter-isolation.spec.ts` and `tenant-react-ui.spec.ts` exist; latest full run not established. | PARTIAL | PARTIAL |
| P2-TEN-003 | SRS §8.3 and §15.2 | Tenant-scope every repository read/write/bulk operation; resource lookups must verify tenant ownership rather than trust a bare identifier. | Tenant-qualified methods exist, e.g. `IntegrationSyncJobRepository.findByIdAndTenantId`, `WarrantyClaimAttachmentRepository.findByIdAndTenantIdAndClaimId`, `AppUserRepository.findByIdAndTenantId`, `ServiceVisitRepository.findByIdAndTenantIdAndServiceRequestId`, and service accessors. Employee, engineer, survey assignment, service visit and integration user call sites were updated to use tenant-qualified methods; not every repository/service call site was reviewed. | `Release12TenantIntegrationTest` covers selected cross-tenant resources. `Phase2OperationsController` now uses tenant-qualified engineer/warranty lookups; a `WarrantyRepository.findByIdAndTenantId` method was added. Exhaustive repository call-site inventory is still not established. | PARTIAL | PARTIAL |
| P2-TEN-004 | SRS §8.3 and §14.3 | Add composite tenant-aware foreign keys and tenant-scoped unique constraints where relationships are tenant-owned. | `V57__tenant_aware_catalogue_foreign_keys.sql` adds tenant-aware product/category/package_item constraints. `V58__operational_tenant_composite_foreign_keys.sql` adds composite keys and FKs across sales, orders, inventory, installation, warranty, finance, service, private evidence, offline sync, branch, approval, audit and notification relationships. V58 preflight is fail-closed. | Workflow 42821756695 passed MySQL 8.4 migration/integrity validation and V57/V58 preflight. Full schema inventory and production-data preflight remain open; see `docs/PHASE_1_2_TENANT_SCHEMA_INVENTORY.md` and `docs/PHASE_1_2_TENANT_SCHEMA_AUDIT.sql`. | UNVERIFIABLE | PARTIAL |
| P2-TEN-005 | Business requirements §3 and §13; SRS §8.4 and §20.6 | Customers must access only their own quotations, orders, invoices, payments, installations, warranties, tickets and documents within the tenant. | `CustomerAccountService`; `FinanceService.ensureTicketAccess`; `WarrantyClaimAttachmentService.download`; customer-qualified repository methods. | `Release12TenantIntegrationTest` includes foreign customer address, claim, attachment, invoice PDF, orders and service ticket checks; `warrantyClaimListCreateAndDecisionAreTenantAndCustomerScoped` now also rejects a same-tenant different-customer warranty claim. All customer resource families are not covered. | PARTIAL | PARTIAL |
| P2-TEN-006 | Business requirements §12–13 and §20 roadmap; SRS §10.14 and §11.3 | Enforce branch/assignment scope server-side for branch-bearing operational records and branch-filtered reports. | `EnterpriseSaasService.filterBranchRecords`; `FinanceService.listInvoices/listTickets`; `OperationsService.installations/warehouses/balances/ledger`; `BranchAnalyticsController/Service`. | `Release12TenantIntegrationTest.branchAssignedMemberListsOnlyAssignedBranchEnquiriesLeadsSurveysQuotationsAndOrders` covers CRM, invoice, service, installation and inventory lists; NULL-branch visibility remains an unresolved policy. | PARTIAL | PARTIAL |
| P2-TEN-007 | SRS §8.3–8.4; §10.14 RPT-004/RPT-006; §13.7–13.8; §15.4 | Export and file download operations must apply the same tenant, customer and permission checks as transactional reads. | `Phase2OperationsController` CSV/XLSX/PDF export paths; `WarrantyClaimAttachmentService.download`; private attachment services/controllers. | `Release12TenantIntegrationTest` covers operations CSV/XLSX/PDF tenant aggregates, invoice PDF, foreign warranty claim and attachment parent/customer/tenant access. Not all download/export families covered. | PARTIAL | PARTIAL |
| P2-TEN-008 | SRS §8.3 and §18 | Scheduled and asynchronous work must carry validated tenant context, isolate tenant data, be idempotent and clear context after execution. | `TenantExecutionContext.open/requireTenantId`; `IntegrationSyncScheduler.dispatch`; `ServiceSlaScheduler.evaluate`; `WarrantyExpiryScheduler.evaluateWarrantyExpiry`; `SubscriptionExpiryScheduler.expireSubscriptions`; `NotificationService.retryFailedDeliveries`; `NotificationDeliveryRepository.claimFailedDelivery`; `IntegrationSyncJobRepository.claimIfDue`. Notification retries now open a tenant scope per persisted delivery and atomically claim FAILED rows by `(id, tenant_id)` before sending. `TenantExecutionContextTest` adds nested exception and pooled-thread reuse cleanup; `NotificationServiceTest` asserts tenant context during retry, cleanup and duplicate-claim rejection. Full async/executor/export worker inventory remains incomplete. | `TenantSchedulerConcurrencyIntegrationTest` covers integration claims and subscription/SLA/warranty scheduler cases. `TenantExecutionContextTest` now covers exception/nested-scope/thread reuse; `NotificationServiceTest` covers notification retry tenant scope. Export worker inventory and atomic duplicate notification retry claims remain open. | PARTIAL | PARTIAL |
| P2-TEN-009 | SRS §8.4; §20.1; §20.4; §20.6; Business requirements §13 | Negative tests must prove foreign-tenant/customer read, write and export/download denial, and test delete only where a delete operation is actually specified/exposed. | `Release12TenantIntegrationTest`; `EndpointAuthenticationMatrixTest`; resource-specific tenant-scoped services and repositories. | Existing integration tests cover selected CRUD/export/download paths; no generated full endpoint × verb × identity matrix was demonstrated. | PARTIAL | PARTIAL |
| P2-TEN-010 | Business requirements §13; SRS §6 and §15.2 | Frontend filters, selectors and hidden menu items must never act as authorization; the backend remains authoritative. | `JwtFilter`, controller/service permission guards; frontend navigation and query filters are presentation only. | `frontend/e2e/tenant-filter-isolation.spec.ts`; `frontend/e2e/tenant-react-ui.spec.ts` (two-tenant React workflow is still marked partial in branch notes). | PARTIAL | PARTIAL |

## 3. Remediation update — 2026-11-10

### Application changes committed on the requested branch

- `1a79b56244af6aa23bd58f15f835766574eae908`: account-aware HMAC login/recovery throttling, bounded body replay, focused tests.
- `8be6f0d78bd19b3a5629524e0556255fd7505bdf`: tenant-qualified employee repository lookup; custom employee role assignment now uses the tenant-qualified method.
- `1f5ce498369a2781fff439dcc12bb99f1e48e978`: tenant-qualified engineer and installation/service visit lookups; same-tenant integration user lookup.
- `0d2a741d3c6a50aa44668954868a4591084432c2`: configurable IP + endpoint rate limits for public enquiry, export and payment webhook endpoints.
- `3caa723d1d8b269394f6dba90d5bda5b53debe20` and `ddd117ae71e304b2d7feb51ab525e61f2def8f20`: reset-password requests are rate-limited by a keyed digest of the opaque reset token; added test that raw token/password are never persisted.
- `f3cb1d3051c12b7ea37f1246de50c0d53994ce9c`: unit tests for public enquiry, export and webhook throttling.
- `5f20f0a613d8599c9f91d4f0e7debd743f711aad`: custom role cross-tenant update/delete and tenant-qualified employee lookup tests.
- `bbd10cce0efb86c839925b59eaae7e87e348b735`: assigned-sales scope enforced for quotation revision reads.
- `5ccba941c0e7c54c40b038807cc3cb17f73a5e74`: negative test for another employee's quotation revision-list access.
- `cb47fc3e3422dcef29d64d41f30c7c15e4c62f47`: assigned-sales guard reused by `QuotationRevisionService.revise`; survey employee and service visit lookups now tenant-qualified.
- `409165ea0e8526911b96efb50595b2e32977b915`: dedicated `QuotationRevisionServiceTest` proves an out-of-scope salesperson cannot supersede a quotation.

No Flyway migration was needed or changed for these code fixes. V57/V58 were inspected and retained unchanged.

### CI verification

- Previous full release-validation run `42818724332` on commit `211773834f9f2e8e9b3bf2fbe83e6442400b249c` passed all four jobs: backend compile/tests, Flyway MySQL 8.4 validation, Spring Boot startup/health and frontend typecheck/production build.
- Run `42820915064` for commit `5ccba941c0e7c54c40b038807cc3cb17f73a5e74` failed in backend tests: one stale rate-limit response assertion and one test still stubbing the pre-change bare-ID employee lookup. Frontend build, MySQL migration validation and Spring Boot health/browser job passed.
- Both test issues were corrected in commit `cab55e6f84245131f8de7252774c9ebd4cfb6c16`; run `42821337890` passed backend tests (162 tests), frontend build and MySQL migration validation, while the Spring Boot/browser job was still running at last check.
- Code/test commits `cb47fc3e` and `409165ea` were verified by workflow `42821756695`: backend (163 tests), frontend build, MySQL validation, Spring Boot health, V57/V58 integrity preflight and Playwright tenant-filter tampering all passed. The subsequent reset-token throttling change is under workflow `42822434704`.

### Updated status totals

| Status | Before remediation | After current changes |
|---|---:|---:|
| MET | 0 | 0 |
| PARTIAL | 16 | 18 |
| NOT MET | 0 | 0 |
| UNVERIFIABLE | 2 | 0 |
| **Total** | **18** | **18** |

The two UNVERIFIABLE items (tenant-table inventory and composite FK inventory) are now PARTIAL because V57/V58 and the integrity preflight were directly inspected. This does not mean all tenant tables/relationships are proven complete.
## 4. Conflict log

| Conflict ID | Doc A statement | Doc B statement | Rule applied | Decision |
|---|---|---|---|---|
| CONFLICT-001 | Business requirements §20 Product Roadmap places multiple branches and multiple warehouses in Phase 3 (Enterprise SaaS). | The same business requirements document §24 MVP Delivery Plan places multiple warehouses and branch management in Phase 2 (Operations). SRS §2 describes multiple branches/warehouses as future support; SRS §3.1 includes warehouse management but does not settle branch delivery timing. | Business requirements take precedence over SRS for business behavior. The two business sections conflict with each other, so the document hierarchy cannot resolve this internal contradiction. | For this audit, branch-level negative tests remain in scope because the user explicitly included them. Do not infer a revised commercial release commitment. Product owner must confirm whether branch management and multi-warehouse support are Phase 2 or Phase 3. Branch-unassigned record visibility is separately unresolved. |

No other direct contradiction between the two source documents was established for the Phase 1/2 security and tenant-isolation requirements. The SRS's more prescriptive cookie/CSRF and database-constraint design is treated as technical detail, not as a contradiction of the business document's general JWT/RBAC/tenant-isolation statements.

## 5. Discrepancies in IMPLEMENTATION_GAP_ANALYSIS.md versus inspected code

The existing gap analysis contains historical snapshots and should not be treated as the current branch truth. The following differences were confirmed against files fetched from the requested branch:

1. **Token storage:** the Phase 1 follow-up text says access and refresh tokens remain in localStorage. Current `frontend/src/main.tsx` removes the legacy `session` localStorage entry; `frontend/src/authenticatedFetch.ts` keeps the access session in module memory and stores only the CSRF value in sessionStorage; `AuthCookieService.issue` issues the refresh token as HttpOnly. This historical gap statement is stale. Cookie/CSRF behavior still needs full runtime verification.
2. **Trusted proxy CIDR support:** the gap analysis says the resolver supports exact IP allowlists only. Current `ClientAddressResolver.parseNetwork` parses CIDR prefixes and `TrustedNetwork.contains` checks network membership. Deployment allowlist correctness still needs operational verification.
3. **Browser E2E inventory:** earlier gap-analysis sections state that browser E2E is absent. The requested branch contains `frontend/e2e/tenant-filter-isolation.spec.ts` and `frontend/e2e/tenant-react-ui.spec.ts`. Their existence does not make the full browser acceptance suite complete; the latest React UI test is explicitly described as partial/pending in the branch's continuation notes.
4. **Custom-role capability:** the gap analysis describes custom roles as absent. Current `Controller.java` exposes custom-role CRUD and permission-catalogue endpoints; `RoleManagementPanel.tsx` provides the permission editor and assignment UI. The discrepancy is not feature absence. The remaining gap is exhaustive end-to-end role/action authorization testing and proof that all endpoints enforce the least-privilege matrix.
5. **Branch metadata:** the existing gap analysis header still names `audit/implementation-gap-analysis` as its audit branch, while this report validates `audit/phase-2-implementation-gap-analysis`. Its historical baseline and status totals must not be copied as current branch totals.

No statement from README was used in this comparison.

## 6. Unspecified / needs product decision

1. Whether lockout should return a distinct HTTP 423 or the current generic 401. The source documents require temporary lockout and generic non-disclosing errors, but do not prescribe 423. Returning 423 only for existing accounts may enable account-state inference. Product/security owner must choose a non-enumerating contract before changing it.
2. Exact per-IP, per-account, per-endpoint thresholds, window algorithms, progressive delay policy, and whether account buckets apply to refresh, reset and MFA endpoints. The SRS says to apply rate limits but does not specify numeric thresholds.
3. Application-level IP + endpoint rate limits now cover public enquiry, export and payment webhook endpoints. Production ingress/WAF configuration, numeric threshold approval and distributed multi-instance counter behavior remain unverified.
4. The intended concurrent-device session policy: logout currently revokes user-wide sessions/token version; replay revokes the affected family. The source documents require rotation/revocation but do not specify whether logout should be device-specific.
5. Branch-unassigned (`branch_id IS NULL`) record visibility for branch-scoped employees and reports. Existing code preserves legacy null-branch visibility; the business rule is not explicit.
6. Whether every resource family should expose DELETE. The source documents require isolation for destructive operations where applicable but also require immutable/retained financial and transaction records. Do not add delete endpoints merely to satisfy a test matrix.
7. Exact ownership and cross-vendor identity policy for customers beyond the SRS statement that separate customer identities per vendor are acceptable in the MVP.
8. Production trusted proxy CIDRs, cookie domain/SameSite policy, CORS origin list, refresh-cookie secure flag, and edge rate-limit configuration.
9. Retention period for authentication audit events and rate-limit buckets beyond the existing two-day bucket cleanup.
10. Whether platform support impersonation will be introduced; SRS §10.15 AUD-005 defines controls only if it is introduced.

## 7. Historical implementation update (superseded)

**Latest implementation commit:** `1a79b56244af6aa23bd58f15f835766574eae908`  
**Documentation update:** `201474727f2b9302dc0c571b3d4c9adefbab7a0f`

### Implemented (pending CI verification)

- Added a second, independent rate-limit bucket for normalized account identifiers on login, forgot-password and reset-password.
- Account bucket identifiers are HMAC-SHA-256 digests derived from the configured JWT secret; raw email/username values are not persisted.
- Existing endpoint + trusted-client-IP bucket remains independent.
- Authentication JSON request bodies are bounded to 16 KiB and replayed to Spring MVC so the controller can still deserialize the request.
- Added unit tests for account/IP bucket invocation, request body replay, raw identifier non-persistence and oversized body rejection.
- Reused V52's existing table. No schema migration was required; no applied migration was edited.

### Verification state

GitHub Actions workflow `release-validation.yml` was automatically triggered by the implementation commit (run ID `42818434724`). At the time of this report update, the workflow was still **in progress**. Therefore:
- P1-AUTH-001 remains **PARTIAL** (not MET) until the backend tests pass and the remaining ingress-route policy is established.
- The report's original before/after requirement totals remain unchanged: code implementation alone is not passing-test evidence.
- Public enquiry, export and webhook rate-limit policy (application filter versus upstream gateway/WAF) remains open.
- No full backend/frontend/MySQL/browser suite result is claimed from this pass.

## 8. Remaining risks and recommended next steps

### High priority

- Complete the role-to-permission migration: replace legacy broad service permission checks with the SRS permission catalogue incrementally, retaining compatibility grants until every endpoint is tested.
- Add a user/account keyed rate-limit bucket without storing raw email addresses; keep IP and endpoint dimensions and use generic responses.
- Complete authenticated 401/403 negative tests across platform, vendor admin, each vendor employee role, customer and service engineer identities.
- Inventory every tenant-owned table, FK, repository method, controller mapping, export, file download, async worker and scheduled job. Generate a repeatable test matrix rather than relying on selected examples.
- Independently verify V57/V58 migration files and the complete MySQL FK inventory; run the integrity preflight against an approved production-like database before production rollout.
- Extend two-tenant browser tests to authenticated UI filter/selector/route interactions for customer and branch scoping, not only selected API calls.

### Medium priority

- Add concurrency tests for simultaneous failed login increments and concurrent refresh replay against MySQL.
- Verify all refresh-cookie and CSRF edge cases (missing cookie, mismatched header, expired/replayed cookie, cross-origin credentials, logout and tab concurrency).
- Add tests for every export/download family and every exposed destructive operation; explicitly mark unsupported delete operations.
- Add scheduled-job context cleanup tests after exceptions and asynchronous worker context propagation/clearing tests.
- Resolve the branch NULL-record policy and document it as an authorization rule.

### Verification blockers

- The GitHub Actions workflow was triggered on the account-throttling commit, but it had not completed at the time of this report update. No passing result is claimed yet.
- The branch notes include earlier CI evidence (including a 154-test backend run, frontend typecheck/build and MySQL migration validation) but do not establish a complete passing run for the latest branch head at the time of this audit.
- No production database or production preflight was accessed in this audit.
- One application implementation change and focused tests were committed: account-aware login/recovery throttling. No schema change was needed. This change remains pending CI verification.

## 9. Gap documents

The following gap files are tracked. P1 rate limiting, role/permission enforcement, session feedback, tenant FK coverage, cross-tenant/customer/branch coverage and background job scoping are PARTIAL with remediation notes; remaining items are Open:

- `docs/gaps/GAP-P1-rate-limiting.md`
- `docs/gaps/GAP-P1-role-permission-matrix.md`
- `docs/gaps/GAP-P1-session-lockout-feedback.md`
- `docs/gaps/GAP-P2-composite-tenant-fks.md`
- `docs/gaps/GAP-P2-cross-tenant-customer-branch-export-tests.md`
- `docs/gaps/GAP-P2-background-job-scoping.md`
- `docs/PHASE_1_2_BACKGROUND_JOB_INVENTORY.md`

## 10. Audit conclusion

**Phase 1: NOT MET.** Core controls exist, but rate-limit dimensions and coverage, least-privilege role enforcement, exhaustive negative tests, and frontend lockout/session behavior are not fully verified.

**Phase 2: NOT MET.** Tenant identity and scoping controls are present and meaningful, but the exhaustive tenant/customer/branch CRUD/export/download matrix and complete database relationship inventory are not proven. Background-job scoping is implemented in several schedulers but not verified for every worker.

No requirement is marked MET based solely on source presence. Production approval should wait for the remaining high-priority evidence and a successful latest-head CI run.


CI update (2026-11-10): workflow 42821756695 passed all four jobs, including 163 backend tests, frontend build, MySQL migration/integrity validation and Playwright tenant-filter tampering. Reset-token throttling code/tests are being verified in workflow 42822434704.


## 11. Additional remediation — 2026-11-10

- `102adc61`: tenant-qualified engineer and warranty lookups in `Phase2OperationsController`; `3e2d6d57` adds `WarrantyRepository.findByIdAndTenantId`.
- `597ebb3f` and `7d65478e`: exception-safe nested tenant context and single-thread executor reuse tests.
- `128f1fc5` and `2c429418`: removed role-name-only gates from installation-assignment and SLA control paths so explicit SRS permission grants are authoritative.
- `a8837dc4`: warranty list now limits SERVICE_VIEW-only users to warranties attached to their assigned installations; INSTALLATION_ASSIGN permits tenant-wide view.
- `6c29c6fb`: custom role with INSTALLATION_ASSIGN is covered by a controller unit test.
- `f11581a3`: notification retry worker establishes tenant execution context per persisted tenant-owned delivery.
- `df73efd3` and `ed0780af`: notification retry tenant context and cleanup test.
- `ea118cd0`: same-tenant cross-customer warranty-claim creation is denied.
- `ed714646`, `9ada09fa`, `9763e0d7`: added read-only MySQL schema audit queries and documented the entity/FK inventory and unverified database classifications.
- Latest-head workflow `42826862684` for commit `7d65478e` is running. Earlier runs failed due to a missing tenant-qualified warranty repository method and an ambiguous JUnit assertion; both source issues were corrected in `3e2d6d57` and `7d65478e`. Do not treat the earlier failed runs as passing evidence.

## 12. Remaining work (still not MET)

1. Generate and execute the full authenticated role × endpoint × verb matrix against every SRS standard role and representative custom roles.
2. Finish source-wide permission reconciliation including all controller mappings, service method entry points, role-name gates, broad grants in Flyway/seeder data and secondary controllers.
3. Complete service engineer assigned-resource read/write/download/export tests for tickets, visits, evidence, warranties and installation-related data.
4. Execute schema inventory SQL on the exact MySQL schema; classify every table, prove tenant-owned `tenant_id NOT NULL`, and enumerate every composite FK and tenant-scoped unique key.
5. Complete same-tenant customer negative tests for quotations, payments, installations, warranty, documents, service attachments and all customer-owned endpoints.
6. Complete branch CRUD, branch membership assignment/removal, branch-scoped mutation and branch-filtered export tests, including NULL-branch policy resolution.
7. Inventory every export and private download endpoint; add foreign-tenant/customer/branch/permission tests for every exposed resource family.
8. Finish all scheduled, notification, export, async and executor inventory; add atomic notification retry claiming/idempotency and any queue worker context tests.
9. Complete browser tests for login lockout/rate-limit messages, refresh rotation/replay, CSRF missing/mismatch, logout, expiry, multiple tabs and session recovery.
10. Add distributed rate-limit concurrency tests against MySQL and confirm multi-instance behavior at the production ingress.
11. Run tenant schema preflight against an approved production-like data snapshot; no production DB was accessed in this task.


## 13. New test coverage and code changes after the previous report revision

- `AuthenticationRateLimitConcurrencyIntegrationTest.concurrentRequestsShareTheAtomicAccountBucketAcrossWorkerThreads` adds shared-MySQL concurrent account-bucket verification (commits `eb19f8bb`, `c970dfaf`).
- `Release12TenantIntegrationTest.fineGrainedRoleMatrixDeniesCrossModulePrivilegeEscalation` adds authenticated inventory/accountant/service-engineer cross-module 403/200 cases (commit `8b608e64`).
- `Phase2OperationsControllerTest.customRoleWithInstallationAssignPermissionCanViewEngineerDirectory` verifies a custom role with INSTALLATION_ASSIGN is not blocked by hard-coded role names (commit `6c29c6fb`).
- `NotificationDeliveryRepository.claimFailedDelivery` and `NotificationService.retryFailedDeliveries` atomically claim retries by delivery ID + tenant ID, avoiding duplicate multi-instance processing of the same FAILED record (commits `85de4065`, `8ecb4697`); tests cover successful tenant-scoped retry and skipped duplicate claim (`b371b5da`).
- `frontend/e2e/auth-session-security.spec.ts` adds browser checks for HttpOnly refresh cookie, no localStorage access token, missing/mismatched CSRF denial, refresh rotation and logout token revocation (commit `f442e4c6`).
- `Phase2OperationsController` now uses tenant-qualified warranty and engineer lookups; `WarrantyRepository.findByIdAndTenantId` was added. `OperationsService.warranties` now limits SERVICE_VIEW-only engineers to their assigned installation warranties (commits `102adc61`, `3e2d6d57`, `a8837dc4`).
- `FinanceService` SLA controls and `Phase2OperationsController.ops` now use the SRS fine-grained permission codes rather than additional hard-coded role-name gates (commits `128f1fc5`, `2c429418`).

Latest branch workflow results must be read from the exact head commit. Earlier run `42827261872` passed all four jobs and 169 backend tests for commit `eb19f8bb`. Subsequent commits add atomic notification retry claims, the authenticated role matrix and browser auth E2E; these are not covered by that earlier pass. Workflow `42828454430` for commit `6585efe6` passed all four jobs: backend (171 tests), frontend typecheck/production build, MySQL 8.4 migration validation (including the new schema/FK/unique-index inventory query and artifact upload), and Spring Boot health plus Playwright browser security tests. The browser auth/session test passed with the cookie scoped to `/api/v1/auth`. A subsequent CI run exposed test interference because the auth E2E logged out the shared seeded admin while another test used it; the auth E2E now uses the separate seeded sales account. Latest code workflow `42828934287` for commit `fe7b1181` passed all four jobs, including the isolated browser auth E2E; the current branch head `00b4d767` contains subsequent documentation-only gap-register updates.


## 14. Latest verification snapshot

- Workflow `42827261872` passed all four jobs and 169 backend tests for commit `eb19f8bb`.
- Workflow `42828044536` passed backend (171 tests), frontend build and MySQL migration validation for commit `c55d3bca`; its Spring Boot/browser job was still running.
- Workflow `42828199821` passed backend (171 tests), frontend build and MySQL migration validation after the browser cookie-path fix (`357aa104`); its Spring Boot/browser job was still running through application startup and Playwright at last check.
- Workflow `42827698398` failed on the initial browser test because the test queried cookies at `/` while the refresh cookie is scoped to `/api/v1/auth`; the test now queries the cookie at its actual path. The same run's backend failed because the new role-matrix test fixtures were not rollback-isolated; the test now has a transaction. Subsequent run `42828199821` confirms backend success.
- Workflow `42828297148` for `0e4cefc5` passed all four jobs and 171 backend tests. Workflow `42828454430` for `6585efe6` also passed all four jobs and 171 backend tests, including the new CI schema inventory query and browser auth E2E. The test-isolation fix is verified by workflow `42828934287` for commit `fe7b1181`.


### Verified full CI checkpoint — 2026-11-10

- **Workflow:** `42828454430` — all four jobs passed.
- **Commit:** `6585efe6ae98e009a975c1c63fb83f6e529faf65`.
- **Backend:** 171 tests, 0 failures, 0 errors, 0 skipped.
- **Frontend:** typecheck and production build passed.
- **Database:** Flyway migration validation on MySQL 8.4 passed; the read-only schema/FK/unique-index audit SQL ran after V57/V58 and its artifact upload step passed.
- **Application/browser:** Spring Boot health and Playwright tests passed, including HttpOnly refresh cookie, CSRF rejection, refresh rotation, logout revocation and tenant-filter tampering.
- A later workflow failed only in the browser test because two parallel specs logged in/out the same seeded admin user. The auth-session spec now uses the separate seeded sales user to avoid cross-test session revocation; latest-head workflow `42828592637` is pending.


### Latest verified code head — 2026-11-10

- Workflow `42828934287` passed all four jobs for code commit `fe7b118117f599d1ab275544afa27245ae3e83e0`.
- Backend: 171 tests, 0 failures, 0 errors, 0 skipped.
- Frontend typecheck/production build: PASS.
- MySQL 8.4 migrations and schema inventory artifact: PASS.
- Spring Boot health and browser tests: PASS, including the separate-user auth session test, HttpOnly refresh cookie, CSRF negative cases, refresh rotation, logout revocation and tenant filter tampering.
- Current branch head `00b4d7672b4412d57d0ebdbc75629d9e6f24955d` adds only documentation updates after that verified code commit. Its CI is also running.

**Phase verdict remains NOT MET for both phases** because the complete endpoint/resource matrix, exhaustive tenant schema/NOT NULL and production-data checks, every export/download family, and all worker/branch/customer combinations are still not fully proven.
