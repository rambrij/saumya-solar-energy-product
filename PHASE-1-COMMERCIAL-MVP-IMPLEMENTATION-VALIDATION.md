# Phase 1 Commercial MVP — Implementation Validation and Independent Re-Audit

**Validation date:** 2026-11-08  
**Repository:** `rambrij/saumya-solar-energy-product`  
**Baseline branch:** `phase-1-commercial-mvp-implementation`  
**Remediation branch:** `phase-1-commercial-mvp-remediation`  
**Validated code commit:** `4ad0e12ea70f37a210fd8cda137930b5e98a0c0b`  
**Source audit:** `phase-1-commercial-mvp-audit/PHASE-1-COMMERCIAL-MVP-AUDIT.md`  
**Follow-on remediation PR:** https://github.com/rambrij/saumya-solar-energy-product/pull/7  
**Successful CI run:** https://github.com/rambrij/saumya-solar-energy-product/actions/runs/42469334656

> This report assesses the implementation on the remediation branch against the complete source audit. It does not claim that 100% completion or zero defects is achievable or proven. No production deployment or merge was performed.

---

## 1. Executive summary

The remediation branch now contains substantial implementation beyond the original first tranche. It closes or materially reduces the principal defects from the source audit:

- Quotation discounts and tax are persisted as accepted financial snapshots through order and invoice creation.
- Package tax is configurable rather than hard-coded to zero.
- Vendor administrators are provisioned through expiring one-time invitations rather than a platform-supplied password.
- Pending vendors receive restricted onboarding access only; normal business APIs remain forbidden until platform approval.
- Vendor legal profiles and private verification documents can be submitted and reviewed.
- Vendor activation requires a completed legal profile and at least one approved verification document.
- Notification delivery has a durable retry/outbox workflow and a recipient-scoped in-app inbox.
- Lead follow-up tasks and scheduled due reminders are implemented.
- Vendor and customer dashboard summaries are served by backend endpoints with permission/account scoping.
- General authenticated API audit history has an authorized API and vendor UI.
- Order cancellation is tested against inventory reservation release.

<badge color="success">Latest CI: 5 of 5 jobs passed</badge>

| Validation area | Result |
|---|---|
| Backend compile and automated tests | PASS — 43 tests, 0 failures, 0 errors, 0 skipped |
| Frontend TypeScript and production build | PASS |
| Flyway migrations on clean MySQL 8.4 | PASS — V1 through V35 |
| Spring Boot startup against MySQL 8.4 and Mailpit | PASS — health UP |
| Browser end-to-end workflows | PASS — 3 Playwright tests |
| Total CI jobs | 5 / 5 passed |

**Estimated Phase 1 completion: 84%.** This is a qualitative estimate across the Phase 1 modules and supporting capabilities listed in this report. It is not a code-coverage figure, penetration-test result, accounting certification, or guarantee of defect-free operation.

**Disposition: Implemented but Not Fully Verified — not yet approved for production acceptance.** Core remediation has passed automated validation. Several requirements remain partial, and production infrastructure, security, accounting and operational acceptance checks have not been performed.

---

## 2. Status definitions

- **Fully Implemented and Verified:** the scoped requirement is implemented across the relevant layers and has direct automated runtime evidence for its acceptance behavior.
- **Implemented but Not Fully Verified:** the core behavior is implemented and tested, but material edge cases, business sign-off, scale or production evidence remain.
- **Partially Implemented:** meaningful implementation exists, but an important part of the expected behavior or acceptance scope remains incomplete.
- **Implemented but Defective:** implementation exists but a confirmed defect remains.
- **Not Implemented:** no implementation evidence was found.
- **Not Applicable:** outside the agreed Phase 1 scope.

Percentages below estimate delivered Phase 1 behavior. They are not source-file or test-count percentages.

---

## 3. Requirement-wise completion

