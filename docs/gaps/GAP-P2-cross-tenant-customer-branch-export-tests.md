# GAP-P2-TEN-002 — Cross-tenant, customer, branch, export and download negative coverage

- **Gap ID:** P2-TEN-002
- **Phase:** Phase 2 — Tenant isolation and data integrity
- **Severity:** Critical
- **Status:** Open

## Requirement reference

- Business requirements §3 — Customer portal ownership; §12 — team permissions; §13 — multi-tenant architecture.
- SRS §8.2–8.4 — tenant resolution, database controls and defense-in-depth tests.
- SRS §10.14 RPT-004/RPT-006 — authorized exports and matching tenant/permission rules.
- SRS §13.7–13.8 — invoice, payment, report and export APIs.
- SRS §15.2 and §15.4 — authorization and file/download controls.
- SRS §20.1 and §20.6 — tenant and customer acceptance scenarios.

## Current state and evidence

- `JwtFilter.doFilterInternal` verifies the authenticated user's current tenant, account type, enabled state and token version.
- Tenant-scoped service/repository paths exist in `AppService`, `FinanceService`, `OperationsService`, `WarrantyClaimAttachmentService` and other modules.
- `EnterpriseSaasService.filterBranchRecords` is reused by CRM and operational list paths. Branch-specific integration tests cover multiple resource families.
- `Phase2OperationsController` export paths delegate to tenant-scoped report logic. Warranty attachment download uses tenant + parent claim + attachment ID scoping.
- `EndpointAuthenticationMatrixTest` enumerates unauthenticated mappings; it does not prove authenticated role/customer/branch access.
- `Release12TenantIntegrationTest` contains selected cross-tenant tests for customer addresses, generic resource lists, operations CSV/XLSX/PDF exports, invoice PDF, foreign warranty claims, private attachment downloads, branch membership lists and branch-scoped CRM/finance/operations resources. The browser `tenant-filter-isolation.spec.ts` forges tenant/customer/branch filters on the product/customer/enquiry/lead/survey/quotation/order list APIs. This is meaningful coverage, but not a complete endpoint × verb × identity matrix.
- Branch-unassigned `branch_id IS NULL` rows are currently included for branch members in the shared filter. The product rule for those legacy rows is not explicit.
- No claim is made that every resource has a DELETE API. Financial/ledger entities may be intentionally immutable.

## Expected state

- Every existing tenant-owned resource endpoint is checked for authenticated tenant and permission scope.
- Customer-owned resources additionally validate the active customer profile/user relationship.
- Branch/assigned-resource scope applies to list, detail, update, export and download operations.
- Export aggregates, file metadata and file bytes are tenant/customer scoped; storage keys and signed links cannot be used as authorization.
- Frontend filters and selectors are UX only; server-side authorization remains authoritative.
- Tests cover read, write, delete where exposed, export and download using foreign tenant/customer/branch identifiers.

## Existing code to extend

- Extend `Release12TenantIntegrationTest`, `EndpointAuthenticationMatrixTest`, `EnterpriseSaasService.filterBranchRecords`, `AppService`, `FinanceService`, `OperationsService`, `Phase2OperationsController`, and private attachment services.
- Reuse the existing `frontend/e2e/tenant-filter-isolation.spec.ts` and `frontend/e2e/tenant-react-ui.spec.ts`.
- Reuse tenant-qualified repository methods and the authenticated principal; do not add client-selected tenant filters as authorization.

## Implementation plan

1. Generate a current controller mapping inventory and map each route to its tenant-owned resource, customer owner, branch owner, permission and supported verbs.
2. For every existing route, build a foreign-tenant/customer/branch fixture and test list/search/detail/create/update/delete-if-exposed/export/download.
3. Add missing tenant/customer/branch predicates to existing service/repository methods; do not duplicate service logic or add endpoints without an explicit requirement.
4. Test bulk actions, nested resources, attachment parent IDs, object keys, export IDs and pagination/filter tampering.
5. Expand Playwright to interact with actual React screens and route/query filters for at least two tenants and two customers.
6. Resolve and document NULL-branch visibility before changing current behavior.
7. Preserve not-found/non-disclosing responses for foreign resource identifiers where that is the established policy.

## Test plan

- Unit: tenant/customer/branch scope resolver and branch filter behavior.
- Integration: foreign tenant IDs for list/read/create/update/delete-if-exposed; foreign customer quotation/order/invoice/payment/installation/warranty/service/document IDs; branch A versus branch B assignments.
- Export/download: CSV/XLSX/PDF content assertions; file upload/download byte and content-disposition assertions; foreign attachment and wrong-parent IDs.
- Browser: sign in as tenant A and B, alter filters/route IDs, use customer and branch selectors, verify no foreign data appears and denied actions have no side effects.
- Regression: platform admin access remains intentionally cross-tenant only where platform permission permits it.

