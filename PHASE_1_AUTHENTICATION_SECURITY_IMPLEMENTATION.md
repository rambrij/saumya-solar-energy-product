# Phase 1 — Authentication and Security Implementation Report

**Repository:** `rambrij/saumya-solar-energy-product`  
**Working branch:** `audit/implementation-gap-analysis`  
**Scope:** Phase 1 authentication and security blockers  
**Status:** **PARTIALLY IMPLEMENTED — validation gates remain open**  
**Requirements reviewed:** `saumya-solar-energy-solution-requirements.md`, `saumya-solar-production-ready-srs-hld.md`, and the current `IMPLEMENTATION_GAP_ANALYSIS.md`.

## 1. Executive summary

The baseline already had BCrypt password hashing, JWT access-token issuance and validation, DB-backed user/tenant/role reload on each protected request, token-version invalidation, refresh tokens, logout, password reset and Platform Super Admin email MFA. These capabilities were retained.

The confirmed gaps were general authentication endpoint throttling, persistent password login lockout, hardcoded access-token lifetime, refresh-token concurrency protection, generic 401/403 response details, and frontend token-refresh/429 behavior.

Implemented in this branch:
- MySQL-persisted account lockout fields and rate-limit buckets, delivered by Flyway migration V52.
- Transactionally serialized login attempts using a pessimistic user-row lookup.
- Configurable login threshold, lockout duration, access-token lifetime and refresh-token lifetime.
- Database-backed, endpoint/IP rate limiting for login, refresh, password-reset request/confirmation and MFA verification.
- Locked refresh-token lookup and transactional consume-and-rotate behavior; replay of a revoked refresh token revokes all active refresh sessions for that user.
- Generic 401/403 API error details.
- Shared frontend JSON API helper refreshes an access token once after 401, stores the rotated refresh token and handles 403/429 messages.
- Focused backend unit tests were added.

**This phase is not declared complete.** No Maven build, test suite, frontend build, Flyway execution or browser E2E run could be executed through the available GitHub repository interface. The test files have been added but have not been run. Permission-catalogue reconciliation, comprehensive tenant-negative tests, audit-event wiring and some frontend direct-fetch paths remain.

## 2. Requirements traceability matrix

| Requirement | Source document and section | Current implementation | Gap | Planned change / delivered change | Test coverage |
|---|---|---|---|---|---|
| Authentication endpoints rate limited | Business requirements §18 Security; SRS §15.3 | No common limiter in baseline; MFA had challenge attempts | Login/refresh/reset endpoints lacked common throttling | Added `AuthenticationRateLimitFilter`, database-backed buckets and configurable limits | `AuthenticationRateLimitFilterTest` added; not executed |
| HTTP 429 and safe error body | SRS §15.3 | No shared authentication rate-limit response | No 429 contract | Filter returns 429, Retry-After and generic detail | Same filter test; not executed |
| Failed login attempts counted | SRS IAM-006 | Controller directly checked BCrypt and returned 401 | No persistent counter | Added `failed_login_attempts` and transactional locked user lookup | `AuthLifecycleServiceTest` added; not executed |
| Account lockout | SRS IAM-006; SRS §15.1 | No password lockout | No lockout expiry or configurable threshold | Added `locked_until`, max-attempt and duration properties | Lockout unit test added; not executed |
| Reset attempts after successful login | SRS IAM-006 | No counter existed | No reset behavior | Successful authentication clears count and lockout | Unit test added; not executed |
| Lockout expiry | SRS IAM-006 | No lockout existed | No expiry path | Expired lockout is cleared before credential check | Unit test added; not executed |
| Disabled user blocked | SRS §8.2, §15.1 | Existing enabled check in JWT/refresh path | Explicit login test missing | Login service denies disabled user with generic 401 | Unit test added; not executed |
| Disabled tenant blocked | SRS §8.2, §15.1 | Existing `tenantIsActive` check on login/refresh/JWT | Dedicated negative tests incomplete | Existing tenant check retained | No new integration test |
| Short-lived access token | SRS IAM-005; §15.1 | Hardcoded 7,200 seconds | Not configurable; too long for stated short-lived intent | Configurable default 900 seconds | No new JWT expiry test |
| Refresh token rotation | SRS IAM-005; §13.2 | Existing revoke-and-issue sequence | Refresh row read was not locked | Added `findByTokenForUpdate` and transactional rotation service | No new concurrency integration test |
| Refresh replay prevention | SRS §20.7 | Existing replay branch revoked other live refresh tokens | Concurrent reuse race not addressed by locked read | Revoke consumed token under pessimistic row lock; replay revokes all active user sessions | No replay/concurrency test added |
| Refresh status validation | SRS §15.1 | Existing user enabled and tenant active check | Dedicated tests missing | Existing checks retained in rotation | No new integration test |
| Logout/revocation | SRS IAM-001/IAM-005 | Existing refresh revocation and token-version increment | Existing behavior is user-wide across active refresh sessions | Retained to avoid contract regression | No new logout integration test |
| Backend RBAC | SRS §9.1–9.3, §15.2 | Existing service-level permission checks; JWT filter reloads DB permissions | Catalogue and role definitions differ from SRS; coverage incomplete | Preserved current permission grants; documented as a residual gap | Existing `Release2SecurityTest` only |
| Tenant isolation | Business requirements §13; SRS §8 and §20.1 | Many tenant-scoped repository methods | Exhaustive guessed-ID tests absent | No broad rewrite without verified endpoint inventory | Existing cross-tenant category test only |
| Frontend session expiry | SRS §11 and §15.1 | 401 caused local session removal and page reload | No refresh-token use by common JSON client | Added one refresh attempt and retry for shared JSON API calls | No frontend test |
| Frontend 403/429 | SRS §15.3 | Generic API error handling | No consistent user feedback | Added permission-specific and rate-limit-specific messages in shared API helper | No frontend test |
| Security audit events | SRS IAM-007; §15.6 | Audit event table/entity exists for business audit | Authentication events not wired | Not completed | Missing |
| Browser token storage | SRS §15.1 and §18.3 | Access JWT is held in memory; refresh credential is stored in an HttpOnly Secure SameSite cookie; CSRF token is in sessionStorage | No raw access or refresh token is persisted in localStorage; new tabs obtain CSRF through GET /auth/csrf | Retained short-lived bearer access-token response for in-memory use; refresh token is removed from JSON and request body | AuthCookieServiceTest, authenticatedFetch tests and Release12 integration tests |