| Phase 1 requirement | Estimated completion | Status | Remaining gap |
|---|---:|---|---|
| Multi-tenant architecture | 80% | Partially Implemented | Global user email uniqueness, incomplete composite tenant-aware foreign keys, and non-exhaustive IDOR matrix |
| Authentication | 80% | Partially Implemented | Login/reset throttling, safer browser token storage, automatic refresh UX and auth-abuse testing |
| Vendor management | 95% | Implemented but Not Fully Verified | KYC policy/legal validation, malware scanning, persistent production document storage and operational review controls |
| User and RBAC management | 85% | Partially Implemented | Exhaustive role × endpoint × HTTP status matrix and platform user governance |
| Customer management | 80% | Partially Implemented | Customer activity/communication history, multi-address workflow and identity uniqueness decision |
| Solar product and package management | 85% | Implemented but Not Fully Verified | Legacy package tax review, complete specification/publication lifecycle and catalogue query performance |
| Enquiries and lead management | 85% | Implemented but Not Fully Verified | Full activity timeline, conversion race coverage and reminder delivery under real provider outages |
| Quotations | 90% | Implemented but Not Fully Verified | Complete branded PDF terms/payment schedule, attachment delivery and latest-revision concurrency coverage |
| Orders | 90% | Implemented but Not Fully Verified | Full transition/state matrix and additional cancellation/payment/inventory combinations |
| Basic inventory | 90% | Implemented but Not Fully Verified | Transfer rollback, multi-line reservation rollback and production warehouse acceptance |
| Invoice generation | 90% | Implemented but Not Fully Verified | Accountant/statutory review, full credit-note/void/partial-payment/concurrency matrix |
| Email notifications | 90% | Implemented but Not Fully Verified | Production provider, HTML templates, attachment support, bounce/complaint handling and event coverage |
| Basic dashboard | 90% | Implemented but Not Fully Verified | Database-level aggregate optimization, agreed KPI definitions, date filters and realistic-scale performance tests |

---

## 4. Audit finding traceability

The source audit's 13 primary requirements are mapped to F01–F13 in the same order as source-audit sections 3.1–3.13. Supporting Phase 1 SRS capabilities are mapped to S01–S06.

