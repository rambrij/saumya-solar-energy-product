# GAP-P1-SEC-001 — Authentication and endpoint rate-limit coverage

- **Gap ID:** P1-SEC-001
- **Phase:** Phase 1 — Authentication and security
- **Severity:** High
- **Status:** Open

## Requirement reference

- Business requirements §18 — Production-Grade Requirements / Security (rate limiting, account lockout and abuse controls).
- SRS §15.3 — Application and API security (rate limits for login, password reset, public enquiry, exports and webhooks).
- User audit scope — login rate limits by IP, user/account and endpoint.

## Current state and evidence

- `backend/src/main/java/com/saumyasolar/security/AuthenticationRateLimitFilter.java` — `shouldNotFilter` limits its filter scope to POST `/api/v1/auth/login`, `/auth/refresh`, `/auth/forgot-password`, `/auth/reset-password` and `/auth/mfa/verify`.
- `AuthenticationRateLimitFilter.doFilterInternal` hashes `path + "|" + resolvedAddress`. The rate-limit bucket is endpoint + resolved client IP; there is no independent normalized account/user bucket in this filter.
- `AuthLifecycleService.authenticate` and V52 persist failed attempts and lockout per known account. This is a separate lockout mechanism, not a general per-account rate limiter for all applicable routes.
- `ClientAddressResolver.resolve` trusts forwarded addresses only through configured trusted networks; `parseNetwork` supports CIDR prefixes.
- `AuthenticationRateLimitCleanup.removeExpiredBuckets` removes old buckets after two days.
- `backend/src/main/resources/db/migration/V52__authentication_lockout_and_rate_limit.sql` creates the bucket table.
- Tests referenced by the branch include `AuthenticationRateLimitFilterTest`, `ClientAddressResolverTest` and `AuthLifecycleServiceTest`. Latest-head execution was not established in this audit.

The inspected filter does not establish application-level throttling for public enquiry, export or webhook routes. An upstream WAF/API gateway may provide it, but no production configuration was available to verify that.

## Expected state

- Enforce bounded, configurable limits by endpoint and trusted client IP.
- Add an independent, privacy-preserving account/user dimension for login and applicable recovery actions; store only a keyed/hash representation of normalized identifiers, never raw email.
- Cover the SRS-listed public enquiry, export and webhook routes either in the application or through verified ingress controls.
- Return a generic 429 response with a correct retry interval and no account-existence disclosure.
- Keep counters safe under concurrent requests and across application instances.

## Existing code to extend

- Extend `AuthenticationRateLimitFilter`, `ClientAddressResolver`, V52's `auth_rate_limit_bucket`, and the existing cleanup component.
- Reuse `AuthLifecycleService.authenticate` and existing failed-attempt/lockout persistence rather than adding a second lockout model.
- Reuse existing tests in `backend/src/test/java/com/saumyasolar/security` and `backend/src/test/java/com/saumyasolar/service`.

## Implementation plan

1. Confirm the exact endpoint inventory and distinguish routes protected by application filters from routes protected by a verified WAF/API gateway.
2. Add an account-key bucket using a server-side HMAC/hash of a normalized identifier. Do not log or persist the raw identifier.
3. Keep endpoint, IP and account limits independently configurable; define precedence and retry behavior.
4. Add rate-limit coverage to public enquiry/export/webhook handlers only where those routes exist and are in scope; otherwise record the verified gateway control.
5. Keep the existing migration additive. If a new bucket dimension or index is needed, add a new Flyway migration after confirming the actual latest version; never edit V52.
6. Extend the cleanup query and metrics to cover all bucket dimensions without exposing identifiers.

## Test plan

- Unit: IP bucket, account bucket, endpoint separation, normalized email case/whitespace, HMAC/hash stability, trusted proxy chain and spoof rejection.
- Integration: concurrent requests across two app instances sharing MySQL; expiry and cleanup; correct 429 and Retry-After.
- Negative/security: same IP attacking many accounts; one account attacked from many IPs; unknown account; public enquiry spam; export abuse; webhook flood; verify no raw email/IP secret material in bucket keys or logs.
- Regression: lockout still increments transactionally and a successful/expired lockout resets as designed.

## Acceptance criteria

- Each protected route has an explicit documented policy and test.
- IP and account buckets are independently enforced and endpoint-specific.
- Concurrent requests cannot bypass configured limits.
- 429 responses do not disclose account existence.
- Cleanup does not delete active buckets.
- CI passes backend tests and MySQL migration validation.

## Status

**Open** — implementation exists for endpoint + IP and per-account failed-login lockout, but independent account throttling and full SRS endpoint coverage are not demonstrated. No code changes were made in this audit pass.