## 3. Existing capabilities reused

| Capability | Existing implementation | Treatment |
|---|---|---|
| Password hashing | `SecurityConfig.passwordEncoder()`, BCrypt cost 12 | Reused |
| JWT issue/parse | `JwtService.issue/parse` | Reused; configurable expiry |
| JWT validation | `JwtFilter.doFilterInternal` | Reused |
| Identity/tenant reload | `AppUserRepository`, `TenantRepository` | Reused |
| Permission reload | User roles and permissions read from DB in JWT filter | Reused |
| Immediate access-token revocation | `app_user.token_version` | Reused |
| Refresh tokens | `RefreshToken`, `RefreshTokenRepository` | Reused; row lock added |
| Password reset | `AuthLifecycleService.forgot/reset` | Reused; lockout reset added on password reset |
| MFA | `MfaChallenge`, existing attempt/expiry validation | Reused; endpoint throttled |
| Schema management | Flyway | Added V52 only |
| Frontend API client | `api()` in `frontend/src/main.tsx` | Extended |

## 4. Verified gaps and implementation details

### 4.1 Rate limiting

Added `AuthenticationRateLimitFilter` using the existing MySQL datasource through `JdbcTemplate`. A hashed key identifies the endpoint and remote address; raw addresses are not stored in the bucket key. The upsert increments the current fixed-window counter atomically at the database row. Requests beyond the configured limit receive 429 with Retry-After.

Protected POST routes:
- `/api/v1/auth/login`
- `/api/v1/auth/refresh`
- `/api/v1/auth/forgot-password`
- `/api/v1/auth/reset-password`
- `/api/v1/auth/mfa/verify`

Default configuration:
- Login and refresh: 10 requests per window.
- Password recovery: 5 requests per window.
- MFA verification: 10 requests per window.
- Window: 60 seconds.

The rate-limit filter is disabled as a servlet-container filter and explicitly inserted into Spring Security's filter chain to avoid duplicate execution.

### 4.2 Password lockout

`AuthLifecycleService.authenticate` now:
1. Performs a case-insensitive user lookup with a pessimistic write lock.
2. Rejects unknown, disabled, inactive-tenant and currently locked accounts with the same generic 401 reason.
3. Clears an expired lockout and resets the counter before evaluating the next credential.
4. Increments and persists the failed counter for a wrong password.
5. Sets a lockout expiry at the configured threshold.
6. Resets the counter/lock on successful authentication and records last login time.

The controller delegates login credential validation to this service. Password reset also clears lockout fields and increments token version/revokes active refresh sessions as before.

### 4.3 Token lifecycle

- JWT access-token expiry is configurable with a default of 900 seconds.
- Refresh-token lifetime is configurable with a default of seven days.
- Refresh token lookup is performed with a pessimistic write lock.
- `rotateRefresh` wraps consume-and-issue in one transaction.
- Reuse of a revoked refresh token revokes all still-active refresh tokens belonging to that user.
- Existing user enabled, tenant active, account type, tenant ID and token-version validation on protected access remain in place.

**Design qualification:** The implementation has a user-wide refresh-session revocation policy rather than an explicit token-family identifier. This is stricter than per-device revocation but does not model the token-family concept separately.

### 4.4 Authorization and tenant scope

The existing authorization path is retained:
- `SecurityConfig` is deny-by-default for non-public API routes.
- `JwtFilter` reloads the current user, active tenant, roles and permissions from the database.
- Existing service-level `requirePermission` and tenant-scoped repository lookups remain authoritative.
- Existing `Release2SecurityTest` covers a vendor without catalog permission and a cross-tenant category reference.

Fine-grained SRS permission codes are now the authorization source for the standard vendor role matrix. V55 removes deprecated broad grants from every tenant role, V56 removes platform-only permissions from tenant roles, new vendor administrators are provisioned without those codes, and custom roles are limited to the vendor permission allowlist. Sales and service/installation assigned-resource scopes use permission capabilities rather than hard-coded role-name checks where the SRS defines assignment permissions. Platform Support is a read-only, audited role.

### 4.5 Frontend behavior

The shared `api()` helper now:
- Reads the in-memory access session before making a protected request.
- Uses the HttpOnly refresh cookie and CSRF double-submit token after a 401.
- Stores only the rotated access JWT in memory; the browser receives the rotated refresh cookie through Set-Cookie.
- Retries the original request once and clears the in-memory session when refresh fails.
- Obtains the CSRF value from GET /auth/csrf when a new tab has no sessionStorage state.
- Uses clear, non-sensitive messages for 403 and 429.

Direct fetch calls used by some binary downloads/uploads and bespoke customer address flows do not all use the shared helper. Those flows need consolidation or equivalent refresh handling before the frontend session-expiry requirement can be marked complete.

