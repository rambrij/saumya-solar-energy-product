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
| Browser token storage | SRS §15.1 | localStorage session | SRS prefers secure HttpOnly cookie where feasible | No cookie migration; requires coordinated CSRF/CORS/API design | Manual architecture decision required |

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

No permission grants were broadened. The SRS fine-grained permission catalogue (for example `PRODUCT_VIEW`, `LEAD_ASSIGN`, `SERVICE_RESOLVE`) differs from the current application catalogue (for example `CATALOG_VIEW`, `LEAD_MANAGE`, `SERVICE_MANAGE`). A safe migration requires a role-by-role mapping and should not be guessed during a rate-limit/lockout change.

### 4.5 Frontend behavior

The shared `api()` helper now:
- Reads the latest stored session before making a protected JSON request.
- Attempts one refresh after a 401 if a refresh token exists.
- Stores the rotated access and refresh token and retries the original request once.
- Clears the session and reloads when refresh fails.
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

A final workflow was triggered for the subsequent proxy-address parser hardening commit `681c3267cf9b1adeb00d83432955f6283054b8c6`. At report update time that latest run was still in progress. The successful run above is strong validation of the preceding code tree; do not claim the final commit verified until its run completes.
