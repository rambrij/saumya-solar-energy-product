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
- Controller.employeeRoles/vendorRolePermissions/createCustomRole/updateCustomRole/deleteCustomRole exposes tenant role listing, an assignable permission catalogue, and custom-role create/update/delete APIs. RoleManagementPanel.tsx provides create/edit/delete and employee role assignment UI. This is not absent; the remaining issue is proving the complete action-to-permission matrix and closing legacy/broad checks consistently.
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

## Remediation update — 2026-11-10

- Custom-role workflow verified in source: Controller.java lines 250–288 exposes GET roles/permissions and POST/PATCH/DELETE role endpoints with ROLE_VIEW/CREATE/UPDATE guards; AuthLifecycleService.java lines 381–464 implements tenant-scoped role create/update/delete, vendor permission allowlisting and assigned-role deletion protection; RoleManagementPanel.tsx contains the permission editor and employee assignment UI.
- Fine-grained enforcement extended: AppService.requirePermission is used for catalogue, CRM, quotation and order actions; OperationsService.permission and FinanceService.permission enforce inventory, installation, service and finance permissions. Existing tests cover selected deny cases in Release2SecurityTest and AuthLifecycleServiceTest.
- Tenant-safe employee resolution extended: commits 8be6f0d78bd19b3a5629524e0556255fd7505bdf and 1f5ce498369a2781fff439dcc12bb99f1e48e978 add tenant-qualified employee/engineer/visit lookup methods and use them in role/assignment workflows.
- Assigned quotation scope extended: commit bbd10cce0efb86c839925b59eaae7e87e348b735 enforces assigned-sales scope on quotation revision reads; test added in commit 5ccba941c0e7c54c40b038807cc3cb17f73a5e74.
- Additional remediation (2026-11-10): Phase2OperationsController.ops now relies on INSTALLATION_ASSIGN rather than hard-coded manager role names; FinanceService SLA controls now rely on SERVICE_UPDATE; OperationsService.warranties applies assigned-installation scoping for SERVICE_VIEW-only users. Phase2OperationsControllerTest.customRoleWithInstallationAssignPermissionCanViewEngineerDirectory verifies custom-role permission behavior. Commits 128f1fc5, 2c429418, a8837dc4, 6c29c6fb.

Still open: inventory every Controller/secondary-controller mapping and service entry point against SRS §9.2; inspect legacy role grants and all remaining role-name checks; add authenticated role × endpoint × resource negative tests; verify assigned-service detail, write, export and download paths. Current tests are not a complete role matrix.

## Status

**PARTIAL — implementation extended; workflow 42821756695 passed all four jobs: backend (163 tests), frontend production build, MySQL 8.4 migration/integrity validation, and Spring Boot health plus Playwright tenant-filter tampering tests. New reset-token throttling code is under workflow 42822434704.** The custom-role CRUD and UI already exist and are not a missing feature. Remaining critical work is the exhaustive action-to-permission traceability matrix, review of legacy broad permission grants, and full authenticated role/resource negative tests. Do not mark MET until latest-head backend/MySQL/browser tests pass.


### Authenticated role-matrix expansion — 2026-11-10

- Added `Release12TenantIntegrationTest.fineGrainedRoleMatrixDeniesCrossModulePrivilegeEscalation` with authenticated Inventory, Accountant and Service Engineer test principals. It verifies allowed module reads return 200 and cross-module unauthorized reads return 403.
- The test fixture runs in a rollback transaction so temporary role/user rows do not contaminate other integration tests.
- CI workflow `42828044536` passed backend (171 tests), frontend build and MySQL migration validation; Spring Boot/browser job was still running at the time of the check.
- This is an expanded representative matrix, not every standard role × every endpoint × every HTTP verb. The complete matrix remains PARTIAL.


### Assigned service-ticket mutation follow-up — 2026-11-10

- FinanceService.closeTicket, pauseSla and resumeSla now invoke the shared customer/assigned-engineer ticket access guard before changing ticket state.
- The assignment predicate is covered by regression tests for unassigned engineer denial, assigned engineer access and tenant dispatcher access.
- This is a targeted correction, not completion of the full role × endpoint × verb × resource matrix; legacy permission reconciliation and exhaustive role tests remain open.