## 5. Backend changes

| File | Change |
|---|---|
| `backend/src/main/java/com/saumyasolar/domain/AppUser.java` | Added failed attempt count, lock expiry and last-login timestamp |
| `backend/src/main/java/com/saumyasolar/repo/AppUserRepository.java` | Added pessimistically locked case-insensitive lookup |
| `backend/src/main/java/com/saumyasolar/repo/RefreshTokenRepository.java` | Added pessimistically locked token lookup |
| `backend/src/main/java/com/saumyasolar/service/AuthLifecycleService.java` | Added login lockout and transactional refresh rotation; configurable refresh TTL; unlock on reset |
| `backend/src/main/java/com/saumyasolar/security/JwtService.java` | Configurable access-token expiry |
| `backend/src/main/java/com/saumyasolar/security/AuthenticationRateLimitFilter.java` | New DB-backed authentication endpoint limiter |
| `backend/src/main/java/com/saumyasolar/security/SecurityConfig.java` | Inserted rate limiter into Spring Security chain; disabled duplicate servlet registration |
| `backend/src/main/java/com/saumyasolar/api/Controller.java` | Login now delegates to lifecycle service; refresh uses atomic rotation |
| `backend/src/main/java/com/saumyasolar/api/ApiExceptionHandler.java` | Generic 401/403 response details |

## 6. Frontend changes

| File | Change |
|---|---|
| `frontend/src/main.tsx` | Shared API helper retries once after successful refresh; clearer 401/403/429 behavior; logout uses latest rotated refresh token |

No UI redesign or role-navigation rewrite was performed.

## 7. Database changes

Flyway migration:
- `backend/src/main/resources/db/migration/V52__authentication_lockout_and_rate_limit.sql`

Adds to `app_user`:
- `failed_login_attempts INT NOT NULL DEFAULT 0`
- `locked_until TIMESTAMP(6) NULL`
- `last_login_at TIMESTAMP(6) NULL`
- Index on `locked_until`

Adds `auth_rate_limit_bucket`:
- `bucket_key CHAR(64)` primary key
- `window_started_at`
- `request_count`
- `updated_at`
- Index on `updated_at`

The migration is additive and uses the repository's Flyway sequence. It has not been run against a live or test MySQL instance in this execution.

## 8. Security decisions

- Keep BCrypt cost 12; do not replace the working password encoder.
- Keep JWT identity validation against the current database user/tenant/role state.
- Keep generic 401 for invalid credentials, disabled users, inactive tenants and locked accounts to reduce account-state disclosure.
- Keep 403 for authenticated callers lacking permission.
- Use 429 for rate-limit exhaustion.
- Persist lockout and rate-limit state in MySQL so it is shared across application instances.
- Do not trust arbitrary client-supplied forwarded IP headers. The current filter uses `getRemoteAddr()`; deployments behind a proxy must ensure the application sees a verified client address or add an explicitly trusted-proxy resolver.
- No secrets or passwords were added to source code.
- No public API contract shape was changed; the refresh request and response remain the existing payloads.

## 9. Configuration changes

New environment-backed properties in `backend/src/main/resources/application.yml`:

| Property | Environment variable | Default |
|---|---|---:|
| `app.auth.access-token-ttl-seconds` | `ACCESS_TOKEN_TTL_SECONDS` | 900 |
| `app.auth.refresh-token-ttl-days` | `REFRESH_TOKEN_TTL_DAYS` | 7 |
| `app.auth.lockout.max-failed-attempts` | `AUTH_MAX_FAILED_ATTEMPTS` | 5 |
| `app.auth.lockout.duration-minutes` | `AUTH_LOCKOUT_DURATION_MINUTES` | 15 |
| `app.auth.rate-limit.login-per-window` | `AUTH_LOGIN_RATE_LIMIT` | 10 |
| `app.auth.rate-limit.recovery-per-window` | `AUTH_RECOVERY_RATE_LIMIT` | 5 |
| `app.auth.rate-limit.mfa-per-window` | `AUTH_MFA_RATE_LIMIT` | 10 |
| `app.auth.rate-limit.window-seconds` | `AUTH_RATE_LIMIT_WINDOW_SECONDS` | 60 |

Values are clamped to a minimum of one for lockout/rate-limit counts and durations. Access-token TTL must be at least 60 seconds.

## 10. Tests added or updated

### Added

- `backend/src/test/java/com/saumyasolar/service/AuthLifecycleServiceTest.java`
  - repeated failures trigger lockout
  - locked account cannot log in with correct password before expiry
  - expired lockout clears on successful login
  - disabled user cannot authenticate

- `backend/src/test/java/com/saumyasolar/security/AuthenticationRateLimitFilterTest.java`
  - threshold exceeded returns 429 and Retry-After
  - non-authentication route bypasses limiter

### Existing relevant tests

- `backend/src/test/java/com/saumyasolar/Release2SecurityTest.java`
  - principal tenant/role/permission boundaries
  - vendor without catalog permission is denied
  - product cannot reference another tenant's category

### Not yet covered by new automated tests

- Missing, malformed and expired JWT over HTTP.
- Refresh token replay and simultaneous refresh race.
- Logout invalidates the expected access-token/session scope.
- Inactive tenant refresh and disabled user refresh.
- 401 vs 403 controller-level integration behavior.
- Horizontal/vertical privilege escalation across every role.
- Every resource family cross-tenant guessed-ID negative tests.
- Frontend refresh, lockout and 429 components.
- Per-route rate-limit policy assertions.
- Authentication audit event persistence.

## 11. Commands executed and results

