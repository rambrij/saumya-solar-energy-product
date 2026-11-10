# Phase 1 and Phase 2 Validation Report

**Repository:** `rambrij/saumya-solar-energy-product`  
**Audit branch:** `audit/phase-2-implementation-gap-analysis`  
**Audit date:** 2026-11-10  
**Sources read:** `saumya-solar-energy-solution-requirements.md`, `saumya-solar-production-ready-srs-hld.md`, and `IMPLEMENTATION_GAP_ANALYSIS.md`.  
**Validation exclusion:** `README.md` was not used, searched, cited, or treated as evidence.

## 1. Executive summary

This is a source-level validation of the requested Phase 1 and Phase 2 scope. It is not a production security certification. The current branch already contains substantial authentication and tenant-isolation implementation. The audit found remaining incomplete coverage and at least two areas that could not be exhaustively verified from the accessible source evidence.

No application code, migrations, or tests were changed in this audit pass. The changes in this commit set are audit artifacts only. This was deliberate: the available GitHub repository integration permits branch file inspection and commits, but does not provide a local checkout, shell, Maven/Node execution, database, or browser runtime. Implementing security changes without the ability to compile, execute migrations, and run the negative tests would be unsafe. Consequently, no gap is represented as fixed or verified.

### Status totals

The status-before column means the state found at the start of this audit on the requested branch. The status-after column means the state after this audit's documentation-only changes. MET requires both implementation evidence and passing test evidence. PARTIAL means a material implementation exists but the full requirement or its test evidence is incomplete. UNVERIFIABLE means the audit could not establish the condition from the available source evidence.

| Status | Before | After |
|---|---:|---:|
| MET | 0 | 0 |
| PARTIAL | 16 | 16 |
| NOT MET | 0 | 0 |
| UNVERIFIABLE | 2 | 2 |
| **Total** | **18** | **18** |

No NOT MET designation is used where an implementation exists but is incomplete; those are PARTIAL. The absence of MET does not mean the platform has no controls. It reflects the strict requirement that each complete requirement be covered by a passing test, which could not be freshly established for the latest branch head in this environment.

## 2. Requirement extraction and traceability matrix

The IDs below are audit IDs created for this report. They are not claimed to be native requirement IDs in the source documents.