| Audit Finding ID | Phase 1 Requirement | Expected Fix | Implementation Status | Backend Evidence | Frontend Evidence | Database Evidence | Test Evidence | Remaining Gap | Severity | Required Action |
|---|---|---|---|---|---|---|---|---|---|---|
| F01 | Multi-tenant architecture | Enforce tenant scope on every protected read/write/download/export and prevent IDOR | Partially Implemented | `security/JwtFilter.java`, `security/Principal.java`; tenant-scoped service/repository access in `AppService`, `OperationsService`, `FinanceService`; pending-vendor route restriction; `DashboardService` | Account/permission-specific navigation in `frontend/src/main.tsx`; customer/vendor/platform portals | V1 baseline tenant tables; V25–V35 additive tenant-owned records and indexes | `Release12TenantIntegrationTest`, `Release3OperationsIntegrationTest`, `Release4FinanceServiceAndPlatformTest`; 43-test suite passed | No exhaustive route-by-route cross-tenant test for every invoice, payment, export, attachment, query filter and request parameter. Global `app_user.email` uniqueness and incomplete composite tenant FKs remain | Critical | Complete API × resource × tenant IDOR matrix; decide email identity model; add composite tenant-aware constraints where safe |
| F02 | Authentication | Secure reset/invitation, MFA and token lifecycle; do not disclose reset tokens | Partially Implemented | `AuthLifecycleService.forgot`, reset-token consumption, refresh rotation/reuse handling, MFA challenge, `JwtService`, `JwtFilter`; vendor invitation token hashing and one-time acceptance | Login/register/forgot/reset/MFA and vendor-invitation acceptance flows in `frontend/src/main.tsx` | Existing user/refresh/reset/MFA tables; V31 invitation token hash and expiry | `Release2SecurityTest`, `Release12TenantIntegrationTest`; invitation reuse/revocation tested | No distributed login/reset rate limiter or progressive lockout; browser tokens remain in localStorage; no full XSS/CSRF/browser token threat assessment; no production MFA/provider test | Critical | Add rate limiting and security-event audit; review token storage and refresh strategy; run authentication abuse testing |
| F03 | Vendor management | Pending → invited → profile/KYC submitted → document review → verified/active lifecycle | Implemented but Not Fully Verified | `AuthLifecycleService.createTenant`, `acceptVendorInvitation`, `resendVendorInvitation`; `VendorProfileService`; `VendorVerificationDocumentService`; `TenantAdminService.verify`; `JwtFilter` limits pending vendors to profile/document endpoints | Platform tenant detail includes legal profile, document download/review, verification and invitation resend; vendor Settings includes profile/document upload | V29 verification lifecycle; V31 invitation; V33 legal profile; V34 private verification documents | `Release12TenantIntegrationTest.platformCanProvisionTenantAndVendorAdminCanManageSalesEmployee` tests invitation revoke/resend, acceptance, pending-route restrictions, profile completion, KYC review and activation; Playwright vendor profile/document upload | No malware scanning, external company/GST verification, required-document policy by vendor type or production persistent-volume test. Document storage is local private filesystem configured by `VENDOR_KYC_DIR` | High | Configure persistent private storage; add malware scanning and business-approved KYC checklist; test recovery and retention |
| F04 | User and RBAC management | Enforce role-to-permission allow-list and backend authorization for each operation | Partially Implemented | `VendorRoleAdminService`, `TenantAdminService`, `JwtFilter`; custom role creation/edit/delete and permission delegation restrictions | Role manager and permission-driven navigation in `frontend/src/main.tsx` | Existing role/permission/user-role tables | `Release12TenantIntegrationTest.rolePermissionMatrixRejectsPrivilegedActionsForSalesCustomerAndPlatformUsers`; cross-tenant employee assignment and escalation rejection | The matrix tests representative roles/endpoints, not every standard/custom role × every route. Platform user lifecycle and permission simulation remain incomplete | Critical | Generate exhaustive API × role matrix including expected 401/403/404 and negative IDOR cases |
| F05 | Customer management | Vendor-managed customer records and customer-owned self-service data | Partially Implemented | Customer CRUD/ownership checks in `AppService`, `CatalogAdminService`; customer order detail ownership filtering; customer dashboard in `DashboardService` | Customer portal, My Orders, invoices, service requests, notification inbox and dashboard in `frontend/src/main.tsx` | Existing customer/profile/order/invoice/payment tables | `Release12TenantIntegrationTest` customer ownership, notification recipient isolation and dashboard role tests; Playwright customer portal test | Customer notes, contact/communication activity history, multi-address workflow and cross-vendor identity model remain | Medium | Add customer activity/address model and tenant-aware identity policy |
| F06 | Solar product and package management | Correct package tax, vendor catalogue CRUD and public catalogue behavior | Implemented but Not Fully Verified | `SolarPackage.taxPercent`; `AppService.quote` uses configured package tax; catalogue services remain tenant-scoped | Product/package forms in `frontend/src/main.tsx`; public catalogue in `frontend/src/PublicHome.tsx` | V30 `solar_package.tax_percent` | `Release12TenantIntegrationTest.vendorAdminCanCreateCategorizedProductAndConfigurablePackage`; frontend build passed | Existing packages migrated with 0.00 tax need business review; full publication/specification lifecycle and large catalogue performance remain | High | Review migrated tax rates; approve product/package data and add publication/query tests |
| F07 | Enquiries and lead management | Enquiry routing, lead history, assigned follow-up tasks and reminders | Implemented but Not Fully Verified | `LeadFollowUpTaskService`, `LeadFollowUpReminderWorker`, `NotificationService`; tenant and assignee validation; row-locked due-task query | Lead follow-up action in vendor CRM; notification inbox | V35 `lead_follow_up_task`; V23/V27/V32 notification delivery/inbox schema | `Release12TenantIntegrationTest` creates/lists/completes follow-up tasks; `NotificationOutboxWorkerTest`; full backend suite passed | Reminder scheduler is not stress-tested with multiple worker instances or a real SMTP outage; no complete CRM activity timeline or concurrent duplicate conversion matrix | High | Add worker concurrency/failure tests and CRM activity timeline |
| F08 | Quotations | Preserve accepted discount/tax snapshots through revisions, orders and invoices | Implemented but Not Fully Verified | `AppService.quote`, `QuotationRevisionService`, `FinanceService.createInvoice`; per-line rounded discount/tax reconciliation | Quote creation, approval, send, revision and PDF actions in `frontend/src/main.tsx` | V25 accepted quotation snapshots; V26 quotation/order line snapshots | `Release12TenantIntegrationTest` tests discounted quote → order → invoice and rounding edge case | PDF branding, full commercial/warranty terms, payment schedule, attached email PDFs and latest-revision concurrency/expiry remain | High | Complete commercial PDF specification and revision race/expiry tests |
| F09 | Orders | Preserve quote values, enforce status transitions and release stock before cancellation | Implemented but Not Fully Verified | `AppService.order`, `OperationsService.transition`, reservation/release and status history | Vendor/customer order panels and customer order line/payment details in `frontend/src/main.tsx` | V25/V26 financial snapshots; existing order/status/inventory ledger schema | `Release12TenantIntegrationTest` quote conversion and customer ownership; `Release3OperationsIntegrationTest.orderCancellationRequiresReservationReleaseAndThenUpdatesInventoryLedger` | Not every status transition, payment interaction, multi-line cancellation or partial fulfillment combination is covered | High | Complete state-transition and cancellation test matrix |
| F10 | Basic inventory | Maintain tenant-scoped stock ledger, reservation and rollback correctness | Implemented but Not Fully Verified | `OperationsService` receipt/issue/return/damage/adjust/transfer/reserve/release paths | `frontend/src/OperationsPanel.tsx` | Existing warehouse, balance, transaction and reservation tables | `Release3OperationsIntegrationTest` stock movement, tenant boundary, concurrent reservation and cancellation-release tests | Transfer rollback and multi-line partial-failure cases remain; no production warehouse reconciliation | Medium | Add rollback/failure injection tests and stock reconciliation acceptance |
| F11 | Invoice generation | Reconcile invoice lines/header to accepted order/quotation and allocate invoice numbers safely | Implemented but Not Fully Verified | `FinanceService.createInvoice` validates snapshots and totals; `InvoiceSequenceRepository` and `TenantRepository` use row locks for tenant/fiscal sequence allocation | Invoice create/download/payment panels and customer invoice views | V18/V20 finance schema; V25/V26 accepted financial snapshots | `Release12TenantIntegrationTest` discounted invoice line/header reconciliation; MySQL 8.4 startup and V1–V35 migration validation | No accountant/statutory GST sign-off, IRN/e-invoice, HSN/SAC, invoice-sequence load test or complete partial-payment/credit-note/void matrix | Critical | Obtain accountant approval and add concurrent numbering/payment/credit-note tests |
| F12 | Email notifications | Durable asynchronous delivery, bounded retry, delivery history and in-app inbox | Implemented but Not Fully Verified | `NotificationService`, `NotificationOutboxWorker`, `NotificationInboxService`; retry/dead-letter and recipient-scoped read state | Notification center in vendor/customer shell; email-triggering workflows | V23 delivery; V27 retry/outbox; V32 read state | `NotificationServiceTest`, `NotificationOutboxWorkerTest`; Playwright public enquiry email observed in Mailpit; 43 backend tests | No production SMTP provider abstraction, HTML templates, attachment delivery, bounce/complaint handling or sender-domain verification. Queue payloads contain sensitive message bodies until delivery/dead-letter; retention/encryption needs review | High | Configure production SMTP, add templates/attachments/provider monitoring and sensitive payload retention/encryption |
| F13 | Basic dashboard | Provide vendor/customer KPI summaries with role/account/tenant scope | Implemented but Not Fully Verified | `DashboardService.vendor`, `DashboardService.customer`; endpoints `GET /api/v1/dashboard/vendor` and `GET /api/v1/dashboard/customer`; metrics are omitted when the user lacks the related permission | Vendor Business Dashboard and customer My Solar Dashboard in `frontend/src/main.tsx` | Uses tenant-scoped business tables; no separate materialized aggregate table | `Release12TenantIntegrationTest.rolePermissionMatrixRejectsPrivilegedActionsForSalesCustomerAndPlatformUsers`; Playwright vendor/customer dashboard test | Service currently loads tenant-scoped lists and aggregates in application memory; no date filters, KPI owner sign-off or large-tenant performance test | High | Move heavy aggregation to database queries, define KPI semantics and load-test representative tenants |
| F14 | General audit logging | Record authenticated API actor, tenant, method, path, status and source IP; restrict review access | Implemented but Not Fully Verified | `AuditLogInterceptor`, `AuditLogService`, `AuditLogController`; vendor admin sees own tenant, platform super admin sees platform history | Vendor Audit Log tab in `frontend/src/main.tsx` | V28 `audit_log` | `Release12TenantIntegrationTest.authenticatedApiRequestsAreWrittenToTenantAwareAuditLog` and audit viewer authorization test; Playwright audit tab | Failed-login/unauthenticated security events, before/after field diffs, retention policy, export and search filters remain | High | Add security-event capture, immutable change events, retention and searchable filters |
| S01 | Site survey basics | Capture survey inputs and link them to lead/quotation | Partially Implemented | Existing `SiteSurvey` and `AppService.survey` | Survey form and quotation references | Existing site_survey schema | Existing sales workflow tests; no complete survey checklist evidence | Photos, customer acknowledgement, checklist and full survey completion lifecycle not verified | Medium | Complete survey checklist and attachment/acknowledgement flow |
| S02 | Customer portal | Customer self-service for catalogue, enquiries, quotes, orders, invoices, payments and service | Implemented but Not Fully Verified | Customer ownership checks, order details, customer dashboard and notification APIs | Customer portal routes/screens in `frontend/src/main.tsx` | Customer, order, invoice, payment and notification tables | Playwright customer login/dashboard/quotation workflow; Release12 customer ownership tests | Full browser flow for quote acceptance → order → invoice/payment → service is not covered in one E2E journey | High | Add one end-to-end customer commercial lifecycle test |
| S03 | In-app notifications | User-specific inbox with read state and no cross-user disclosure | Implemented but Not Fully Verified | `NotificationInboxService`, `NotificationController`; GET list and POST read endpoints filter by recipient and tenant; response omits message body | NotificationCenter in `frontend/src/main.tsx` | V32 `read_at`, V23/V27 delivery schema | Recipient isolation and read-state test in `Release12TenantIntegrationTest`; Playwright inbox test | No notification preferences, pagination, category filters or push/real-time updates | Medium | Add pagination/preferences after delivery provider coverage |
| S04 | Operational monitoring | Health, deployment checks and operational readiness | Partially Implemented | Actuator health, scheduled notification/follow-up workers, CI startup check | Frontend build and Playwright workflow | V1–V35 migrations validated | MySQL 8.4 + Mailpit startup health passed | No production metrics/tracing/alerts, load/soak test, backup/restore or DR exercise | High | Configure production monitoring/alerts and rehearse backup restore |
| S05 | Reporting and exports | Platform/vendor reports with tenant and permission scope | Partially Implemented | Existing platform tenant export; dashboard summary APIs | Platform exports and dashboard screens; vendor audit log | Existing tenant/business tables | Existing platform export tests and dashboard authorization tests | Vendor sales, lead conversion, inventory valuation and finance report center not implemented | Medium | Agree report catalogue and add tenant-scoped report endpoints |
| S06 | Release/build/database configuration | Reproducible build, clean migrations, startup and E2E validation | Implemented but Not Fully Verified | Maven compile/tests; Spring Boot health endpoint; MySQL 8.4 startup | TypeScript/Vite production build; Playwright | Flyway V1–V35 clean MySQL validation | CI run 42469334656: 43 backend tests, 3 Playwright tests, migrations, startup and frontend build all passed | CI uses H2 for the Maven integration-test profile; MySQL migration/startup are separate CI jobs. Production deployment/secrets/backups are not validated | Medium | Add MySQL Testcontainers for backend integration tests and run production deployment acceptance |

