# Phase 1 Commercial MVP — Implementation Validation

**Validation date:** 2026-11-08  
**Repository:** `rambrij/saumya-solar-energy-product`  
**Target branch:** `phase-1-commercial-mvp-implementation`  
**Validated branch HEAD:** `767c5659617d852f094d754228bdeae8890d7a0c`  
**Audit source branch:** `phase-1-commercial-mvp-audit`  
**Audit source commit:** `19661a18507570df64b1ef55512a692beaef672b`  
**Audit baseline commit:** `9a2872522a827460833a5c6df0a298c94ace8ea4`  
**Scope:** Phase 1 Commercial MVP only. Phase 2/3 items are excluded unless the audit/SRS expressly includes them.

---

## 1. Executive conclusion

**Disposition: Phase 1 is NOT fully accepted. Estimated completion: 62% (qualitative).**

The implementation branch contains substantial remediation since the audit baseline. It is 44 commits ahead of the audit baseline and zero commits behind it. The change set adds SMTP-backed email delivery, a notification delivery table, password-reset email delivery, Platform Super Admin email MFA, and new unit tests. The password-reset token disclosure finding is corrected in the inspected code.

However, the branch still has material gaps and defects:

1. The CI startup/health check fails because the Actuator mail health indicator attempts SMTP at `localhost:1025` where no SMTP server is configured in that job. Application migrations complete and the process reaches the health endpoint, but the health response does not become UP.
2. Invoice totals can still diverge from accepted quotation/order totals. `FinanceService.createInvoice` recalculates invoice totals from order item line totals and tax snapshots; it does not reconcile the quotation-level discount. No invoice-specific integration test was added.
3. Email delivery is now implemented for password reset, Platform MFA, quotation send and invoice issue, but it is synchronous, has no retry worker/queue, no attachment support, no delivery-history API/UI, and does not cover all relevant business events.
4. The vendor commercial dashboard remains absent. Platform tenant metrics do not satisfy vendor sales/operations KPI requirements.
5. Vendor onboarding still creates tenants directly as ACTIVE and provisions an initial Vendor Admin; no pending/verified lifecycle, vendor verification or invitation workflow exists.
6. Tenant isolation is enforced in many service paths and selected negative tests exist, but exhaustive cross-tenant IDOR and role-by-endpoint coverage is still missing. Global email uniqueness and lack of composite tenant-aware foreign keys remain.
7. Static role mappings remain; custom tenant role/permission administration is not implemented.
8. No general append-only audit log or in-app notification inbox/API is present.