| Requirement ID | Source document and section | One-line requirement | Code evidence | Test evidence | Before | After |
|---|---|---|---|---|---|---|
| P1-AUTH-001 | Business requirements §18 Security; SRS §15.3 | Apply abuse throttling by endpoint and client source, with account/user controls for authentication and recovery flows. | `AuthenticationRateLimitFilter.shouldNotFilter/doFilterInternal/accountIdentifier/incrementAndGet`; `ClientAddressResolver.resolve`; `AuthLifecycleService.authenticate`; V52 `auth_rate_limit_bucket`. Since commit `1a79b562`, login/forgot-password/reset-password also use a separate HMAC-derived account + endpoint bucket; refresh/MFA remain IP + endpoint only. | `AuthenticationRateLimitFilterTest`; `AuthLifecycleServiceTest`; no demonstrated per-account limiter test or coverage for all SRS-listed public enquiry/export/webhook routes. | PARTIAL | PARTIAL |
| P1-AUTH-002 | SRS §10.1 IAM-006; §15.1; Business requirements §18 Security | Persist failed password attempts, apply temporary lockout, and define safe expiry/reset behavior. | `AppUser.failedLoginAttempts/lockedUntil`; `AuthLifecycleService.authenticate`; V52. | `AuthLifecycleServiceTest.repeatedFailuresLockAccountAndReturnGenericUnauthorized`; `successfulLoginAfterLockoutExpiryResetsCounter`. Latest-head pass not established here. | PARTIAL | PARTIAL |
| P1-AUTH-003 | SRS §10.1 IAM-005; §13.2; §15.1; §20.7 | Rotate refresh tokens, revoke sessions, detect replay, and revoke the affected token family. | `AuthLifecycleService.rotateRefresh/consumeRefresh/logout`; `RefreshTokenRepository.findByTokenForUpdate/findByFamilyIdForUpdate`; `JwtFilter.doFilterInternal`; V53 family fields as described by the branch gap analysis. | `AuthLifecycleServiceTest` includes refresh-family replay coverage; no current-branch DB-backed concurrent refresh-replay pass was established. | PARTIAL | PARTIAL |
| P1-AUTH-004 | SRS §13.2 Browser session contract; §15.1; §15.3 | Keep access tokens in memory, issue refresh tokens in HttpOnly/Secure/SameSite cookies, and protect cookie-authenticated actions with CSRF validation. | `AuthCookieService.issue/requireCsrf/clear`; `Controller.login/refresh/logout/csrf`; `frontend/src/authenticatedFetch.ts` `rotateSession`; `main.tsx` session bootstrap. | `authenticatedFetch.ts` has no standalone proof here of all browser cookie/CSRF edge cases; React E2E exists but latest-head completion was not confirmed. | PARTIAL | PARTIAL |
| P1-AUTH-005 | Business requirements §12; SRS §9.1–9.3; §20.2 | Enforce the documented role-to-permission matrix, prevent platform privilege escalation, and support vendor-scoped custom roles. | `JwtFilter.doFilterInternal` reloads DB roles/permissions; `AppService.requirePermission`; `AuthLifecycleService.createCustomRole`; `Controller.createVendorEmployee/assignEmployeeRole`; V53 permission catalogue/grants as described in branch notes. Legacy broad permission checks remain in services; no complete custom-role API/editor path was established. | `Release12TenantIntegrationTest` includes platform-role escalation coverage; no complete role × endpoint 401/403 matrix. | PARTIAL | PARTIAL |
| P1-AUTH-006 | SRS §13.1; §15.2; §20.2 | Deny unauthenticated and unauthorized requests consistently, returning 401 for missing/invalid identity and 403 for authenticated but forbidden actions. | `SecurityConfig.chain`; `JwtFilter.doFilterInternal`; service-level permission guards. | `EndpointAuthenticationMatrixTest.everyNonPublicHttpHandlerRequiresAuthentication` checks unauthenticated mappings, not all authenticated role/resource combinations. | PARTIAL | PARTIAL |
| P1-AUTH-007 | SRS §10.1 IAM-007; §15.6 | Persist authentication, reset, MFA, role-change and account-disable security events with safe actor/tenant context. | `AuthenticationAuditService.record`; `AuthLifecycleService.auditAuth`; existing append-only `audit_event` storage. | `AuthenticationAuditServiceTest` and auth lifecycle tests are referenced by branch gap notes; latest-head pass not confirmed here. | PARTIAL | PARTIAL |
| P1-AUTH-008 | SRS §11; §13.2; §15.3 | Show safe rate-limit and session-expiry feedback in the frontend without leaking account existence or sensitive state. | `frontend/src/main.tsx` handles generic login errors and 429; `authenticatedFetch.ts` dispatches `saumya:session-expired`; API helper maps 403/429. Account lockout intentionally returns generic 401; no distinct 423 flow was established. | Frontend auth/refresh tests are referenced by branch notes; no 423 lockout UX test. | PARTIAL | PARTIAL |
| P2-TEN-001 | Business requirements §13; SRS §8.1 and §14.2 | Every tenant-owned entity/table must carry a non-null tenant identifier; global reference data must be explicitly classified. | Tenant IDs are present across many domain entities and repository methods; the full migration/entity inventory was not exhaustively enumerated in this pass. | `Release12TenantIntegrationTest` and migration validation suites cover selected entities, not every table. | UNVERIFIABLE | UNVERIFIABLE |
| P2-TEN-002 | Business requirements §13; SRS §6 and §8.2 | Derive tenant identity from the authenticated principal/server-side trusted resolution, never from a caller-supplied tenant ID. | `JwtFilter.doFilterInternal` reloads user and compares tenant/account type/token version; `Principal`; `TenantExecutionContext`; `AppService` tenant guards. | `Release12TenantIntegrationTest`; `tenant-filter-isolation.spec.ts` and `tenant-react-ui.spec.ts` exist; latest full run not established. | PARTIAL | PARTIAL |
| P2-TEN-003 | SRS §8.3 and §15.2 | Tenant-scope every repository read/write/bulk operation; resource lookups must verify tenant ownership rather than trust a bare identifier. | Tenant-qualified methods exist, e.g. `IntegrationSyncJobRepository.findByIdAndTenantId`, `WarrantyClaimAttachmentRepository.findByIdAndTenantIdAndClaimId`, and service accessors. Not every repository/service call site was reviewed. | `Release12TenantIntegrationTest` covers selected cross-tenant resources; no exhaustive repository inventory test was established. | PARTIAL | PARTIAL |
| P2-TEN-004 | SRS §8.3 and §14.3 | Add composite tenant-aware foreign keys and tenant-scoped unique constraints where relationships are tenant-owned. | Existing branch notes describe V37/V43 composite tenant/branch constraints and later V57/V58 tenant-integrity work. The corresponding latest migration paths and full FK inventory could not be independently located/verified in this inspection. | Branch notes describe MySQL 8.4 migration and integrity preflight jobs; latest-head result and full constraint inventory not confirmed. | UNVERIFIABLE | UNVERIFIABLE |
| P2-TEN-005 | Business requirements §3 and §13; SRS §8.4 and §20.6 | Customers must access only their own quotations, orders, invoices, payments, installations, warranties, tickets and documents within the tenant. | `CustomerAccountService`; `FinanceService.ensureTicketAccess`; `WarrantyClaimAttachmentService.download`; customer-qualified repository methods. | `Release12TenantIntegrationTest` includes customer/claim/attachment checks; all customer resource families are not covered. | PARTIAL | PARTIAL |
| P2-TEN-006 | Business requirements §12–13 and §20 roadmap; SRS §10.14 and §11.3 | Enforce branch/assignment scope server-side for branch-bearing operational records and branch-filtered reports. | `EnterpriseSaasService.filterBranchRecords`; `FinanceService.listInvoices/listTickets`; `OperationsService.installations/warehouses/balances/ledger`; `BranchAnalyticsController/Service`. | `Release12TenantIntegrationTest` branch tests cover multiple record families; NULL-branch visibility remains an unresolved policy. | PARTIAL | PARTIAL |
| P2-TEN-007 | SRS §8.3–8.4; §10.14 RPT-004/RPT-006; §13.7–13.8; §15.4 | Export and file download operations must apply the same tenant, customer and permission checks as transactional reads. | `Phase2OperationsController` CSV/XLSX/PDF export paths; `WarrantyClaimAttachmentService.download`; private attachment services/controllers. | `Release12TenantIntegrationTest.operationsCsvXlsxAndPdfExportsContainOnlyAuthenticatedTenantAggregates`; warranty attachment foreign-tenant/customer/parent tests. Not all download/export families covered. | PARTIAL | PARTIAL |
| P2-TEN-008 | SRS §8.3 and §18 | Scheduled and asynchronous work must carry validated tenant context, isolate tenant data, be idempotent and clear context after execution. | `TenantExecutionContext.open/requireTenantId`; `ServiceSlaScheduler.evaluate`; `WarrantyExpiryScheduler.evaluateWarrantyExpiry`; `SubscriptionExpiryScheduler.expireSubscriptions`; `IntegrationSyncJobRepository.claimIfDue`. | `TenantSchedulerConcurrencyIntegrationTest` covers integration claims and subscription/SLA/warranty scheduler cases; not every async job/provider path. | PARTIAL | PARTIAL |
| P2-TEN-009 | SRS §8.4; §20.1; §20.4; §20.6; Business requirements §13 | Negative tests must prove foreign-tenant/customer read, write and export/download denial, and test delete only where a delete operation is actually specified/exposed. | `Release12TenantIntegrationTest`; `EndpointAuthenticationMatrixTest`; resource-specific tenant-scoped services and repositories. | Existing integration tests cover selected CRUD/export/download paths; no generated full endpoint × verb × identity matrix was demonstrated. | PARTIAL | PARTIAL |
| P2-TEN-010 | Business requirements §13; SRS §6 and §15.2 | Frontend filters, selectors and hidden menu items must never act as authorization; the backend remains authoritative. | `JwtFilter`, controller/service permission guards; frontend navigation and query filters are presentation only. | `frontend/e2e/tenant-filter-isolation.spec.ts`; `frontend/e2e/tenant-react-ui.spec.ts` (two-tenant React workflow is still marked partial in branch notes). | PARTIAL | PARTIAL |