---

## 5. Fully resolved findings

The following original defects have been materially resolved with code and automated evidence:

1. **Password-reset token disclosure:** forgot-password no longer returns the reset token to the client; reset delivery is queued through the notification service.
2. **Quotation discount/tax lost before invoice:** accepted header and per-line snapshots persist through quote revision, order and invoice; invoice creation rejects inconsistent snapshots.
3. **Package tax hard-coded to zero:** package tax is configurable and used in quotation calculation.
4. **Vendor immediately ACTIVE:** new tenants start INACTIVE/PENDING_VERIFICATION; activation requires completed legal profile and an approved verification document.
5. **No secure vendor administrator setup:** one-time expiring invitation, token hashing, resend/revocation and password acceptance are implemented.
6. **No custom vendor roles:** tenant-scoped role administration and privilege allow-list exist.
7. **No reliable email retry:** durable outbox, scheduled retry and dead-letter behavior exist.
8. **No in-app notification inbox:** recipient-scoped inbox and read state exist.
9. **No general authenticated API audit history:** interceptor, persistence, API and vendor audit screen exist.
10. **No vendor/customer dashboard:** backend summary APIs and role/account-aware screens exist.
11. **No lead follow-up task workflow:** tenant-scoped tasks, assignee checks and due reminder worker exist.
12. **Order cancellation could leave reserved stock:** cancellation is blocked while reservation remains; release-before-cancel is covered by an integration test.