## Acceptance criteria

- A generated endpoint × verb × principal matrix is checked into CI.
- Every existing resource family has negative coverage for applicable read/write/delete/export/download operations.
- No cross-tenant/customer/branch data or counts leak through list, search, report, export or file metadata.
- All branch rules are enforced server-side and NULL-branch behavior is approved.
- Browser and backend tests pass on latest branch head.

## Status

**PARTIAL — selected negative coverage is extensive and present; exhaustive matrix remains open.** Existing integration tests cover multiple tenant/customer/branch/export/download paths. Added a same-tenant cross-customer negative case: customer A cannot create a warranty claim against customer B's warranty, even when both belong to the same vendor tenant (`Release12TenantIntegrationTest.warrantyClaimListCreateAndDecisionAreTenantAndCustomerScoped`, commit `ea118cd0`). Engineer and warranty lookups in `Phase2OperationsController` now use tenant-qualified repository methods (commits `102adc61`, `3e2d6d57`).

Still open: full customer resource family matrix, all branch CRUD/assignment operations, every export and private file download family, and service engineer assigned-resource access across detail/write/download/export paths. Workflow 42821756695 passed all four jobs: backend (163 tests), frontend production build, MySQL 8.4 migration/integrity validation and Spring Boot/Playwright tenant-filter tampering. The later reset-token change is under workflow 42822434704.


### Assigned-engineer ticket mutation guard — 2026-11-10

- Closed a concrete authorization bypass in FinanceService: ticket closure and SLA pause/resume previously loaded a tenant-qualified ticket and checked a broad service permission, but did not consistently call the shared assigned-ticket ownership guard.
- Extracted the assignment predicate into package-visible assertAssignedTicketAccess and reused it from ensureTicketAccess; closeTicket, pauseSla and resumeSla now call ensureTicketAccess before mutation. A SERVICE_ASSIGN dispatcher retains tenant-wide access; a non-dispatcher must be the ticket's assigned engineer.
- Added regression cases in FinanceServiceAssignedVisitScopeTest for unassigned engineer denial, assigned engineer access and dispatcher access.
- This closes the listed ticket mutation path only. Service engineer authorization across every endpoint, export and download remains open pending the complete endpoint/resource matrix and CI.


### Branch lifecycle and permission-scope test expansion — 2026-11-10

- Added an integration test that verifies branch-management denial for a sales employee and Vendor Admin create/list/update, member assign/list/remove, deactivate and reactivate operations.
- Replaced role-name gates in service comments, offline service comments, survey review/reassignment and warranty evidence access with the corresponding fine-grained permissions. Assigned-service visibility remains constrained unless the principal has SERVICE_ASSIGN.
- Existing customer negative tests still cover addresses, orders, invoices, service tickets, warranty claims and claim evidence; existing exports cover operations CSV/XLSX/PDF and invoice PDF. This is not yet every customer resource family, every branch mutation, every report/export, or every attachment family.
- No claim is made that all export and file-download paths have been exhausted. The endpoint inventory remains the checklist and must be closed against every exposed download/attachment route.


### Customer payment receipt and attachment upload authorization — 2026-11-10

- Customer payment receipt reads now rely on invoice/customer ownership rather than requiring the vendor-only PAYMENT_VIEW permission after ownership has been verified. A foreign customer's payment remains hidden with 404.
- Added a same-tenant customer quotation-list negative case and payment receipt owner/foreign-owner assertions to the customer integration test.
- Service evidence upload requires SERVICE_UPDATE for vendor users and the existing tenant/assigned-ticket guard; a read-only service viewer cannot upload.
- The complete customer matrix remains open for all other resource families and each exposed export/download route. Existing PDF/XLSX/CSV and attachment tests are selected evidence, not proof that every binary route is covered.


### Assigned service evidence download tests — 2026-11-10

- ServiceAttachmentServiceTest now verifies that a read-only assigned engineer with SERVICE_VIEW can download evidence after the ticket scope guard succeeds, and an unassigned engineer is denied before attachment repository/storage access.
- The service upload test covers SERVICE_UPDATE enforcement; FinanceService's assigned-ticket guard and visit filtering are separately covered by FinanceServiceAssignedVisitScopeTest.
- Foreign-tenant HTTP attachment access remains covered by Release12TenantIntegrationTest. A same-tenant cross-customer HTTP download/list test is still required before this route family can be marked exhaustive.
