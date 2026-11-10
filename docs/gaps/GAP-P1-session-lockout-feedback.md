# GAP-P1-AUTH-003 — Session contract and lockout/rate-limit feedback

- **Gap ID:** P1-AUTH-003
- **Phase:** Phase 1 — Authentication and security
- **Severity:** High
- **Status:** Open

## Requirement reference

- SRS §13.2 — Authentication APIs and browser session contract.
- SRS §15.1 — Authentication and session security.
- SRS §15.3 — Generic API errors and rate limiting.
- SRS §11 — Login/register screens.
- Business requirements §18 — Login/session management and account lockout/rate controls.

## Current state and evidence

The source implementation is substantially aligned with the SRS cookie design:

- `AuthCookieService.issue` issues `saumya_refresh` as HttpOnly with configurable Secure/SameSite and path `/api/v1/auth`; the CSRF cookie is readable by the browser.
- `AuthCookieService.requireCsrf` compares the CSRF cookie and header using `MessageDigest.isEqual`.
- `Controller.refresh/logout/csrf` use the cookie and CSRF service.
- `frontend/src/authenticatedFetch.ts` keeps the access session in module memory, uses the cookie for refresh, stores the CSRF value in sessionStorage, serializes refreshes and dispatches `saumya:session-expired`.
- `frontend/src/main.tsx` clears the legacy `session` localStorage key and bootstraps the session through refresh.
- `AuthLifecycleService.authenticate` returns the same generic 401 for invalid credentials and a locked account. This avoids disclosing account lockout state.
- The shared API helper maps 429 and 403 messages; the session-expired event returns the user to login with a generic message.
- No distinct 423 response or lockout-specific frontend state was established.

The source-level contract appears implemented, but latest-head browser verification for missing/mismatched CSRF cookie, refresh replay, concurrent tabs, logout and cookie attributes was not established in this audit.

## Expected state

- Preserve the SRS contract: access token in memory, refresh token HttpOnly/Secure/SameSite cookie, CSRF double-submit on refresh/logout, explicit CORS allowlist and credentialed requests.
- Keep account lockout responses non-disclosing.
- Provide clear 429 retry feedback and generic 401/session-expired behavior.
- Do not add a 423 response unless security/product owners approve a non-enumerating contract. The source documents do not mandate 423; a distinct 423 may disclose that an account exists or is locked.

## Existing code to extend

- Extend `AuthCookieService`, `Controller.login/refresh/logout/csrf`, `SecurityConfig.cors`, `authenticatedFetch.ts`, and the existing `main.tsx` auth form/session event handler.
- Reuse the existing `AuthLifecycleService.authenticate` and lockout persistence in V52.
- Reuse frontend authentication/refresh tests rather than adding a second session client.

## Implementation plan

1. Verify cookie attributes for production configuration and ensure refresh cookie is never serialized into JSON or browser storage.
2. Add controller/integration tests for CSRF missing, mismatched, valid, expired, replayed and logout cases.
3. Add Playwright coverage for bootstrap, reload, expired access token, expired refresh cookie, concurrent tabs and session-expired UX.
4. Add frontend assertions for 429 Retry-After messaging and generic 401 login feedback.
5. Obtain product/security approval for the 423-vs-401 contract. Until then, preserve generic 401 for account lockout.
6. Confirm CORS origins and trusted cookie domain/SameSite settings for each deployment environment.

## Test plan

- Unit: cookie builder flags, CSRF constant-time comparison, lockout expiry and generic failure body.
- Integration: login sets HttpOnly refresh cookie; refresh rotates cookie; old token replay revokes family; logout clears cookies; CSRF mismatch returns 403.
- Browser: refresh after reload without localStorage token; multiple tabs; 401 refresh success; refresh failure clears session; 429 message and retry behavior.
- Negative/security: unknown account and locked account return indistinguishable status/body; no refresh token in JSON, localStorage or logs.

## Acceptance criteria

- Refresh token exists only in the HttpOnly cookie and server-side storage.
- Access token is memory-only; CSRF token is not treated as an authentication credential.
- CSRF is required for cookie-authenticated refresh/logout.
- 401/403/429 behavior is safe and tested.
- Account lockout does not reveal account existence.
- Latest-head frontend and backend tests pass.

## Remediation update — 2026-11-10

- Existing HttpOnly refresh cookie, CSRF double-submit, memory-only access token and generic lockout response are retained; no change was needed to avoid exposing lockout state.
- Existing auth tests cover password lockout expiry, disabled users and refresh-family replay (`AuthLifecycleServiceTest`). Existing browser tests cover tenant filter tampering but do not establish the full session lifecycle.
- Run 42821337890 passed all four jobs (162 backend tests). Latest-head run 42821756695 passed all four jobs (163 backend tests), including MySQL 8.4 migration/integrity validation, Spring Boot health and Playwright tenant-filter tampering tests. The later reset-token rate-limit change is under workflow 42822434704.
- Still open: browser tests for missing/mismatched CSRF, refresh-cookie attributes, concurrent tabs, logout and expired refresh; explicit 429 Retry-After UX assertions; production cookie/CORS settings. SRS does not require a 423 response; preserve generic 401 until product/security approves otherwise.

## Status

**PARTIAL — implementation exists and prior CI passed; full latest-head browser session verification remains open.**


Browser verification (2026-11-10): workflow `42828454430` passed Playwright checks for HttpOnly refresh cookie path, no access-token persistence in localStorage, missing/mismatched CSRF rejection, refresh rotation and logout token revocation. The auth E2E uses the dedicated seeded Sales account to avoid parallel logout/revocation interference. Workflow `42828934287` passed the browser security tests on code commit `fe7b1181`. Lockout expiry/browser message and multiple-tab edge cases remain open.
