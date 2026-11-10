# GAP-P1-AUTH-002 — Fine-grained role/permission enforcement

- **Gap ID:** P1-AUTH-002
- **Phase:** Phase 1 — Authentication and security
- **Severity:** Critical
- **Status:** Open

## Requirement reference

- Business requirements §12 — Team Management (role matrix and permission-level controls).
- SRS §9.1–9.3 — Standard roles, permission catalogue and critical authorization rules.
- SRS §15.2 — Deny-by-default authorization and ownership checks.
- SRS §20.2 — Role and permission acceptance scenarios.

## Current state and evidence

- `JwtFilter.doFilterInternal` reloads user roles and permission codes from the database and builds authorities for each request.
- `AppService.requirePermission` and service-level guards enforce existing permission codes.
- `AuthLifecycleService.createCustomRole` contains service-level custom-role creation logic and validates vendor permission codes.
- `Controller.createVendorEmployee` and `Controller.assignEmployeeRole` enforce vendor identity and permission checks; role assignment is still based on role names.
- V53 and `DataSeeder.employeeRolePermissions` are described in the branch's implementation notes as adding the SRS catalogue and predefined grants.
- Existing services retain legacy broad permission checks. The complete SRS catalogue is not yet consistently enforced at each controller/service boundary.
- The inspected controller surface does not establish a complete vendor custom-role CRUD/permission-editor API and UI.
- `EndpointAuthenticationMatrixTest` checks unauthenticated access only. `Release12TenantIntegrationTest` covers selected role-escalation cases, not the full role × endpoint matrix.

## Expected state

- All protected actions require explicit fine-grained permissions, with deny-by-default behavior.
- Vendor Admin can create/update/delete unused custom vendor roles only from the permitted vendor-level permission catalogue.
- No vendor role can receive platform permissions or cross-tenant grants.
- Assigned-resource restrictions for Sales Executive and Service Engineer are enforced by backend queries and detail/download operations.
- Legacy broad grants remain only as a compatibility bridge until all call sites have migrated and tests prove the new matrix.

## Existing code to extend

- Extend `JwtFilter`, `Principal`, `AppService.requirePermission`, `AuthLifecycleService.createCustomRole/assignEmployeeRole`, and the existing controller/service permission guards.
- Reuse the existing `permission`, `role`, `role_permission`, and `user_role` schema and V53 permission catalogue.
- Extend `RoleNavigation.tsx` only for UX; it must not be used as an authorization boundary.
- Extend `EndpointAuthenticationMatrixTest`, `Release2SecurityTest` and `Release12TenantIntegrationTest`.

## Implementation plan

1. Extract the actual role grants from the database migration and seeder and compare them with SRS §9.1–9.3.
2. Generate a controller mapping/action inventory and map every operation to one required permission.
3. Migrate service checks from broad legacy codes to fine-grained codes incrementally, retaining old grants during the transition.
4. Expose custom-role management through existing role services only after confirming the complete API gap; add a UI editor using the central permission catalogue.
5. Enforce assignment-level predicates in list, detail, update, export and attachment paths.
6. Add additive migration(s) for grant mapping only after reviewing existing production grants; do not automatically grant elevated permissions.

## Test plan

- Unit: permission evaluation, role grant validation, platform permission rejection, custom role permission allowlist.
- Integration: every standard role against each protected operation; Vendor Admin cannot grant platform permissions; cross-tenant role IDs rejected; employee role assignment cannot cross tenants.
- Negative/security: missing identity 401; authenticated insufficient permission 403; assigned sales/service user cannot access unassigned resources by guessed ID, list, export or download.
- Regression: existing role navigation remains consistent, but server authorization tests pass when navigation is bypassed.

## Acceptance criteria

- Every protected controller operation has a documented permission.
- No legacy broad permission check can grant access that the fine-grained matrix denies.
- Custom roles cannot contain platform permissions.
- Assigned-resource restrictions apply consistently to all read/write/export/download paths.
- Role-matrix and privilege-escalation tests pass in CI.

## Status

**Open** — catalogue and service foundations exist, but legacy broad checks, full custom-role workflow and exhaustive role matrix tests remain incomplete.