**Verified CI result for target HEAD (GitHub Actions run #381, run ID 42455347378):**
- Backend Maven clean verify: PASS — 31 tests, zero failures/errors/skips.
- Frontend TypeScript/Vite production build: PASS.
- MySQL 8.4 migration validation: PASS — V1 through V24 applied.
- Spring Boot/MySQL startup health gate: FAIL — Actuator mail health check cannot connect to `localhost:1025`.

The branch should not be accepted until the critical financial defect, SMTP health/startup integration and remaining high-priority product/security gaps are resolved or explicitly waived by the product owner.

## 2. Validation method and limitations

### Source baseline

The GitHub branch reference was read directly and resolves to HEAD `767c5659617d852f094d754228bdeae8890d7a0c`. GitHub compare reports the implementation branch is 44 commits ahead and 0 behind `9a2872522a827460833a5c6df0a298c94ace8ea4`.

The audit report was read from `phase-1-commercial-mvp-audit`. Its detailed findings were compared with the current implementation tree and source. The current recursive tree contains 203 entries, including backend source/tests, frontend source, Flyway V1–V24, Docker Compose, README and CI workflow.

A local `git clone/fetch/checkout` could not be completed in this execution environment because DNS resolution for github.com failed. Therefore this validation used the authoritative remote branch ref, GitHub file/tree/compare APIs and the GitHub Actions checkout/build/test run. The Actions run checked out the PR merge ref for the target HEAD. This is not represented as a local working-tree checkout.

### Evidence categories

- **Confirmed implementation:** source, schema, configuration or UI exists in the target branch.
- **Verified runtime behavior:** an automated CI job/test actually executed and passed or failed.
- **Code-only finding:** source inspection supports the conclusion, but a live frontend/API/database workflow was not manually exercised.
- **Assumption:** none used as proof of completion; assumptions are explicitly identified.
- **Unable to verify:** deployed production behavior, provider delivery to real inboxes, live hosting settings and manual browser E2E not available in this validation.

No application code, existing tests, migrations or deployment files were modified. Only this validation report is added to the target branch.

## 3. Status definitions

| Status | Meaning |
|---|---|
| Fully Implemented and Verified | Required behavior is connected across relevant layers and supported by meaningful automated/runtime evidence. |
| Implemented but Not Fully Verified | Implementation exists, but complete end-to-end/runtime/negative-path evidence is insufficient. |
| Partially Implemented | Some expected behavior exists, but material parts are missing. |
| Implemented but Defective | The feature exists but a confirmed defect materially affects correctness, security or operation. |
| Not Implemented | No adequate implementation evidence exists. |
| Not Applicable | Outside approved Phase 1 scope or not applicable to the requirement. |

Percentages are qualitative estimates of requirement behavior delivered, not code-volume or test-coverage percentages.

## 4. Requirement-wise completion

| Phase 1 requirement | Estimate | Validation status | Main reason |
|---|---:|---|---|
| Multi-tenant architecture | 70% | Partially Implemented | Good principal/service scoping; incomplete exhaustive IDOR assurance, global email uniqueness and composite tenant FK coverage |
| Authentication | 70% | Implemented but Defective | Reset-token disclosure fixed; email reset and Platform MFA added; localStorage tokens, long access-token lifetime, no login throttling and no MFA-specific tests remain |
| Vendor management | 45% | Partially Implemented | Platform tenant CRUD/status exists; no pending/verified/invitation/vendor verification lifecycle |
| User and RBAC management | 60% | Partially Implemented | Employee CRUD and fixed roles exist; no custom role/permission administration or complete permission matrix |
| Customer management | 70% | Partially Implemented | Tenant customer CRUD and customer portal exist; profile/activity depth, customer order details and tenant-specific identity model gaps remain |
| Solar product/package management | 75% | Partially Implemented | Core CRUD/catalogue/package components exist; package attribute/publication and server-side catalogue query gaps remain |
| Enquiries and lead management | 65% | Partially Implemented | Enquiry-to-lead and history exist; CRM activities, source/follow-up workflow and concurrency proof remain |
| Quotations | 73% | Implemented but Defective | Revision/PDF/approval/acceptance and email notification exist; discount/tax snapshot defect and incomplete email/PDF tests remain |
| Orders | 65% | Partially Implemented | Idempotent quotation conversion and status history exist; order payment state/details and invoice reconciliation remain |
| Basic inventory | 85% | Fully Implemented and Verified (core scope) | Receipt/issue/return/damage/adjustment/transfer/reservation/ledger and selected integration/concurrency tests pass |
| Invoice generation | 58% | Implemented but Defective | Invoice numbering/PDF/snapshots and issue email exist; totals can diverge from accepted order and no invoice-specific tests |
| Email notifications | 45% | Partially Implemented | SMTP service and selected event emails exist; no retry queue, attachments, inbox/history API/UI or complete event coverage |
| Basic dashboard | 25% | Partially Implemented | Platform tenant dashboard exists; vendor commercial/customer summary dashboards absent |

**Overall estimate: 62%** — arithmetic mean of the 13 Phase 1 requirement estimates above. This is a directional estimate, not a claim of complete business acceptance.

## 5. Audit finding traceability

Finding IDs below are assigned for this validation to make the audit's 13 requirement rows and supporting SRS findings traceable.

| Audit Finding ID | Phase 1 Requirement | Expected Fix | Implementation Status | Backend Evidence | Frontend Evidence | Database Evidence | Test Evidence | Remaining Gap | Severity | Required Action |
|---|---|---|---|---|---|---|---|---|---|---|
| F01 | Multi-tenant architecture | Principal-derived tenant scope; no cross-tenant ID/parameter access; tenant-aware relationships | Partially Implemented | `security/Principal.java`, `JwtFilter.java`; `AppService`, `CatalogAdminService`, `OperationsService`, `FinanceService` use tenant-scoped lookups | `frontend/src/main.tsx` dispatches Platform/Vendor/Customer; permission-driven tabs are UI-only | V1 tenant-owned tables; later tenant-scoped indexes/constraints; `app_user.email` remains globally unique; composite tenant FKs generally absent | `Release12TenantIntegrationTest`, `Release3OperationsIntegrationTest`, `Release4FinanceServiceAndPlatformTest` pass; 31-test suite | Not every endpoint/resource ID/download/export/filter is tested for cross-tenant substitution; DB constraints do not independently guarantee tenant consistency | Critical | Create endpoint-by-role/tenant IDOR matrix; add cross-tenant tests for orders, invoices, payments, quotations, users and attachments; decide email uniqueness model; add composite constraints where feasible |
| F02 | Authentication | Secure login/logout/refresh/reset, remove reset token disclosure, privileged MFA | Implemented but Defective | `AuthLifecycleService.forgot` now stores SHA-256 token hash and calls `NotificationService.sendPasswordReset`; `Controller.login` and `/auth/mfa/verify`; `verifyMfaChallenge` enforces expiry, single use and five attempts | `frontend/src/main.tsx` login → MFA code → verify; forgot/reset flow reads resetToken from emailed URL; access/refresh tokens still in localStorage; 401 clears session instead of automatic refresh | `password_reset_token`, `refresh_token`; V4 token version; V24 `mfa_challenge` | `Release2SecurityTest` passes; no dedicated MFA/reset email integration tests; test profile explicitly sets `platform-mfa-enabled: false` | No progressive login/reset throttling, no MFA test coverage, 2-hour JWT, browser localStorage exposure, no automatic refresh; challenge issuance is not throttled | Critical | Add auth abuse/rate-limit tests, MFA success/failure/expiry/reuse tests, session refresh tests; assess HttpOnly cookie/CSP strategy and access-token lifetime |
| F03 | Vendor management | Platform onboarding with pending/verified/active lifecycle, vendor profile and initial-admin invitation | Partially Implemented | `AuthLifecycleService.createTenant` creates tenant with status ACTIVE, standard roles/categories and initial VENDOR_ADMIN; `TenantAdminService` supports platform list/detail/edit/export | Platform component in `frontend/src/main.tsx` supports create/view/edit display name/activate/suspend/export | `tenant`, `app_user`, `role`, `user_role`; no vendor verification/document/invitation schema | `Release12TenantIntegrationTest`, `Release4FinanceServiceAndPlatformTest` pass for current provisioning/admin flows | Tenant is immediately ACTIVE; no pending verification, vendor document collection, invitation or complete legal/contact/store profile workflow | High | Implement/approve minimum onboarding state machine and secure invitation; complete vendor profile and activation checks |
| F04 | User and RBAC management | Tenant user lifecycle and role assignment with backend authorization and manageable permissions | Partially Implemented | `Controller.createVendorEmployee` requires VENDOR + USER_MANAGE; `AuthLifecycleService.createEmployee`; `CatalogAdminService.updateEmployee` tenant-checks and blocks privileged role assignment | Vendor `Crud` in `frontend/src/main.tsx` provides user create/edit/enable/disable and role selection | `app_user`, `role`, `permission`, `user_role`, `role_permission`; V5/V7/V10/V13 seed static role maps | `Release12TenantIntegrationTest`, `Release2SecurityTest` pass selected role restrictions | No custom role/permission admin; no invite/first-login flow; no complete Platform/Vendor Admin/Vendor User/Customer endpoint matrix | High | Confirm fixed-role vs custom-role Phase 1 scope; add role-by-endpoint authorization tests and privilege escalation tests |
| F05 | Customer management | Vendor customer CRUD and customer self-service restricted to own tenant/profile | Partially Implemented | `AuthLifecycleService.register`, `CatalogAdminService`, `AppService.list`, `FinanceService` customer/order/invoice ownership checks | Customer component in `frontend/src/main.tsx` includes catalogue, enquiries, quotations, orders, invoices, payments, installations, warranties and service | `customer_profile`, `app_user`, `enquiry`, `customer_order`, `invoice`, `payment` | `Release12TenantIntegrationTest`, `Release4FinanceServiceAndPlatformTest` pass selected ownership tests | Basic profile/address/activity; global email uniqueness; incomplete customer order line details and actions | Medium | Complete customer profile/activity as approved; expose customer-authorized order item/status details; add cross-customer tests for quote/order/invoice/payment/attachment |
| F06 | Solar product and package management | Category/product/package CRUD, attributes, publish lifecycle and safe public catalogue | Partially Implemented | `CatalogAdminService`, `AppService.publicProducts/publicPackages`; tenant checks and active store resolution | `RecordEditor.tsx`, `PackageForm` and `PackageImageUpload.tsx` in `main.tsx`; `PublicHome.tsx` public catalogue and filters | `product_category`, `product`, `solar_package`, `package_item`; V6/V11/V16 package/product extensions | `Release12TenantIntegrationTest`, `Release3OperationsIntegrationTest`, `Release4AccessAndPackageTest` pass | Package attributes/detail/publication lifecycle incomplete; public filtering is largely client-side; no scalable server-side catalogue pagination/filter API | Medium | Confirm package minimum attribute set; complete package detail/specification and lifecycle; add cross-tenant/inactive-category/public-store tests |
| F07 | Enquiries and lead management | Enquiry intake, lead conversion/assignment/stage/follow-up and audit history | Partially Implemented | `AppService.createPublicEnquiry`, `convertLead`, `updateLead`, `leadHistory`; endpoints `POST /public/{storeSlug}/enquiries`, `POST /customer/enquiries`, `GET /leads/{id}/history` | `PublicHome.tsx`; Enquiries/Leads CRUD and actions in `frontend/src/main.tsx`; SurveyForm links lead | `enquiry`, `sales_lead`, `lead_history`; V8 assignment history | `Release12TenantIntegrationTest` proves selected enquiry → lead → survey → quote → order path | No lead source/activity/task timeline; follow-up is a date; conversion concurrency/duplicate prevention not proven; prompt-based editor | High | Add safe/idempotent conversion and concurrency test; add CRM activity/follow-up model if in approved MVP; replace prompt editor with structured form |
| F08 | Quotations | Versioned quote, approval, send, PDF and latest-revision customer decision | Implemented but Defective | `AppService.quote/action`, `QuotationRevisionService`; `action("send")` now calls `NotificationService.sendBusinessNotification`; endpoints `POST /quotations`, `POST /quotations/{id}/{action}`, `GET /quotations/{id}/pdf`, `POST /quotations/{id}/revisions` | `QuoteForm`, quotation approval/send/revision/history/PDF/customer accept/reject actions in `frontend/src/main.tsx` | `quotation`, `quotation_item`, `idempotency_record`; V3/V9 | `Release12TenantIntegrationTest` covers selected send/accept/order conversion; no notification delivery integration/latest-revision concurrency/PDF content tests | Package tax remains zero in quotation calculation; order snapshots preserve undiscounted line base while header uses discounted grand total; email is plain text/no PDF attachment | Critical | Fix and test tax/discount snapshot model before acceptance; test latest revision/expiry/concurrent acceptance and SMTP event failure handling |
| F09 | Orders | Idempotent order creation, status transitions, payment state and customer detail | Partially Implemented | `AppService.order`; `OperationsService` status/reservation flows; order status history | Vendor/customer order views in `frontend/src/main.tsx`; customer order details/actions incomplete | `customer_order`, `order_item`, `idempotency_record`, `order_status_history`; V14 tax snapshot | `Release12TenantIntegrationTest` proves accepted quote conversion/idempotent key; `Release3OperationsIntegrationTest` selected stock/order paths | Order payment state separate from payment records; UI doesn't fully expose order line details; invoice totals not reconciled | High | Define order/payment status contract; complete customer order details/actions; add full state transition, cancellation and invoice reconciliation tests |
| F10 | Basic inventory | Tenant-safe stock receipt/issue/return/damage/adjust/transfer/reservation/ledger | Fully Implemented and Verified (core Phase 1 scope) | `OperationsService` and inventory endpoints implement tenant-scoped stock operations, reservations and release | `OperationsPanel.tsx` inventory operations UI | `warehouse`, `inventory_balance`, `inventory_transaction`, `inventory_reservation`; V11–V15 | `Release3OperationsIntegrationTest` and other integration tests pass; 31 total tests pass | Further rollback/transfer/cancellation and full business acceptance cases remain desirable; this status is limited to basic inventory core | Medium | Retain; expand rollback/transfer/cancellation and high-contention tests before scale sign-off |
| F11 | Invoice generation | Tenant/fiscal invoice numbering, item/tax snapshots, PDF and customer delivery | Implemented but Defective | `FinanceService.createInvoice` (lines 71–109) creates invoice and sends `INVOICE_ISSUED`; lines 89–102 recalculate from order items and line tax snapshots | `FinanceServicePanel.tsx` invoice issue/list/PDF/payment UI; customer invoice tab in `main.tsx` | `invoice`, `invoice_item`, `invoice_sequence`, credit note/payment tables; V18–V20 | No invoice-specific integration test found in test tree; CI passes existing tests only | Quotation discount is not carried into order line taxable snapshots, so invoice subtotal/grand total can differ; no PDF attachment email; no statutory GST certification | Critical | Preserve quote discounts/taxes into immutable order/invoice snapshots; add invoice/PDF/numbering/tax/payment/customer ownership tests; accountant to approve statutory GST behavior |
| F12 | Email notifications | SMTP provider, secure templates, delivery tracking and relevant business event notifications | Partially Implemented | `NotificationService` sends password-reset/MFA/business emails and stores delivery status; called by `AuthLifecycleService.forgot/createMfaChallenge`, `AppService.action(send)`, `FinanceService.createInvoice` | Login MFA, forgot/reset UI and quotation send action in `frontend/src/main.tsx`; no notification inbox/history UI | V23 `notification_delivery`; `NotificationDelivery` and repository; Spring Mail dependency and application mail config | `NotificationServiceTest` has 2 mocked sender tests; no live Mailpit end-to-end test or retry test | No retry worker, queue, attachment, bounce tracking, delivery status API/UI, in-app notifications or user-invite/order-status/service-event coverage; synchronous send can block transaction | High | Add durable async outbox/retry/backoff, event coverage, attachments where required, delivery admin visibility and Mailpit integration tests |
| F13 | Basic dashboard | Platform, vendor and customer role-specific dashboard metrics | Partially Implemented | `TenantAdminService` platform tenant aggregation only; no vendor KPI aggregation service/API identified | Platform dashboard in `frontend/src/main.tsx`; Vendor component is module navigation, Customer is tabbed portal; no vendor/customer summary dashboard | Business source tables exist; no dashboard projection/materialized reporting model | `Release4FinanceServiceAndPlatformTest` covers platform tenant dashboard only; no vendor dashboard/role visibility tests | No vendor revenue/outstanding/lead conversion/sales/low-stock/order/service metrics or customer summary; no date filters/role KPI matrix | High | Agree KPI definitions; add tenant-scoped vendor dashboard API/UI and role visibility/performance tests; keep platform metrics separate |

## 6. Supporting Phase 1 SRS findings

| Audit Finding ID | Supporting requirement | Implementation Status | Evidence | Remaining gap / required action |
|---|---|---|---|---|
| S01 | Site survey basics | Partially Implemented | `SiteSurvey`, `AppService.survey`, `POST /api/v1/surveys`, `SurveyForm`; fields include schedule/property/roof/area/shading/load/notes | Completion checklist, customer acknowledgement, photos and full survey lifecycle not demonstrated |
| S02 | Customer portal | Partially Implemented | Customer component in `frontend/src/main.tsx`; own catalogue/enquiries/quotes/orders/invoices/payments/installations/warranties/service | No customer summary dashboard; incomplete order line details/actions |
| S03 | In-app notifications | Not Implemented | V23 persists email delivery attempts only; no notification inbox/entity/API/UI | Implement in-app notification feed only if it remains in approved Phase 1 SRS |
| S04 | General audit logging | Not Implemented | `lead_history`, `order_status_history`, `service_event` are workflow histories, not a general audit ledger | Add append-only actor/action/entity/tenant/before-after/security event audit capability and access-controlled search |
| S05 | Operational monitoring | Partially Implemented | Actuator health/info and `ServiceSlaScheduler`; CI MySQL startup gate currently fails due mail health | Add structured metrics/logs/tracing/alerts, readiness-vs-liveness separation and documented backup/restore verification |
| S06 | Reporting/export | Partially Implemented | Platform tenant CSV/XLSX/PDF export in `TenantAdminService` and Platform UI | Vendor sales/lead/inventory reports and export center absent; defer non-Phase-1 reports if approved |

## 7. End-to-end workflow validation

| Workflow | Status | Evidence and conclusion |
|---|---|---|
| Vendor onboarding and management | Partially Implemented | Platform tenant create/list/edit/status/export and initial Vendor Admin provisioning exist. Tenant becomes ACTIVE immediately; no verification/invitation lifecycle. |
| User create/update/activation/role assignment | Implemented but Not Fully Verified | `POST /api/v1/users`, `PATCH /users/{id}`, `PATCH /users/{id}/enabled`, `GET /roles`; frontend forms exist. Selected tests pass; full role-by-endpoint matrix and email invitation absent. |
| Customer management | Implemented but Not Fully Verified | Vendor CRUD and customer registration/portal exist; selected customer ownership tests pass. Full customer lifecycle and customer-to-customer IDOR matrix not run. |
| Product/package management | Implemented but Not Fully Verified | CRUD, package component, image upload and public catalogue exist; selected integration tests pass. Complete package specification/publication lifecycle not present. |
| Enquiry → lead → survey | Implemented but Not Fully Verified | Integration path in `Release12TenantIntegrationTest`; no conversion concurrency proof or complete CRM activity workflow. |
| Lead → quotation create/update | Implemented but Not Fully Verified | `AppService.quote`, revision service and QuoteForm; tax/discount snapshot issue remains. |
| Quotation send | Partially Implemented | `AppService.action("send")` sets SENT and calls SMTP NotificationService; delivery is synchronous and failure/retry/attachment behavior is not end-to-end tested. |
| Quotation acceptance | Implemented but Not Fully Verified | Customer acceptance checks ownership/status/validity; no specific latest-revision race/expiry regression test identified. |
| Accepted quotation → order | Implemented and tested for core path | Idempotency-Key conversion in `AppService.order`; `Release12TenantIntegrationTest` passes. Financial snapshots are still defective. |
| Order status management | Partially Implemented | `OperationsService` and order status history; no complete state machine/cancellation/payment synchronization test suite. |
| Inventory reservation and stock updates | Fully Implemented and Verified (core scope) | Inventory service, ledger and reservation concurrency tests pass in CI. |
| Invoice generation and download | Implemented but Defective | Invoice creation/PDF endpoints and UI exist; no invoice-specific tests; quote discount reconciliation defect remains. |
| Email notifications | Partially Implemented | Password reset, MFA, quotation sent and invoice issued invoke SMTP. Mailpit is configured in Compose; no actual inbox/E2E delivery test was executed in this run. |
| Dashboard metrics and role visibility | Partially Implemented | Platform tenant metrics only; vendor/customer KPI dashboard and comprehensive role visibility tests absent. |

## 8. Authentication, tenant isolation and RBAC checks

### Confirmed positive behavior from code and selected tests

- `Principal` contains authenticated user ID, tenant ID, account type, roles and permissions.
- `JwtFilter` revalidates persisted user/tenant/token version and active tenant state on protected requests.
- Vendor service operations generally derive tenant ID from the principal and use tenant-scoped repository lookups.
- Public catalogue/enquiry endpoints resolve tenant by active store slug.
- Customer finance/service paths apply customer ownership checks.
- Employee create/update paths require vendor account and appropriate user permissions and block platform-role assignment through employee role APIs.
- Selected cross-tenant and customer ownership tests pass in CI.
- Frontend 401 handling clears expired sessions; 403 is surfaced as an authorization error in the shared API wrapper.

### Not fully verified

- A complete endpoint-by-endpoint ID substitution test suite was not found.
- Every role (Platform Super Admin, Vendor Admin, Vendor User role families and Customer) has not been tested against every API.
- Frontend route/menu visibility cannot prove backend authorization.
- Global `app_user.email` uniqueness and non-composite tenant relationship constraints remain.
- No general denied-cross-tenant security event audit log exists.

**Conclusion:** tenant isolation is meaningfully implemented in service code but is not comprehensively verified. Do not claim universal tenant isolation from the current test suite.

## 9. Database, migration, configuration and build verification

### Database migrations

- Target branch includes Flyway V1–V24.
- GitHub Actions MySQL 8.4 migration job applied V1 through V24 successfully to a clean CI schema.
- The application startup log confirms Flyway successfully applied all 24 migrations and Hibernate proceeded to schema initialization.
- This is positive clean-database evidence. It does not prove migration safety against every historical production dataset or live backup/restore behavior.
- Test profile `backend/src/test/resources/application-test.yml` uses H2 MySQL mode, Hibernate `create-drop`, and `flyway.enabled=false`; the separate CI MySQL migration job is therefore important.

### CI run evidence

GitHub Actions run: [#381 — Implement Phase 1 email notifications and platform MFA foundation](https://github.com/rambrij/saumya-solar-energy-product/actions/runs/42455347378)

| Job | Result | Evidence |
|---|---|---|
| Backend compile and tests | PASS | `mvn --batch-mode --no-transfer-progress clean verify`; 31 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS |
| Frontend typecheck and production build | PASS | `npm install`; `npm run build` (tsc + vite build) succeeded |
| MySQL 8.4 migrations | PASS | V1–V24 applied successfully |
| Start Spring Boot against MySQL 8.4 | FAIL | Health gate did not report UP; Actuator `MailHealthIndicator` attempted `localhost:1025`, connection refused |

### Startup failure detail

- `application.yml` defaults SMTP host/port to `localhost:1025`.
- Docker Compose correctly uses `MAIL_HOST=mailpit`, `MAIL_PORT=1025` and starts a Mailpit service.
- The GitHub Actions Spring Boot/MySQL smoke job does not start Mailpit or set a reachable SMTP host.
- The Actuator mail health indicator attempts SMTP connection and causes the health response to fail the workflow's `status:UP` check.
- CI logs show Flyway V1–V24 succeeded before the health check failure.

**Classification:** configuration/readiness integration defect. It does not invalidate the passing Maven build, frontend build or migration job. The backend process reaches the health endpoint, but the required health gate is not successful.

### Configuration review

- `backend/src/main/resources/application.yml` externalizes DB credentials, JWT secret, CORS, SMTP, MFA toggle and public base URL.
- `docker-compose.yml` includes Mailpit at localhost-only ports 8025/1025 and points backend SMTP to `mailpit:1025`.
- `backend/pom.xml` includes `spring-boot-starter-mail`.
- `PLATFORM_MFA_ENABLED` defaults true; test profile disables it.
- Production SMTP provider settings are documented in README. Actual provider credentials, sender-domain verification, SPF/DKIM/DMARC and real delivery are not verified.
- Docker Compose demo credentials and seed settings are local-only and must not be reused in production.

## 10. New defects and regression risks identified

1. **SMTP health dependency breaks CI startup gate (High):** health endpoint checks mail connectivity against localhost:1025 when no mail service is present in the smoke-test job. Separate liveness from external dependency readiness or provide a test SMTP service/configuration.
2. **Notification send is synchronous (High):** `NotificationService.deliver` persists PENDING, calls `JavaMailSender.send` inline, then marks DELIVERED/FAILED. Quotation/invoice/password-reset/MFA request latency depends on SMTP availability.
3. **No retry/outbox worker (High):** failed attempts are recorded as FAILED but no retry/backoff/queue consumer is implemented. A temporary provider outage causes notification loss unless a user repeats the triggering action.
4. **Email not comprehensive (High):** quote and invoice, password reset and platform MFA are connected. Vendor invitations, order status, inventory/service events and SLA escalation are not generally wired.
5. **No invoice attachment (Medium/High):** invoice email contains a text link/message; it does not attach the generated invoice PDF. The audit recommendation called for PDF attachments.
6. **Invoice discount/tax mismatch remains (Critical):** `FinanceService.createInvoice` sums order item `lineTotal` and recalculates tax; the quotation discount is not reliably carried into the order item taxable snapshot. Invoice can overstate the accepted amount.
7. **No invoice-specific tests (High):** invoice issuance, PDF, discount/tax rounding, numbering concurrency, customer ownership, void/credit note and payment reconciliation are not covered by a dedicated invoice integration test.
8. **MFA has no dedicated automated test (High):** test profile disables Platform MFA, and no MFA challenge test class exists. Primary login throttling and challenge issuance throttling are also not evidenced.
9. **No delivery history API/UI (Medium):** notification delivery rows are stored but there is no notification administration/history screen or delivery status API surfaced in the UI.
10. **Existing feature regression assurance is limited:** all 31 backend tests pass, but there are no frontend unit/E2E tests and CI smoke startup is failing.

## 11. Fully resolved, partially resolved and unresolved findings

### Fully resolved from the audit's material findings

- Password-reset token is no longer returned in the API response. `AuthLifecycleService.forgot` returns only the generic message and emails the raw single-use token link; the database stores the hash.
- Platform Super Admin MFA has a concrete email-code challenge flow, challenge persistence, five-minute expiry, single-use state and five-attempt limit. **Implementation exists, but end-to-end security acceptance remains unverified.**
- Basic email delivery foundation now exists for reset/MFA/quotation/invoice events; this is not a full resolution of the broader notification requirement.
- New V23/V24 migrations are applied successfully in CI MySQL 8.4.

### Partially resolved

- Authentication security: reset-token exposure fixed and platform MFA added; token storage, throttling, automatic refresh and tests remain.
- Email notifications: SMTP provider abstraction is not provider-neutral but uses Spring Mail; selected event emails work in code; no retry/attachment/history API/full event coverage.
- Quotations: send action now invokes an email notification; tax/discount snapshots and email delivery verification remain.
- Invoicing: invoice issue notification added; financial reconciliation defect and invoice tests remain.
- CI: backend tests/frontend build/migrations pass, but startup health gate fails due SMTP configuration.

### Unresolved

- Multi-tenant exhaustive isolation and global email uniqueness/composite tenant FK issues.
- Vendor onboarding PENDING/VERIFIED/ACTIVE, verification and invitation.
- Custom RBAC administration and full role-by-endpoint matrix.
- Customer profile/activity/order detail gaps.
- Package attribute/publication and scalable server-side catalogue gaps.
- Lead source/activity/follow-up and conversion concurrency.
- Order payment/status/detail contract and financial snapshot.
- Invoice discount/tax reconciliation and invoice-specific test suite.
- Complete email notification lifecycle, retry/outbox, attachment and in-app notification.
- Vendor/customer dashboard KPIs.
- General append-only audit logging.
- Frontend automated unit/E2E tests and operational monitoring/backup restore evidence.

## 12. Prioritized remediation plan

### P0 — Critical before MVP acceptance

1. Fix quote → order → invoice financial snapshot so quotation discounts, package tax and tax rounding are preserved consistently. Add invoice/order reconciliation assertions.
2. Correct CI/Actuator mail health configuration so the Spring Boot/MySQL smoke job can validate application health without depending on an unavailable SMTP daemon; keep SMTP readiness semantics explicit.
3. Add security tests for password-reset token non-disclosure, reset expiry/reuse, MFA challenge ownership/expiry/attempt limits and Platform MFA login.
4. Build a tenant endpoint/resource IDOR matrix and test foreign tenant/customer IDs across reads, writes, exports, invoice PDFs, payment receipts and attachments.

### P1 — High priority

1. Add durable notification outbox/async delivery, retry/backoff, delivery history API and PDF attachment support where required.
2. Wire email for the approved business events, including vendor/employee invitation and order status if in Phase 1 scope.
3. Define vendor onboarding lifecycle and secure initial-admin invitation.
4. Confirm fixed roles versus custom roles; implement only the approved scope and test every role against sensitive endpoints.
5. Add vendor dashboard KPIs and tenant/role visibility tests.
6. Add order payment/status consistency and customer order details.
7. Add financial integration tests and end-to-end sales → order → inventory → invoice workflow tests.

### P2 — Medium priority

1. Complete customer profile/address/activity and package specification/publication fields.
2. Add lead source/activity/follow-up and safe conversion concurrency controls.
3. Add frontend unit/E2E tests and API contract tests.
4. Add general audit logging if required by Phase 1 SRS.
5. Add observability, alerts and documented backup/restore evidence.

### P3 — Scope decisions / lower priority

1. Agree whether in-app notifications, full customer dashboard, advanced reports and custom roles are mandatory for Phase 1 acceptance or should be formally deferred.
2. Keep Phase 2/3 subscriptions, multi-branch/warehouse plan enforcement, WhatsApp/SMS, advanced analytics, CMS/blog and mobile applications out of Phase 1 unless the approved roadmap changes.

## 13. Items not verified in this validation

- No local checkout/pull was possible because the execution environment could not resolve github.com; remote refs and source were read from GitHub, and the GitHub Actions run checked out the PR merge ref for this branch head.
- No manual browser-based end-to-end test of every screen/form/menu/route.
- No deployed production environment/API test.
- No real customer inbox delivery test or access to production SMTP logs/provider account.
- No production database/secret/hosting configuration access.
- No penetration test, DAST, load test, concurrency stress test or backup restore test.
- No statutory GST/legal invoice compliance review.
- No full role-by-endpoint authorization matrix or exhaustive IDOR suite.

These are **unable to verify**, not passed.

## 14. Final acceptance recommendation

**Do not mark Phase 1 Commercial MVP as fully complete yet.**

The implementation branch has materially improved since the original audit. The reset-token disclosure is fixed; platform MFA and SMTP delivery are implemented; V23/V24 migrations and selected notification tests pass. The CI evidence is stronger than the original audit baseline.

Nevertheless, the latest CI run has a failing Spring Boot health gate, the quotation/order/invoice discount reconciliation defect remains, and important vendor onboarding, RBAC, tenant-isolation assurance, dashboard, notification reliability and audit logging requirements remain incomplete.

This report is a validation artifact only. No remediation has been performed. **Stop here and wait for explicit product-owner confirmation before implementing any fix.**