**Repository operations:** files and branch were inspected and updated through GitHub. The current branch is `audit/implementation-gap-analysis`.

**Not executed in this environment:**
- `cd backend && ./mvnw test` (or `mvn test`)
- `cd backend && ./mvnw verify`
- `cd frontend && npm ci`
- `cd frontend && npm test`
- `cd frontend && npm run build`
- Flyway migration against MySQL
- Browser E2E / security negative test suite

GitHub Actions was available. Results are recorded in the Phase 1 follow-up addendum below. Earlier runs passed MySQL migration V53 and frontend build, but backend compile/test and frontend component-test failures were observed on intermediate commits. The latest commit must pass the full workflow before completion.

## 12. Known limitations and remaining gaps

1. Authentication success, failure and lockout events are not yet recorded to `audit_event`, despite SRS IAM-007 and §15.6.
2. The rate-limit table has no scheduled retention cleanup; add a bounded cleanup job before long-running production use.
3. Rate limiting is based on remote address + route. Behind a proxy, verify trusted ingress address forwarding; never trust arbitrary forwarded headers.
4. Refresh replay revokes all sessions for a user rather than a distinct refresh-token family.
5. The browser still stores tokens in localStorage. A secure HttpOnly cookie approach requires CSRF, CORS, cookie and API-client design changes.
6. Direct frontend fetch paths for uploads/downloads and customer addresses do not all share the automatic refresh helper.
7. The permission catalogue/role matrix differs from SRS. A reviewed mapping and migration are required.
8. Tenant isolation tests cover only selected flows, not every protected resource and role combination.
9. Added tests and migration have not been executed. Runtime behavior and build compatibility remain unverified.
10. Rate-limit defaults require production traffic sizing and ingress topology review.

## 13. Manual verification steps

Only after running the migration in a disposable/test MySQL database:

1. Set a test user to an enabled state with a known BCrypt password. Submit five incorrect passwords (or the configured threshold); verify all responses are generic 401 and the account is locked.
2. Attempt the correct password while locked; verify authentication is still denied.
3. Set/await lock expiry and verify successful login resets the counter and lock.
4. Exceed each authentication endpoint's configured IP window; verify 429, Retry-After and no sensitive response data.
5. Refresh once; verify the old refresh token cannot be reused and replay revokes remaining user sessions.
6. Disable a user and suspend a tenant; verify refresh fails and protected access returns 401.
7. Verify an authenticated user without permission receives 403 and a missing/invalid token receives 401.
8. Verify frontend refresh rotates stored tokens and invalid refresh clears the local session.
9. Run two backend instances against the same database and verify rate-limit counters are shared.
10. Verify the deployment proxy overwrites forwarded-address headers and that the application uses the verified client address.

## 14. Rollback considerations

- Application rollback without database rollback is generally compatible because the V52 migration is additive, but old application code ignores the new columns/table.
- Do not manually drop V52 columns/table in a production database after the migration has been applied.
- Roll back the application code and leave the additive schema in place; use a separately reviewed forward migration if schema removal is ever required.
- Keep previous environment variable values/defaults available during rollback.
- Reverting the frontend while the backend is updated is API-compatible because existing login/refresh request and response shapes are retained.
- Before rollback, consider active accounts currently locked by the new policy; coordinate unlock/reset operations through the approved administrative process rather than editing production rows ad hoc.

## 15. Completion decision

**Phase 1 is not yet complete.** Rate limiting, account lockout, refresh rotation hardening, configurable token lifetimes, safe status responses and shared-API frontend refresh handling have been implemented in source. Completion is blocked by unexecuted build/tests/migration, missing authentication audit events, incomplete full-matrix negative tests, permission-catalogue reconciliation, direct-fetch frontend gaps, and unresolved token storage/proxy deployment decisions.

Do not promote this branch as production-ready until these gates are resolved and test evidence is attached.


---

## 16. Follow-up hardening — 2026-11-09

### 16.1 Refresh-token family revocation

Added V53:
- `refresh_token.family_id BINARY(16) NOT NULL`
- `refresh_token.replaced_by_token_id BINARY(16)`
- `refresh_token.created_at TIMESTAMP(6)`
- Family and user/family indexes.
- Self-referencing replacement-token foreign key.

New refresh sessions receive a new family ID. Rotation retains the family ID and links the old token to the replacement. Reuse of a revoked token revokes all still-active members of that family under a pessimistic lock. Legacy token rows are assigned individual families during migration; the fallback path revokes all sessions for a user if a legacy null family is encountered.

Evidence:
- `RefreshToken.familyId`, `RefreshToken.replacedByTokenId`
- `RefreshTokenRepository.findByFamilyIdForUpdate`
- `AuthLifecycleService.newRefresh/rotateRefresh/consumeRefresh`
- `V53__refresh_token_families_and_srs_permissions.sql`
- `AuthLifecycleServiceTest.replayOfRotatedRefreshTokenRevokesEveryTokenInItsFamily`

### 16.2 Authentication audit-event persistence

Reused the existing append-only `audit_event` schema and repository. No duplicate audit table was introduced.

Events now written include:
- Login success, invalid credentials, locked account and lockout threshold.
- Refresh rotation and refresh replay.
- Logout/session revocation.
- Password-reset request and completion.
- MFA challenge delivery success/failure and verification.
- Vendor employee role assignment and account enable/disable.

The event records actor/tenant where known, safe action/outcome/reason, resolved source IP, truncated user agent and request ID. Passwords, raw refresh tokens, OTPs and reset tokens are not recorded. Audit persistence joins the caller transaction; audit exceptions are not silently suppressed.