These are resolved scoped findings; they do not mean the broader modules are 100% complete.

---

## 6. CI and runtime evidence

**Successful CI run:** https://github.com/rambrij/saumya-solar-energy-product/actions/runs/42469334656

| CI job | Result | Evidence |
|---|---|---|
| Backend compile and tests | PASS | Maven BUILD SUCCESS; 43 tests, 0 failures/errors/skips |
| Frontend TypeScript and production build | PASS | TypeScript check and Vite production build |
| Flyway migrations | PASS | V1–V35 applied to clean MySQL 8.4 schema |
| Spring Boot startup | PASS | Backend started against MySQL 8.4 and Mailpit; health endpoint UP |
| Browser end-to-end workflows | PASS | 3 Playwright tests passed (12.8 seconds) |
| Total | **5 / 5 PASS** | Run 42469334656 |

### Runtime behavior verified

- Platform creates vendor tenant with INACTIVE/PENDING_VERIFICATION state.
- Vendor administrator invitation is emailed, can be resent, and old unaccepted token is rejected.
- Invitation acceptance sets the vendor administrator password.
- Pending vendor can update legal profile and submit KYC document, but a business catalogue API returns 403 before approval.
- Platform Super Admin can download/review vendor KYC evidence.
- Vendor approval is blocked until legal profile and approved evidence exist; successful approval activates the tenant.
- Recipient notification inbox is scoped to authenticated user and supports read state.
- Vendor/customer dashboard APIs reject the wrong account type.
- Representative sales/customer/platform role-to-endpoint cases return the expected authorization status.
- Lead follow-up task create/list/complete works in integration tests.
- Order cancellation requires stock reservation release.
- Discounted quotation → order → invoice totals and per-line values reconcile.
- Frontend vendor profile, KYC upload, audit log and customer portal screens run in Playwright.
- Mailpit receives the public enquiry notification in the browser E2E workflow.

