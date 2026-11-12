# Security controls and verification

Backend Spring Security, service ownership checks and tenant-scoped data access are authoritative. Frontend navigation
visibility is not an authorization boundary. Keep tenant/branch scoping on backend reads and mutations, and keep
authentication, refresh-token and CSRF behavior covered by regression tests.

Production tenant-integrity preflight is manual and protected. Ordinary CI must never receive production credentials.
Private customer evidence must only be downloaded through authorized API paths.

## CI helpers

The `ci/` directory contains focused evidence checks and source scanners, not a separate runtime framework:
endpoint authorization reconciliation, disposable schema assertions, transport guard, async-worker inventory and
legacy authorization inventory. These checks complement tests; they do not replace fixture-backed role/resource tests
or production infrastructure-owner sign-off. See `.github/workflows/security-checks.yml` and the protected
production preflight workflow.