Evidence:
- `AuthenticationAuditService.record`
- `AuthLifecycleService.auditAuth`
- `AuthLifecycleService.authenticate/rotateRefresh/consumeRefresh/logout/forgot/reset/verifyMfaChallenge/createEmployee/setEmployeeEnabled`
- `AuditEventRepository` and existing `audit_event` append-only schema
- `AuthenticationAuditServiceTest`

### 16.3 Trusted proxy and rate-limit retention

Added `ClientAddressResolver` and injected it into `AuthenticationRateLimitFilter`. Forwarded client IPs are only considered if the immediate remote address is in the explicit trusted proxy allowlist. The allowlist is configured through `TRUSTED_PROXY_IPS`; only exact IP addresses are supported, not CIDR ranges.

Added `AuthenticationRateLimitCleanup.removeExpiredBuckets`, scheduled by the existing `@EnableScheduling` application configuration. It deletes rate-limit buckets older than two days. Configure the schedule through `AUTH_RATE_LIMIT_CLEANUP_CRON`.

Evidence:
- `ClientAddressResolver.resolve`
- `AuthenticationRateLimitFilter.requestPath/doFilterInternal`
- `AuthenticationRateLimitCleanup.removeExpiredBuckets`
- `application.yml`
- `ClientAddressResolverTest`

### 16.4 Frontend refresh coverage and concurrency

Extracted the common helper to `frontend/src/authenticatedFetch.ts`. It:
- Preserves `Headers`, FormData and binary response handling.
- Retries a protected request once after a 401 and successful refresh.
- Updates the stored rotated refresh token.
- Uses a shared in-flight refresh promise to prevent parallel 401 responses from replaying the same refresh token.
- Reuses a newer session written by another request before attempting refresh.
- Dispatches `saumya:session-expired` when refresh fails; the App clears its authenticated UI state.

All identified authenticated direct API calls in these components now use the helper:
- `main.tsx`
- `EnterpriseSaasPanel.tsx`
- `FinanceServicePanel.tsx`
- `OfflineSyncPanel.tsx`
- `OperationsPanel.tsx`
- `PackageImageUpload.tsx`
- `Phase2AdminPanel.tsx`
- `PlatformAnalyticsPanel.tsx`
- `VendorAnalyticsPanel.tsx`

The remaining direct calls in `PublicHome.tsx` are public, unauthenticated endpoints.

Frontend tests added in `frontend/tests/authenticatedFetch.test.ts` for single refresh/retry, concurrent refresh coalescing and invalid-session cleanup.

### 16.5 Permission catalogue and enforcement qualification

V53 inserts the fine-grained permission codes defined by the SRS and grants predefined employee role permissions. `DataSeeder.PERMISSIONS` and `employeeRolePermissions()` were extended so subsequently created tenants receive the same catalogue/grants. A negative integration test verifies a vendor administrator cannot create an employee with `PLATFORM_SUPER_ADMIN`.

However, the existing backend still checks legacy broad codes such as `CUSTOMER_MANAGE`, `LEAD_MANAGE`, `SURVEY_MANAGE`, `QUOTATION_MANAGE` and `CATALOG_MANAGE` in multiple services. These were retained to avoid breaking existing workflows. The fine-grained grants are not yet the sole enforcement source, and some assigned-record scopes and custom role administration are not implemented. Therefore role/permission compliance remains **PARTIAL**.

Evidence:
- `V53__refresh_token_families_and_srs_permissions.sql`
- `DataSeeder.PERMISSIONS`
- `DataSeeder.employeeRolePermissions()`
- `AppService.requirePermission`
- `Controller.createEmployee`
- `Release12TenantIntegrationTest.vendorAdminCannotEscalateEmployeeToPlatformSuperAdmin`

### 16.6 HttpOnly cookie review

The browser currently persists both access and refresh tokens in localStorage. This does not satisfy the SRS recommendation for HttpOnly refresh-token storage and leaves the refresh credential exposed to same-origin script execution.

A safe migration requires coordinated cookie issuance/clearing, Secure/SameSite policy, credentialed CORS, CSRF protection, frontend session bootstrap and compatibility handling for existing clients. This was reviewed but not migrated as an untested partial change. It remains a **production-readiness blocker**.

### 16.7 Additional tests

Added:
- `backend/src/test/java/com/saumyasolar/security/ClientAddressResolverTest.java`
- `backend/src/test/java/com/saumyasolar/service/AuthenticationAuditServiceTest.java`
- Refresh family replay case in `AuthLifecycleServiceTest`
- Role escalation and missing/malformed bearer-token cases in `Release12TenantIntegrationTest`
- `frontend/tests/authenticatedFetch.test.ts`

### 16.8 CI validation history