### Code-only or partially verified behavior

- Scheduler timing, multi-instance reminder contention and retry under prolonged SMTP outage were not stress-tested.
- The complete role × endpoint matrix has not been enumerated.
- Not every foreign-tenant ID/filter/download/export path has a negative test.
- Dashboard aggregate behavior is tested, but performance with large tenant datasets is not.
- Vendor KYC is not checked against external GST/company registries.
- SMTP behavior is verified with Mailpit, not a production mail provider.

---

## 7. Remaining gaps, defects and risks

1. **Tenant database isolation:** global email uniqueness remains; composite tenant-aware FKs are not consistently applied. Application-level scoping and selected IDOR tests are not a substitute for an exhaustive security review.
2. **RBAC matrix:** representative vendor/customer/sales/platform checks pass, but every API has not been tested against every standard/custom role.
3. **Authentication abuse controls:** login/reset throttling and progressive lockout are absent. Browser access/refresh tokens remain in localStorage. A security review of token lifetime, XSS exposure and refresh rotation is still required.
4. **KYC/document security:** private storage is configured through `VENDOR_KYC_DIR`; production must mount a persistent private volume. Antivirus scanning, external GST/company verification, document retention and recovery are not implemented.
5. **Notification security/reliability:** outbox payloads contain sensitive message bodies until delivery/dead-letter. Restrict DB/backup access and consider envelope encryption and retention. Production SMTP, bounces, complaints, HTML templates and attachments remain.
6. **Customer management:** customer activity/communication history, multi-address support and cross-vendor identity model remain.
7. **CRM:** lead follow-up tasks are implemented, but full activity timeline, conversion concurrency and reminder worker load tests remain.
8. **Quotation documents:** detailed commercial terms, warranty terms, payment schedules, PDF branding and PDF email attachments remain.
9. **Orders/inventory:** broader status, partial fulfillment, multi-line reservation rollback and transfer failure matrix remains.
10. **Finance:** invoice totals reconcile to accepted snapshots, but statutory GST treatment, HSN/SAC, IRN/e-invoice, credit note, void, partial payment and concurrency need accounting/product acceptance.
11. **Dashboard scale:** `DashboardService` returns backend-calculated metrics, but obtains several tenant-scoped lists and aggregates them in memory. Large tenants need database-level aggregation, pagination/date filters and load testing.
12. **Audit:** authenticated request logging exists; failed login/unauthenticated security events, before/after data diffs, retention and audit export/search remain.
13. **Operations:** production metrics/tracing/alerting, backups/restore, disaster recovery and load/soak tests have not been run.
14. **Test database parity:** backend integration tests run under the H2 test profile; MySQL 8.4 is separately validated for migrations and application startup. A MySQL Testcontainers backend test profile would improve parity.
15. **Production deployment:** no production Vercel/Render secrets, persistent disk, SMTP provider, backup policy or production deployment was accessed or verified.