## 3. Conflict log

| Conflict ID | Doc A statement | Doc B statement | Rule applied | Decision |
|---|---|---|---|---|
| CONFLICT-001 | Business requirements §20 Product Roadmap places multiple branches and multiple warehouses in Phase 3 (Enterprise SaaS). | The same business requirements document §24 MVP Delivery Plan places multiple warehouses and branch management in Phase 2 (Operations). SRS §2 describes multiple branches/warehouses as future support; SRS §3.1 includes warehouse management but does not settle branch delivery timing. | Business requirements take precedence over SRS for business behavior. The two business sections conflict with each other, so the document hierarchy cannot resolve this internal contradiction. | For this audit, branch-level negative tests remain in scope because the user explicitly included them. Do not infer a revised commercial release commitment. Product owner must confirm whether branch management and multi-warehouse support are Phase 2 or Phase 3. Branch-unassigned record visibility is separately unresolved. |

No other direct contradiction between the two source documents was established for the Phase 1/2 security and tenant-isolation requirements. The SRS's more prescriptive cookie/CSRF and database-constraint design is treated as technical detail, not as a contradiction of the business document's general JWT/RBAC/tenant-isolation statements.

## 4. Discrepancies in IMPLEMENTATION_GAP_ANALYSIS.md versus inspected code

The existing gap analysis contains historical snapshots and should not be treated as the current branch truth. The following differences were confirmed against files fetched from the requested branch:

1. **Token storage:** the Phase 1 follow-up text says access and refresh tokens remain in localStorage. Current `frontend/src/main.tsx` removes the legacy `session` localStorage entry; `frontend/src/authenticatedFetch.ts` keeps the access session in module memory and stores only the CSRF value in sessionStorage; `AuthCookieService.issue` issues the refresh token as HttpOnly. This historical gap statement is stale. Cookie/CSRF behavior still needs full runtime verification.
2. **Trusted proxy CIDR support:** the gap analysis says the resolver supports exact IP allowlists only. Current `ClientAddressResolver.parseNetwork` parses CIDR prefixes and `TrustedNetwork.contains` checks network membership. Deployment allowlist correctness still needs operational verification.
3. **Browser E2E inventory:** earlier gap-analysis sections state that browser E2E is absent. The requested branch contains `frontend/e2e/tenant-filter-isolation.spec.ts` and `frontend/e2e/tenant-react-ui.spec.ts`. Their existence does not make the full browser acceptance suite complete; the latest React UI test is explicitly described as partial/pending in the branch's continuation notes.
4. **Custom-role capability:** the gap analysis describes custom roles as absent. Current `AuthLifecycleService.createCustomRole` exists, but the inspected controller surface does not establish a complete custom-role CRUD/permission-editor workflow. The precise discrepancy is service-level capability versus missing/incomplete end-to-end integration, not full feature completion.
5. **Branch metadata:** the existing gap analysis header still names `audit/implementation-gap-analysis` as its audit branch, while this report validates `audit/phase-2-implementation-gap-analysis`. Its historical baseline and status totals must not be copied as current branch totals.

No statement from README was used in this comparison.

## 5. Unspecified / needs product decision

1. Whether lockout should return a distinct HTTP 423 or the current generic 401. The source documents require temporary lockout and generic non-disclosing errors, but do not prescribe 423. Returning 423 only for existing accounts may enable account-state inference. Product/security owner must choose a non-enumerating contract before changing it.
2. Exact per-IP, per-account, per-endpoint thresholds, window algorithms, progressive delay policy, and whether account buckets apply to refresh, reset and MFA endpoints. The SRS says to apply rate limits but does not specify numeric thresholds.
3. Rate limits for public enquiry, export and webhook traffic may be enforced at an upstream gateway/WAF rather than in this application. The repository evidence inspected here does not establish production ingress configuration or policy.
4. The intended concurrent-device session policy: logout currently revokes user-wide sessions/token version; replay revokes the affected family. The source documents require rotation/revocation but do not specify whether logout should be device-specific.
5. Branch-unassigned (`branch_id IS NULL`) record visibility for branch-scoped employees and reports. Existing code preserves legacy null-branch visibility; the business rule is not explicit.
6. Whether every resource family should expose DELETE. The source documents require isolation for destructive operations where applicable but also require immutable/retained financial and transaction records. Do not add delete endpoints merely to satisfy a test matrix.
7. Exact ownership and cross-vendor identity policy for customers beyond the SRS statement that separate customer identities per vendor are acceptable in the MVP.
8. Production trusted proxy CIDRs, cookie domain/SameSite policy, CORS origin list, refresh-cookie secure flag, and edge rate-limit configuration.
9. Retention period for authentication audit events and rate-limit buckets beyond the existing two-day bucket cleanup.
10. Whether platform support impersonation will be introduced; SRS §10.15 AUD-005 defines controls only if it is introduced.

## 6. Implementation update after initial audit

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

## 7. Remaining risks and recommended next steps

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

## 8. Gap documents

The following gap files are tracked. P1 rate limiting is **Implemented / Pending Verification** for the account bucket; the remaining gap scope is Open. Other gap files remain Open:

- `docs/gaps/GAP-P1-rate-limiting.md`
- `docs/gaps/GAP-P1-role-permission-matrix.md`
- `docs/gaps/GAP-P1-session-lockout-feedback.md`
- `docs/gaps/GAP-P2-composite-tenant-fks.md`
- `docs/gaps/GAP-P2-cross-tenant-customer-branch-export-tests.md`
- `docs/gaps/GAP-P2-background-job-scoping.md`

## 9. Audit conclusion

**Phase 1: NOT MET.** Core controls exist, but rate-limit dimensions and coverage, least-privilege role enforcement, exhaustive negative tests, and frontend lockout/session behavior are not fully verified.

**Phase 2: NOT MET.** Tenant identity and scoping controls are present and meaningful, but the exhaustive tenant/customer/branch CRUD/export/download matrix and complete database relationship inventory are not proven. Background-job scoping is implemented in several schedulers but not verified for every worker.

No requirement is marked MET based solely on source presence. Production approval should wait for the remaining high-priority evidence and a successful latest-head CI run.