- MySQL 8.4 Flyway validation through V53 passed in workflow run [42657544449](https://github.com/rambrij/saumya-solar-energy-product/actions/runs/42657544449).
- An intermediate backend run failed compilation because an edit to `DataSeeder.java` inadvertently removed existing demo seeding methods. Both methods were restored in commit `bec3748ad55fb001d41ff3e8ca7b4e64fc07ac37`.
- An intermediate rate-limit test failed because a mock request exposed an empty servlet path. `AuthenticationRateLimitFilter.requestPath` now falls back to the request URI.
- The first frontend test run after helper extraction failed on an escaped slash regex. It was corrected in commit `8b33a8195761488c1172ee2e7aee0c361ed1200f`.
- The latest workflow run after these corrections is the source of truth for pass/fail. The repository workflow runs on every branch push; intermediate failures must not be treated as final results.

### 16.9 Final status

**Phase 1 remains PARTIALLY IMPLEMENTED.** Additional hardening has been committed, but completion is blocked until:
1. Latest backend compilation/tests pass.
2. Latest frontend component tests and production build pass.
3. Latest MySQL migration validation and Spring Boot startup pass.
4. Fine-grained permissions replace legacy broad permission enforcement without breaking documented workflows.
5. HttpOnly refresh-cookie and CSRF design is implemented and tested.
6. Full tenant-resource and refresh replay concurrency integration tests pass.
7. Production proxy allowlist and scheduler configuration are verified in the deployed environment.

Do not merge or deploy as production-ready until all blockers above are closed.


## 17. Latest verified CI results

Workflow run [42659338279](https://github.com/rambrij/saumya-solar-energy-product/actions/runs/42659338279) for commit `8888b5dd22df6e3270d0d6cbd9eaaae23fc9b12d` completed successfully:
- Backend compile and tests: **PASS — 108 tests, 0 failures, 0 errors, 0 skipped**.
- Frontend component tests: **PASS**.
- Frontend production build: **PASS**.
- MySQL 8.4 migrations V1–V53: **PASS**.
- Spring Boot package/startup/health check against MySQL 8.4 and Mailpit: **PASS**.

The backend test run exposed a pre-existing transactional boundary in `EnterpriseSaasService.enforceUserLimit`: its pessimistic tenant lock was called without an active transaction on the subscribed-tenant path. Added `@Transactional` to that service method. The new role-escalation integration test now exercises that path.

H2 integration-test support was also corrected:
- `backend/src/test/resources/schema-test.sql` creates the rate-limit bucket table for the test profile.
- `application-test.yml` loads that schema and raises only test-profile rate limits so unrelated integration tests are not throttled.
- The rate-limit bucket update now uses portable atomic UPDATE/INSERT-with-duplicate-retry SQL rather than MySQL-only `ON DUPLICATE KEY UPDATE`.

The subsequent proxy-address parser hardening commit `681c3267cf9b1adeb00d83432955f6283054b8c6` was also fully validated by workflow run [42659414140](https://github.com/rambrij/saumya-solar-energy-product/actions/runs/42659414140): backend compile/tests passed (108 tests, 0 failures/errors), frontend component tests/build passed, MySQL migrations through V53 passed, and Spring Boot startup/health passed. The only commits after that validated source commit update these two documentation files; no source code changed after the passing run.


---

## 18. Production blocker closure update — 2026-11-09

### 18.1 Status

**Repository implementation: substantially complete.** The three code-level blockers previously called out have been addressed in this branch: SRS fine-grained authorization and custom roles, HttpOnly refresh-cookie sessions with CSRF protection, and expanded cross-tenant/replay/security verification.

**Deployment-level reverse-proxy verification remains pending.** No production Render settings were changed and no deployment was triggered. The deployment workspace must be confirmed before reading or changing its configuration.

### 18.2 Requirements traceability

| Requirement | Source | Implementation and evidence | Status |
|---|---|---|---|
| Authentication event persistence | SRS IAM-007 | Existing append-only audit_event reused by AuthenticationAuditService; AuthLifecycleService records login success/failure/lockout, refresh rotation/replay, logout, password reset, MFA, role and account-state changes. | IMPLEMENTED |
| Fine-grained role catalogue | SRS §9.1–9.3 | DataSeeder.employeeRolePermissions reconciled to standard SRS roles; core AppService, CatalogAdminService, OperationsService, FinanceService, SurveyLifecycleService, OfflineSyncService, PaymentGatewayCheckoutService, QuotationRevisionService, Phase2OperationsController, ServiceCommentController and WarrantyClaimAttachmentService use operation-specific permissions. | IMPLEMENTED IN CODE; regression verification required |
| Existing employee grant cleanup | SRS §9.2 | V54__reconcile_employee_role_permissions.sql removes legacy broad permission grants from predefined employee roles. Vendor Admin retains vendor-level administration; platform permissions remain excluded from custom roles. | IMPLEMENTED; MySQL migration validation passed in run 42669708678 |
| Custom role management and assignment | SRS IAM-004, §9.1 and §13.3 | GET /permissions, GET/POST /roles, PATCH/DELETE /roles/{roleId}, PATCH /users/{userId}/role; RoleManagementPanel; allowlist prevents platform and deprecated broad permissions; reserved roles, self-demotion, administrator reassignment and deletion of roles in use are blocked. | IMPLEMENTED |
| Assigned-resource scope | SRS §9.3 | Sales Executives are restricted to assigned leads, linked enquiries/customers, quotations and orders; field engineers are scoped to assigned surveys, installations, service tickets, customers and teams. | IMPLEMENTED; see negative integration tests |
| Refresh cookie and CSRF | SRS IAM-005 and §18.3 | AuthCookieService issues an HttpOnly, Secure, SameSite refresh cookie scoped to /api/v1/auth and a CSRF cookie/token. Refresh/logout require X-CSRF-Token; refresh credential is removed from the JSON response. | IMPLEMENTED |
| Frontend token storage | SRS §18.3 | Access token is memory-only; refresh token is HttpOnly cookie; CSRF token is sessionStorage; old localStorage session key is removed at app startup. | IMPLEMENTED |
| Automatic frontend refresh | SRS IAM-005 | authenticatedFetch centralizes cookie refresh, concurrency coalescing, retry, FormData/binary handling and session-expiry event handling. All authenticated direct upload/download paths use it; public requests remain unauthenticated. | IMPLEMENTED |
| Proxy-aware rate limiting | SRS IAM-006 | ClientAddressResolver trusts forwarded IP only when immediate remote address is in TRUSTED_PROXY_IPS; rate-limit bucket cleanup is scheduled. CIDR ranges are not supported; exact IP allowlist is required. | CODE IMPLEMENTED; deployed proxy values pending |
| Tenant boundary verification | SRS §9.3 | Release12TenantIntegrationTest covers cross-tenant order history, quotation revisions, invoice items, service requests and installation checklist access. | IMPLEMENTED AND TESTED |
| Refresh replay verification | SRS IAM-005 | Database-backed concurrent refresh replay test verifies one rotation succeeds and replay revokes the entire token family. | IMPLEMENTED AND TESTED |

### 18.3 Database changes

- V52: authentication lockout and rate-limit persistence.
- V53: refresh-token family/replacement linkage and SRS permission catalogue.
- V54: removes legacy broad grants from predefined employee roles.
- V55: removes deprecated broad grants from all tenant roles (including old custom roles) and creates the restricted PLATFORM_SUPPORT role with AUDIT_VIEW and TENANT_VIEW.
- V56: removes PLATFORM_MANAGE, SUBSCRIPTION_MANAGE, TENANT_VIEW and TENANT_UPDATE from every tenant-owned role.
- No manual SQL step is required; Flyway owns the migration sequence.

### 18.4 Security tests added or updated

- AuthCookieServiceTest: HttpOnly/Secure/SameSite attributes, CSRF double-submit validation and SameSite=None requiring Secure.
- AuthenticationRateLimitCleanupTest: verifies expiry cleanup uses the configured two-day retention window.
- AuthLifecycleServiceTest: custom role permission allowlist and tenant-scoped employee role assignment.
- Release12TenantIntegrationTest: concurrent refresh replay/family revocation, missing-CSRF rejection, no refresh token in JSON, cross-tenant access across five resource families, custom sales/service assigned-resource isolation, platform-support read-only access/audit, complete seeded-role permission checks, legacy/platform grant denial and privilege-escalation audit persistence.
- Existing authentication lockout, rate-limit, disabled-user/tenant, JWT, 401/403 and tenant tests retained.
- Frontend authenticatedFetch tests cover cookie refresh/retry, parallel refresh coalescing, reload bootstrap and invalid-session cleanup.
- Role navigation tests updated to SRS fine-grained permission codes.

### 18.5 CI evidence

Workflow run [42669708678](https://github.com/rambrij/saumya-solar-energy-product/actions/runs/42669708678) completed successfully for source commit c2b4ec806f97b1a500bfcedc60823bd7d6a8859d:
- Backend compile/tests: 116 tests, 0 failures, 0 errors, 0 skipped.
- Frontend typecheck/component tests/production build: PASS.
- MySQL 8.4 Flyway migrations through V54: PASS.
- Spring Boot package/startup/health check against MySQL 8.4 and Mailpit: PASS.

A further CI run was triggered after adding the dedicated rate-limit cleanup test and documentation updates. Its result is the final validation gate; do not treat the earlier run as evidence for files committed afterward.

### 18.6 Production environment requirements

Before enabling the cookie session flow in the deployed environment, verify:

- CORS_ORIGINS contains the exact browser application origin(s); do not use a wildcard with credentialed CORS.
- AUTH_REFRESH_COOKIE_SECURE=true.
- AUTH_REFRESH_COOKIE_SAME_SITE=None for cross-site frontend/API origins; use Lax or Strict where deployment is same-site. SameSite=None requires Secure.
- AUTH_REFRESH_COOKIE_DOMAIN is set only when a shared parent domain is intentionally used; otherwise keep the cookie host-only.
- TRUSTED_PROXY_IPS contains only the immediate trusted reverse-proxy IP addresses or narrowly scoped CIDR networks. The resolver intentionally ignores forwarded headers from untrusted peers and rejects catch-all /0 networks.
- HTTPS is enforced end to end and the proxy forwards the real client address only from trusted hops.

The Render workspace selection has not been confirmed, so the deployed environment variables and actual proxy topology have not been inspected or modified. This is the only outstanding production-operations verification item recorded here.

### 18.7 API compatibility and rollback

The browser refresh contract is intentionally changed to HttpOnly cookies: POST /auth/refresh and POST /auth/logout no longer accept a refresh token in the request body. The frontend client was updated in the same branch. Any external/mobile clients using the old body contract must be migrated before rollout.

Rollback must be coordinated: reverting the frontend while retaining cookie-only backend endpoints, or reverting the backend while leaving cookie-only frontend calls, breaks browser sessions. The V54 migration is additive in effect only with respect to existing rows but removes deprecated broad role grants; do not manually re-add those grants during rollback. Revert application code and migration only through an approved Flyway recovery plan.

### 18.8 Remaining limitations

- Deployed CORS and TRUSTED_PROXY_IPS values are not yet verified because the Render workspace has not been selected.
- SameSite=None cookies may be blocked by browser third-party-cookie policies when the frontend and API are on unrelated registrable domains; prefer same-site subdomains or a same-origin reverse proxy.
- Cross-tab browser refresh coordination is not implemented; refresh single-flight currently coalesces requests within one browser tab. A simultaneous refresh from multiple tabs is treated as replay and revokes the token family.
- External clients must adopt the cookie refresh contract before production rollout.


---

## 19. Phase 1 production-hardening addendum — 2026-11-09

This addendum supersedes conflicting earlier statements in Sections 1–18 about localStorage token persistence, legacy permission grants, proxy CIDR support, missing assigned-scope tests and migration coverage.

### 19.1 Final authorization reconciliation

- SRS standard roles are reconciled in `DataSeeder.employeeRolePermissions()`.
- `PLATFORM_SUPPORT` is a global, read-only role with only `TENANT_VIEW` and `AUDIT_VIEW`; tenant directory reads and audit-log reads are recorded as security audit events.
- `TenantAdminService.authorizeRead` permits support reads; tenant mutation remains restricted to `PLATFORM_SUPER_ADMIN`.
- V55 removes legacy broad permission grants from all tenant-owned roles, including custom roles.
- V56 removes platform-only permissions from tenant-owned roles.
- `DataSeeder` and `AuthLifecycleService` no longer grant legacy broad or platform-only permission codes when seeding/provisioning vendor administrators.
- Fine-grained assignment permissions govern assigned-resource scope for sales leads/quotations/orders, surveys, service tickets and installations.
- Custom sales roles without `LEAD_ASSIGN` see only assigned/linked records; custom service viewers without `SERVICE_ASSIGN` see only tickets assigned to their user.
- Role-assignment API now returns the safe `UserView` DTO instead of serializing the JPA `AppUser` entity.
- Blocked reserved-role and platform-permission escalation attempts are recorded through `AuthenticationAuditService` with `REQUIRES_NEW`, so the audit event survives the rejected request transaction rollback.

### 19.2 Browser token storage and CSRF

- The short-lived access JWT (default 900 seconds) is kept in application memory and sent as a Bearer token. It is not written to localStorage or sessionStorage.
- The rotating refresh token is issued only as an HttpOnly, Secure, SameSite cookie scoped to `/api/v1/auth`; it is not returned in JSON and is not accepted from the browser request body.
- The readable CSRF double-submit cookie is host-only even if a refresh-cookie parent domain is configured. Refresh and logout validate the cookie/header pair.
- `GET /api/v1/auth/csrf` lets a new browser tab bootstrap the CSRF value from the API cookie; `authenticatedFetch.ts` then refreshes and keeps the access JWT in memory.
- All identified authenticated direct upload/download calls use `authenticatedFetch`; public storefront calls remain unauthenticated.
- Automated tests cover fresh-tab CSRF bootstrap, cookie refresh/retry, concurrent refresh within a tab, session cleanup and the absence of localStorage token persistence.

### 19.3 Trusted proxy and rate-limit operations

- `ClientAddressResolver` now supports exact IPv4/IPv6 addresses and CIDR networks in `TRUSTED_PROXY_IPS`.
- It rejects malformed hostnames and catch-all `/0` networks, and traverses X-Forwarded-For only while the current hop is explicitly trusted.
- Rate-limit bucket cleanup remains scheduled and bounded to two days.
- `ClientAddressResolverTest` covers trusted CIDR matching and ignoring a peer outside the configured network.
- The production Render ingress addresses/ranges and actual environment variable values have not been verified from the repository. Configure only the documented immediate proxy networks; do not trust all sources.

### 19.4 Latest source validation

Source commit `8a079a8ff0641dd253f9b04ed470a837f24bf836` passed backend compilation and 127 backend tests (0 failures/errors/skips), frontend typecheck/component tests/production build, and Spring Boot startup/health against MySQL 8.4 and Mailpit in workflow run [42673236804](https://github.com/rambrij/saumya-solar-energy-product/actions/runs/42673236804). The MySQL migration job through V56 passed in workflow run [42672708442](https://github.com/rambrij/saumya-solar-energy-product/actions/runs/42672708442); migrations did not change between those source commits. Subsequent source change `b24bdfb` adds Web Locks cross-tab refresh serialization and its tests; the latest workflow for that change must pass before final completion.

### 19.5 Remaining production limitations

1. The deployed Render `TRUSTED_PROXY_IPS`, `CORS_ORIGINS`, cookie attributes and HTTPS forwarding chain still require environment-level verification.
2. Cross-tab refresh serialization uses Web Locks where supported. Browsers without Web Locks use per-tab coalescing only; either enforce a supported-browser policy or add a separately reviewed cross-tab fallback before supporting those clients.
3. Browser third-party-cookie restrictions can block SameSite=None cookies when frontend and API use unrelated registrable domains. Prefer same-site subdomains or a same-origin reverse proxy.
4. External/mobile clients that previously supplied refresh tokens in JSON must migrate to the cookie contract or use a separately designed non-browser client authentication flow.

**Completion decision:** Source implementation is substantially hardened, but do not mark Phase 1 production-ready until the latest CI run passes and the deployed proxy/CORS/cookie configuration is verified.


### 19.6 Render deployment alignment check

Read-only Render inspection of the sole available workspace (`My Workspace`) found a service named `soumya-solar-api` at `https://soumya-solar-api.onrender.com`, but it is configured to deploy from `https://github.com/rambrij/soumya-solar-energy-solution` on branch `main`. It is **not** connected to `rambrij/saumya-solar-energy-product` or `audit/implementation-gap-analysis`.

The Render service details expose an unrestricted public ingress allowlist (`0.0.0.0/0`), which is distinct from the application's `TRUSTED_PROXY_IPS` allowlist and is not evidence of trusted forwarded-client handling. The service inspection response does not expose its environment variable values, so `TRUSTED_PROXY_IPS`, `CORS_ORIGINS`, `AUTH_REFRESH_COOKIE_SECURE`, `AUTH_REFRESH_COOKIE_SAME_SITE` and `AUTH_REFRESH_COOKIE_DOMAIN` could not be verified. No Render service configuration, environment variable or deployment was changed.

**Deployment blocker:** production is not proven to be running this repository/branch. Before rollout, explicitly approve repointing the Render API service to the intended repository/branch, then verify its exact CORS/cookie/proxy settings and deploy health. Do not infer these values from source defaults.