---

## 8. Prioritized remediation plan

### P0 — before production use

- Complete endpoint-by-endpoint tenant IDOR and role × endpoint authorization matrices.
- Decide tenant-specific customer identity/email uniqueness and add composite tenant-aware constraints where safe.
- Add distributed authentication rate limiting and review browser token storage.
- Obtain accountant approval for GST/tax treatment, invoice numbering and statutory invoice content.
- Configure production SMTP, sender-domain authentication, monitoring and secret rotation.
- Configure persistent private KYC storage, access policy, backup and malware scanning.

### P1 — before Phase 1 product acceptance

- Complete customer address/activity/communication history.
- Add CRM activity timeline and test follow-up scheduler under concurrency/outage.
- Complete quotation PDF terms, warranty, payment schedule and email attachment behavior.
- Complete order state-transition and inventory rollback test matrices.
- Optimize dashboard aggregates in SQL and add date filters/KPI sign-off.
- Add audit failed-login events, before/after changes, retention and search.
- Run a full browser customer journey from enquiry through quotation, order, inventory, invoice and payment.

### P2 — operational maturity

- Add MySQL Testcontainers for backend integration tests.
- Add production observability, alerting and load/soak tests.
- Rehearse backup/restore and disaster recovery.
- Add report catalogue and tenant-scoped sales/inventory/finance exports.
- Add notification preferences, pagination and real-time updates if required.

---

## 9. Confirmed implementation vs runtime vs assumptions

### Confirmed implementation (source code)

- Vendor invitation and restricted pending-onboarding routes.
- Vendor legal profile and private KYC document upload/review.
- Custom tenant-scoped RBAC and privilege allow-list.
- Accepted quotation/order per-line financial snapshots and invoice reconciliation.
- Notification outbox, retry/dead-letter and recipient-scoped inbox.
- Lead follow-up tasks and scheduled reminders.
- Role/account-aware vendor/customer dashboard APIs.
- Authenticated API audit interceptor, persistence, API and vendor audit screen.
- Inventory reservation release guard on order cancellation.

### Verified runtime behavior

- Successful CI run 42469334656.
- 43 backend tests passed.
- 3 Playwright browser tests passed.
- MySQL 8.4 clean migrations V1–V35 passed.
- Spring Boot startup with MySQL 8.4 and Mailpit passed.
- Frontend TypeScript and production build passed.
- Mailpit received public enquiry email.

### Unable to verify

- Production hosting/deployment and secrets.
- Production SMTP deliverability and bounce/complaint behavior.
- Persistent volume and backup/restore for KYC documents.
- Production GST statutory compliance and accountant sign-off.
- Penetration test, DAST, load/stress test and disaster recovery.
- Full role × endpoint and cross-tenant resource matrix.
- Realistic large-tenant dashboard performance.

### Assumptions explicitly not treated as evidence

- Mailpit success does not prove production email deliverability.
- CI startup does not prove production deployment correctness.
- Invoice PDF generation does not prove statutory GST compliance.
- Selected IDOR tests do not prove complete tenant isolation.
- Passing automated tests do not guarantee zero defects.

---

## 10. Final independent re-audit conclusion

The remediation branch now addresses the highest-risk original findings and has passed the current automated validation pipeline: **43 backend tests, 3 Playwright tests, frontend production build, MySQL 8.4 migrations V1–V35 and Spring Boot startup with Mailpit.**

The most important additional workflows—secure vendor invitation and verification, notification inbox, lead follow-ups, backend dashboard summaries and audit review—are now implemented and have representative automated coverage.

**Final disposition: Phase 1 Commercial MVP — approximately 84% complete; core remediation tranche validated; production acceptance remains conditional on P0/P1 remediation and product-owner review.**

This is not a 100% completion or zero-defect certification. No production deployment or merge was performed.
