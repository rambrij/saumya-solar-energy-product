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

- `backend/src/main/java/com/saumyasolar/security/AuthenticationRateLimitFilter.java` — `shouldNotFilter` now selects auth endpoints plus public enquiry POST, export GET/POST, and payment webhook POST routes through `limitFor`.
- `AuthenticationRateLimitFilter.doFilterInternal` hashes `path + "|" + resolvedAddress`. The rate-limit bucket is endpoint + resolved client IP; there is no independent normalized account/user bucket in this filter.
- `AuthLifecycleService.authenticate` and V52 persist failed attempts and lockout per known account. This is a separate lockout mechanism, not a general per-account rate limiter for all applicable routes.
- `ClientAddressResolver.resolve` trusts forwarded addresses only through configured trusted networks; `parseNetwork` supports CIDR prefixes.
- `AuthenticationRateLimitCleanup.removeExpiredBuckets` removes old buckets after two days.
- `backend/src/main/resources/db/migration/V52__authentication_lockout_and_rate_limit.sql` creates the bucket table.
- Tests referenced by the branch include `AuthenticationRateLimitFilterTest`, `ClientAddressResolverTest` and `AuthLifecycleServiceTest`. Latest-head execution was not established in this audit.

Application-level route buckets have now been added for `/api/v1/public/{storeSlug}/enquiries`, GET export routes and POST `/api/v1/exports`, `/api/v1/payment-webhooks/**` and `/api/v1/payment-gateway-webhooks/**`. They use separate configurable per-IP endpoint buckets. Production ingress/WAF configuration still needs verification.

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

1. **Implemented in commit `1a79b56244af6aa23bd58f15f835766574eae908`:** account bucket added for login, forgot-password and reset-password requests. The identifier is normalized in memory and keyed with HMAC-SHA-256 derived from the configured JWT secret; only the 64-character digest is stored in the existing `auth_rate_limit_bucket` table.
2. **Implemented:** the existing IP + endpoint bucket remains independent; account + endpoint is an additional bucket. Both use the configured route-specific limit and window.
3. **Implemented:** request JSON is buffered with a 16 KiB maximum and replayed to Spring MVC; malformed JSON receives only IP throttling and continues to normal MVC validation; oversized bodies are rejected with a generic 400.
4. **No migration required:** existing V52 bucket schema accepts both opaque 64-character hashes; no applied migration was edited.
5. **Implemented in commit `0d2a741d3c6a50aa44668954868a4591084432c2`:** independent configurable IP + endpoint buckets added for public enquiry, export and payment webhook endpoints. Config keys: `app.auth.rate-limit.public-enquiry-per-window`, `export-per-window`, `webhook-per-window`.
6. **Tests added in commit `f3cb1d3051c12b7ea37f1246de50c0d53994ce9c`:** public enquiry, export and webhook rate-limit unit tests. Latest-head workflow is pending.
7. **Open:** prove distributed/concurrent semantics on MySQL, verify route inventory and production gateway configuration, and decide whether refresh/MFA need a trustworthy account/challenge bucket. Refresh and MFA currently use IP + endpoint throttling.

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

**Implemented / Pending Verification (expanded partial closure).**

- Account + endpoint throttling is implemented for login, forgot-password and reset-password, reusing V52 and the existing filter.
- IP + endpoint throttling remains in place for all five existing authentication routes.
- Focused unit tests were added for the additional bucket, request-body replay, raw identifier non-persistence and oversized-body rejection.
- The GitHub Actions full validation workflow was triggered for commit `1a79b56244af6aa23bd58f15f835766574eae908`; at report update time its run was still in progress. Do not mark this requirement MET until the backend job passes.
- Application rate limits now also cover public enquiry/export/webhook routes; production ingress/WAF configuration and distributed concurrency verification remain Open.
- No applied migration was changed and no schema migration was needed.
