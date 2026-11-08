# Phase 2 Operations — Analysis and Gap Report

**Audit date:** 2026-11-08  
**Repository:** `rambrij/saumya-solar-energy-product`  
**Base branch:** `main`  
**Base commit audited:** `34b9dac90da5455b032c197579d8dce881ac923c`  
**Feature branch:** `feature/phase-2-operations`  
**Branch starting commit:** `34b9dac90da5455b032c197579d8dce881ac923c`  
**Audit mode:** Stage 1 — read-only application analysis; only this report is added to the feature branch.

---

## 1. Executive summary

**Disposition: Phase 2 Operations is NOT ready for acceptance. Estimated completion: 47% (qualitative, equal-weight average of the nine Phase 2 modules).**

The audited `feature/phase-2-operations` snapshot contains meaningful operational capabilities from the prior Release 3/4 work: basic site-survey capture, order-linked installation scheduling and commissioning, installation teams/checklists, stock ledger and reservations, manual invoice/payment tracking, service tickets with visits and SLA tracking, and warranty activation during commissioning.

However, this is not a complete Phase 2 implementation. The most material gaps are:

1. **Site survey lifecycle is partially implemented.** The feature branch adds tenant-scoped detail/update/start/submit/approve/reject/cancel/history APIs, technical assessment fields, and lifecycle integration tests. The Operations panel exposes lifecycle actions. Remaining gaps include survey-specific photo/attachment capture, a GPS entry UI, explicit approved-quotation/confirmed-order linkage, and a gate from survey approval into installation creation.
2. **Engineer resource management is incomplete.** Users can be grouped into installation teams and assigned to installations/service tickets, but engineer skills, territory, availability, workload, profile lifecycle, and conflict prevention are not implemented.
3. **Installation scheduling lacks scheduling controls.** It supports order linkage, a scheduled timestamp, team/engineer assignment, checklist, rescheduling, completion and commissioning. It does not prevent overlapping engineer/team bookings, provide a calendar view, or support cancellation.
4. **Warranty is activation-and-read-only only.** Warranty records are created when an installation is commissioned and shown in lists/customer portal. Claims, claim review/decision, suspension/cancellation, warranty documents, and expiry alerts are absent.
5. **Inventory is the strongest module but not fully accepted.** Core receipt, issue, reserve, release, adjustment, return, damage, transfer and consumption paths exist, with tenant-scoped balances and an immutable ledger. Remaining gaps include formal transaction reference numbers, a compensating-transaction correction workflow, complete audit evidence, and broader API/security tests.
6. **Payment tracking is substantially implemented as an offline/manual finance workflow.** Partial/full manual payment recording, outstanding-balance checks, receipts, refunds for manual payments, and a signed/idempotent webhook endpoint exist. No concrete live payment-gateway adapter/provider is configured; the adapter is only an interface.
7. **Advanced reporting is largely absent.** The platform tenant dashboard and CSV/XLSX/PDF tenant exports are not the required vendor operational dashboards and reports.
8. **SMS and WhatsApp are not implemented.** The current notification service is synchronous SMTP email only; there are no SMS/WhatsApp adapters, templates, consent/preferences, or provider delivery tracking for those channels.
9. **End-to-end evidence is insufficient.** There are backend integration tests for selected inventory, service, finance, tenant and notification cases, but no browser E2E suite or complete tests for all ten cross-module workflows.

The application has substantial backend and UI building blocks, but **the existence of domain classes and endpoints is not equivalent to acceptance**. Status and percentages below are based on observed connected behavior and test evidence, not file counts.

## 2. Repository and branch details

| Item | Value |
|---|---|
| Repository | `https://github.com/rambrij/saumya-solar-energy-product` |
| Default/base branch | `main` |
| Audited main HEAD | `34b9dac90da5455b032c197579d8dce881ac923c` |
| Existing feature branch audited | `feature/phase-2-operations` |
| Main/base SHA | `34b9dac90da5455b032c197579d8dce881ac923c` |
| Feature branch audited HEAD | `486d9942363a59d8d947143b6e62b832f58aacbb` |
| Branch delta vs main | 23 commits ahead, 0 behind |
| Audited repository tree | 206 entries; 180 files |
| Main technology | Java 21 / Spring Boot 3.5.7, MySQL 8.4 / Flyway, React / TypeScript / Vite |
| Latest migration present | Flyway V27 |
| Audit report | `PHASE-2-OPERATIONS-ANALYSIS-AND-GAP-REPORT.md` |

The target branch already existed when this audit began. GitHub comparison confirmed it is based on the audited `main` SHA and is 23 commits ahead / 0 behind. The branch already contained application-source, migration, test, workflow and an earlier version of this report; those pre-existing changes are not changes made by this audit. This audit updated only this Markdown report. GitHub Actions run `42474204585` executed against branch HEAD `486d9942363a59d8d947143b6e62b832f58aacbb` and passed backend tests, frontend build, MySQL migration validation through V27, and application startup/health verification. No local checkout or manual deployed-business-workflow exercise was performed.

## 3. Audit scope and approach

Reviewed the repository tree and relevant source in:

- Backend controllers, DTOs, domain entities, repositories, services, security configuration and application configuration.
- Frontend vendor portal, customer portal, operations panel, finance/service panel and survey form.
- Flyway migrations V1–V27, including Release 3 operations, Release 4 finance/service, and the Phase 2 survey lifecycle migration.
- Backend integration/security/service tests, notification unit tests, Maven configuration, frontend package scripts and GitHub Actions workflow.
- Project requirements and SRS/HLD documents, including Phase 2 scope and detailed requirements.

Primary requirement references:

- `saumya-solar-energy-solution-requirements.md`: Phase 2 scope at lines 831–843; inventory, installation, service, reporting and communication requirements in sections 8–15.
- `saumya-solar-production-ready-srs-hld.md`: requirements SRVY-001–004, INV-001–008, INS-001–006, FIN-001–007, SVC-001–007, NOT-001–006, RPT-001–006 and AUD-001 onward.

Evidence classification used:

- **Code Verified:** implementation path inspected in source.
- **Test Verified:** a relevant automated test is present and its assertions cover the behavior.
- **Runtime Verified:** the exact audited `main` snapshot was executed in a runtime or CI job.
- **Documentation Only:** stated in project documentation but not supported by implementation evidence.
- **Assumed:** not accepted as implementation evidence.
- **Unable to Verify:** runtime/deployed behavior or an execution result was not available.

Status classification follows the requested labels. Percentages are qualitative estimates of acceptance behavior delivered; they are not test coverage or code-volume metrics.

## 4. Build and test status

| Validation | Result | Evidence / limitation |
|---|---|---|
| Backend Maven compile/test on audited feature-branch HEAD | **Test Verified — PASS** | GitHub Actions run `42474204585`, job `Backend compile and tests`, ran `mvn --batch-mode --no-transfer-progress clean verify` against `486d9942363a59d8d947143b6e62b832f58aacbb`; job conclusion `success`. |
| Frontend production build on audited feature-branch HEAD | **Test Verified — PASS** | Same GitHub Actions run, job `Frontend typecheck and production build`; `npm install` and `npm run build` (`tsc && vite build`) completed successfully. No frontend unit/E2E test script is defined. |
| Clean MySQL migration run on audited feature-branch HEAD | **Runtime Verified — PASS** | Same run, job `Validate Flyway migrations on MySQL 8.4`, applied V1–V27 successfully to a clean MySQL 8.4 service; V27 survey lifecycle migration step passed. This verifies the workflow's SQL migration path, not every production upgrade/data scenario. |
| Backend test inventory | Code Verified | `Release3OperationsIntegrationTest`, `Release4FinanceServiceAndPlatformTest`, `Release12TenantIntegrationTest`, `Release2SecurityTest`, `Release4AccessAndPackageTest`, and `NotificationServiceTest`. |
| Application startup/health smoke | **Runtime Verified — PASS** | Same run, job `Start Spring Boot against MySQL 8.4 and Mailpit`, packaged backend and completed `Start application and verify health`. This is a health smoke, not a full browser or business E2E. |
| Browser E2E | Not Implemented / Unable to Verify | No Playwright/Cypress/Webdriver test suite or frontend E2E script was found in the repository tree/package scripts. |
| CI run evidence | **All 4 jobs PASS** | Run `42474204585` on branch HEAD `486d9942363a59d8d947143b6e62b832f58aacbb`: MySQL V1–V27 migrations, Spring Boot startup/health, frontend production build, and backend compile/tests. |

**Conclusion:** CI provides positive build, backend test, clean migration and health-smoke evidence for the audited branch HEAD. It does not establish complete Phase 2 acceptance: there is no browser E2E suite, and the business workflows still have material functional gaps.

## 5. Overall and module-wise completion

| Phase 2 module | Estimate | Status | Summary |
|---|---:|---|---|
| Site survey | 55% | Partially Implemented | Backend lifecycle APIs, history, technical assessment fields, UI actions and integration tests exist; no survey attachments/photo evidence, no GPS entry UI, no order approval linkage or installation gate. |
| Installation scheduling | 55% | Partially Implemented | Order-linked scheduling, team/engineer, checklist, reschedule, completion and commissioning; no conflicts/calendar/cancel. |
| Engineer assignment | 30% | Partially Implemented | Basic same-tenant employee/team assignment; no skills, territory, availability, workload or robust assignment history. |
| Service tickets | 70% | Partially Implemented | Ticket creation, assignment, visits, attachments, event history and SLA; missing notification and some lifecycle/operational capabilities. |
| Warranty management | 30% | Partially Implemented | Warranty activation on commissioning and customer/vendor listing; no claim lifecycle, documents or expiry alert. |
| Inventory transactions | 85% | Partially Implemented | Most stock movements, reservations, locking, balances and immutable ledger exist; some transaction/audit and test gaps remain. |
| Payment tracking | 80% | Partially Implemented | Manual full/partial tracking, outstanding calculation, receipt, manual refund and webhook idempotency; no connected gateway implementation. |
| Advanced reporting | 15% | Partially Implemented | Platform tenant dashboard/export only; vendor operational reporting absent. |
| SMS and WhatsApp | 0% | Not Implemented | No provider adapters or channel workflows found. |

**Overall Phase 2 estimate: 47%** (mean of the nine module estimates, rounded from 46.7%). The score should be treated as a directional gap-assessment metric, not as a certification of partial production readiness.

## 6. Requirement traceability matrix

| ID | Module | Acceptance criterion | Status | Completion % | Backend evidence | Frontend evidence | Database evidence | Security / RBAC evidence | Test / runtime evidence | Gap | Severity | Recommended action |
|---|---|---|---|---:|---|---|---|---|---|---|---|---|
| SUR-01 | Site survey | Create and list tenant surveys linked to leads | Partially Implemented | 40 | `Controller.survey`, `AppService.survey`, `SiteSurveyRepository` | `main.tsx` SurveyForm; generic surveys tab/list | `SiteSurvey`; survey table in V1 schema | Vendor + `SURVEY_MANAGE`; same-tenant lead/employee checks | No dedicated survey lifecycle integration test identified | Create/list only; no dedicated update/detail API | High | Implement explicit survey resource APIs and full lifecycle tests |
| SUR-02 | Site survey | Schedule, assign, approve/reject/cancel and retain history | Partially Implemented | 15 | `AppService.survey` accepts initial SCHEDULED/COMPLETED/CANCELLED status | Survey creation form only | `scheduled_at`, `assigned_user_id`, `status`, `completed_at` | Initial tenant checks only | Unable to verify runtime | No approval/rejection, reassignment, cancellation transition or audit history | High | Add state machine, action permissions, history and transition tests |
| SUR-04 | Site survey | Record lifecycle history and enforce tenant/permission boundaries | Test Verified (CI) | 70 | `SurveyLifecycleService.record`, `requireSurvey`, `authorize`; `SurveyLifecycleController.history` | `OperationsPanel.tsx` exposes history/action affordances | V27 `site_survey_history` | `SURVEY_MANAGE`, tenant-scoped `findByIdAndTenantId` | `SurveyLifecycleIntegrationTest` covers history, cross-tenant 404 and missing-permission 403; CI passed | History is survey-specific; no common audit event stream and no browser E2E | Medium | Extend platform audit coverage and add Playwright workflow tests |
| SUR-03 | Site survey | Capture roof, shading, load, location, GPS, electrical details, notes, images and findings | Partially Implemented | 20 | `SiteSurvey` fields: propertyType, roofType, usableAreaSqft, shading, sanctionedLoadKw, notes | Form captures subset only | Survey schema lacks GPS/electrical/photo/findings columns | No survey attachment-specific authorization | Code Verified only | No coordinates, orientation, sanctioned supply detail, electricity usage, attachments or technical recommendation | High | Extend survey DTO/schema and private attachment handling |
| INS-01 | Installation | Create from eligible order; customer/site/order linkage | Implemented but Not Runtime Verified | 75 | `OperationsService.createInstallation`, `InstallationJobRepository` | `OperationsPanel` installation tab | `installation_job` in V11/V12 | Tenant-scoped order/team/engineer lookup | Selected operations integration tests; no runtime run for audited SHA | Site survey approval is not a prerequisite and no package snapshot linkage | Medium | Gate creation on accepted survey/order policy and test linkage |
| INS-02 | Installation | Date/time, engineer/team assignment, reschedule and cancellation | Partially Implemented | 50 | `createInstallation`, `reschedule`; no cancel endpoint | Installation form/list; no calendar | `scheduled_at`, `assigned_engineer_id`, `team_id`, `reschedule_reason` | Vendor/Operations Manager checks; engineer access scoped to assigned job | No conflict test identified | No overlapping-booking prevention, cancellation, schedule calendar or reschedule history | High | Add availability/overlap validation, cancellation and calendar APIs/UI |
| INS-03 | Installation | Checklist, evidence, completion, commissioning and customer sign-off | Implemented but Not Runtime Verified | 80 | `checklist`, `updateChecklist`, `completeInstallation`, `commission` | Checklist and commissioning controls in `OperationsPanel.tsx` | Checklist item/template and installation columns in V11/V12 | Tenant + assigned engineer/elevated role checks | Operations tests cover stock/concurrency more than full commissioning path | No image evidence attachment storage; checklist evidence is a URL/text field | Medium | Use protected evidence storage and add full commissioning integration tests |
| ENG-01 | Engineer assignment | Engineer profile, skills, territory, availability and workload | Not Implemented | 0 | No dedicated engineer profile/resource service found | Uses employee lookup | No engineer skill/territory/availability schema | Existing employee tenant scoping only | No tests | Employee records are not an engineer resource-management module | High | Add engineer profile, skill/territory and availability model |
| ENG-02 | Engineer assignment | Assign/reassign/remove to surveys, installations and tickets; prevent conflicts | Partially Implemented | 45 | Survey initial assignedUserId; installation assignedEngineerId/team; ticket assign method | Basic team/engineer selection and service assignment prompt | Team member IDs stored as JSON; assignments stored on job/ticket | Same-tenant employee checks; engineer assigned-job checks for selected actions | No comprehensive duplicate/conflict tests | No assignment history/removal workflow or schedule-aware conflict detection | High | Centralize assignment service and add conflict/history tests |
| SVC-01 | Service tickets | Customer/internal ticket creation and customer/order/install/product/warranty linkage | Partially Implemented | 70 | `FinanceService.createTicket`; `FinanceController` service request APIs | Customer portal service area; `FinanceServicePanel` ticket form | `service_request` in V18; installation/product/customer IDs | Customer profile and tenant-scoped lookups | `Release4FinanceServiceAndPlatformTest` tests customer create and tenant denial | Order linkage not present in ticket entity; warranty linkage is a snapshot/status rather than a claim relation | Medium | Add order/warranty association and linkage tests |
| SVC-02 | Service tickets | Assign, visits, comments/history, resolution, close/reopen and SLA | Partially Implemented | 80 | `assignTicket`, visit lifecycle, `serviceHistory`, SLA policy/scheduler, pause/resume/reopen | `FinanceServicePanel.tsx` | V18–V22 service tables | Service Manager/vendor and assigned engineer access checks | Release4 service lifecycle and tenant isolation test | SRS status names differ; no general comment thread or explicit On Hold workflow | Medium | Align state model to SRS and add comments/status tests |
| SVC-03 | Service tickets | Attachments, evidence, parts and customer notifications | Partially Implemented | 55 | `ServiceAttachmentService`; inventory consumption from visit completion | Upload/download evidence UI | V22 private attachment table; inventory movement service_request_id | Ticket access checked before attachment list/download | Service attachment upload/download and foreign tenant denial test | No trigger-based customer notification; evidence URL path also accepted | High | Add notification outbox and verify protected evidence end to end |
| WAR-01 | Warranty | Create/activate warranty from commissioned installation/order/product | Implemented but Not Runtime Verified | 75 | `OperationsService.commission`, `createWarranty` | Warranty listing and customer warranties tab | `warranty` in V11 | Tenant/customer filtered warranty reads | No dedicated warranty activation test identified | Start/end dates default to current date/product warranty years; no manufacturer certificate verification | Medium | Add warranty activation test and configurable terms/start policy |
| WAR-02 | Warranty | Claims, approval/rejection/resolution, status changes, documents and expiry alerts | Not Implemented | 0 | No claim service/API found | No claims UI | No warranty claim/document/alert tables found | N/A | No tests | Warranty is read-only after activation | High | Implement claims workflow, document store and expiry scheduler |
| INV-01 | Inventory | Receipt, issue, reservation, release, adjustment, return, damage and transfer | Implemented but Not Runtime Verified | 90 | `OperationsService` inventory movement methods and endpoints in `Controller` | Inventory forms and ledger in `OperationsPanel.tsx` | V11/V12 inventory tables and dimensions | Tenant-scoped warehouse/product/order lookups; permissions | `Release3OperationsIntegrationTest` covers receipts, issue, damage, return, transfer/reservation paths | No complete runtime evidence on audited SHA | Medium | Re-run tests and add reversal/edge-case matrix |
| INV-02 | Inventory | Available/reserved/consumed quantities; no negative stock; concurrent reservation safety | Implemented but Not Runtime Verified | 90 | Locked warehouse/balance queries, availability checks, reservation consumption | Balance view shows on-hand/reserved/damaged/available | DB CHECK constraints and version column | Tenant ID included in repository access patterns | Test covers concurrent reservations and foreign-tenant product denial | Need broader concurrent issue/transfer and MySQL concurrency proof | Medium | Add MySQL concurrency tests and validate all movement types |
| INV-03 | Inventory | Immutable transaction history, references and audit | Partially Implemented | 75 | `InventoryTransaction` prevents update/delete; movement actor/reason/time | Immutable ledger view | `inventory_transaction` plus reserved/damaged deltas | Tenant predicates on ledger listing | Receipt ledger assertions present | No dedicated reference number, generic audit correlation ID or compensating transaction API | Medium | Add external transaction reference and reversal workflow |
| PAY-01 | Payment tracking | Manual full/partial payments and outstanding balance | Implemented but Not Runtime Verified | 90 | `FinanceService.recordPayment`, net-paid calculation and invoice state updates | Manual payment form and payments view | `payment`, invoice/payment status columns | `FINANCE_MANAGE`; customer invoice/payment list is own-customer scoped | Service/finance integration test exists; detailed payment partial/full tests not fully established in this audit | No date-filtered payment report or pagination | Medium | Add comprehensive full/partial/overpayment tests and reporting queries |
| PAY-02 | Payment tracking | Receipt, refund/cancellation and history | Partially Implemented | 75 | Receipt PDF; manual refund with permission/reason; refund list | Receipt download/refund controls | `payment_refund`, payment source/status | Tenant-scoped invoice/payment lookup; refund permission | No complete refund lifecycle runtime proof | Gateway refunds explicitly unsupported without provider adapter; no full payment history event log | Medium | Add refund audit/history and provider refund path when a gateway is selected |
| PAY-03 | Payment tracking | Live payment gateway processing | Not Applicable to Phase 2 acceptance unless separately required | 0 | `PaymentGatewayAdapter` is an interface; webhook endpoint supports signed/idempotent callback parsing | No checkout/payment provider UI | Provider/reference fields and webhook event table | HMAC signature verification and unique provider event key | Test verifies duplicate webhook and invalid signature | No concrete provider implementation/configuration; do not represent as live gateway | Low | Keep manual tracking unless product owner explicitly adds gateway scope |
| RPT-01 | Advanced reporting | Vendor operational dashboard: leads, quotations, orders, installation, tickets, stock, revenue and outstanding | Not Implemented | 0 | No vendor aggregate reporting service/API found | Vendor portal has operational panels, not an integrated KPI dashboard | No reporting aggregate/read model | Existing tenant-scoped transactional APIs only | No dashboard aggregation tests | Platform tenant counts are not vendor operational KPIs | Critical | Implement tenant-scoped reporting service, endpoints and dashboard |
| RPT-02 | Advanced reporting | Filters, sorting, pagination and date/tenant scoping | Partially Implemented | 25 | Platform tenant listing is paginated; operational lists are largely unpaged | Client-side list filtering; no vendor report filter center | Transaction tables have some tenant/date indexes | Tenant scope in key service methods | Platform filter/export tests only | No consistent report filters for dates, status, branch, warehouse, employee and product | High | Add allow-listed filters, server pagination and query tests |
| RPT-03 | Advanced reporting | CSV/XLSX/PDF report exports and large async exports | Partially Implemented | 20 | Platform tenant export endpoints only | Platform tenant export buttons | No export-job table | Platform-only export permission | Tests cover platform tenant CSV/XLSX/PDF | No vendor business report export or async export/expiry | High | Build tenant-scoped export job service and format-specific exports |
| NOT-01 | SMS/WhatsApp | SMS and WhatsApp provider adapters and configuration | Not Implemented | 0 | No SMS/WhatsApp adapter or provider config found | No SMS/WhatsApp configuration UI | Notification table is channel-agnostic only for email fields | No tenant provider credential boundary for these channels | No tests | Current `NotificationService` uses JavaMailSender only | Critical | Add provider ports and secure tenant-scoped provider configuration |
| NOT-02 | SMS/WhatsApp | Templates, event triggers, delivery status, retry and deduplication | Not Implemented | 0 | Synchronous email-only delivery; failed email recorded once | No template/consent/preferences UI | V23 stores event, recipient, subject, status and attempt count | No channel consent or dedupe key | `NotificationServiceTest` tests email success/failure only | No async worker, retry schedule, provider IDs, SMS/WhatsApp delivery state or duplicate prevention | Critical | Add outbox, retry worker, templates, consent and idempotency |

## 7. Detailed module findings

### 7.1 Site survey — 55%

**Present:** Survey creation/listing remains in `Controller.survey` / `AppService.survey` and is linked to a same-tenant lead. The branch adds `SurveyLifecycleController` at `/api/v1/surveys` with `GET /{id}`, `GET /{id}/history`, `PATCH /{id}`, and POST actions `/start`, `/submit`, `/approve`, `/reject`, `/cancel`. `SurveyLifecycleService` enforces `SURVEY_MANAGE`, vendor tenant context, same-tenant enabled employee assignment, editable-state rules, required assessment fields before submission, mandatory rejection/cancellation reasons, and status transition history. `SiteSurvey` plus migration `V27__survey_lifecycle_and_technical_assessment.sql` add latitude/longitude, roof orientation, monthly electricity bill, electrical phase, meter type, findings, recommendation, review metadata and `site_survey_history`. `SurveyLifecycleIntegrationTest` covers submission validation, approval, rejection/resubmission, history, tenant denial, permission denial and cancellation after approval. `OperationsPanel.tsx` contains survey detail/edit/submit/approve/reject/cancel/history UI actions.

**Gaps:** The frontend survey form does not expose GPS coordinate entry (the backend accepts latitude/longitude). No survey-specific image/document upload and protected attachment lifecycle exists. Survey creation is lead-linked, not explicitly linked to an approved quotation or confirmed order. The installation creation workflow does not enforce an approved-survey prerequisite or persist an approved-survey relationship. The legacy create endpoint still uses the generic `Input` path; its validation contract is weaker than the dedicated lifecycle update DTO. No browser E2E proves the form-to-transition flow.

### 7.2 Installation scheduling — 55%

**Present:** `OperationsService.createInstallation` creates an installation for an order in MATERIAL_RESERVED or INSTALLATION_SCHEDULED state, validates same-tenant order, enabled engineer and active team, and prevents a second installation for the same tenant/order. It creates checklist items from tenant templates or defaults. Rescheduling stores a reason. Mandatory checklist completion is required before installation completion; commissioning requires customer sign-off and commissioning notes. Serialized products require serial numbers, and commissioning creates warranty rows. Customer installation listing is available.

**Gaps:** There is no overlap check for engineer/team scheduling, no engineer availability model, no calendar/schedule view, no cancellation endpoint, no cancellation reason flow, and no schedule conflict test. A reschedule overwrites the prior reason rather than retaining an event history. Installation evidence is represented by checklist evidence URL/text; a dedicated protected installation-photo/document flow was not found. Installation creation is not explicitly gated by a completed/approved survey.

### 7.3 Engineer assignment — 30%

**Present:** Vendor employee records can be selected for surveys. Installation teams are created and managed, with same-tenant enabled users restricted to selected installer/engineer roles. Installations carry an assigned engineer and team. Service tickets can be assigned/reassigned to enabled same-tenant service-engineer/technician/installer users. Assigned engineers are restricted to assigned installation/ticket operations in inspected service methods.

**Gaps:** There is no dedicated engineer profile with skills, certifications, territory, shift/availability, workload/capacity, leave or service specialization. Team membership is stored as JSON. No assignment history table/API was found for engineer changes, and no conflict-aware assignment engine prevents double booking. Engineer dashboard/work queue is limited to filtering assigned installation/ticket records, not a unified work queue.

### 7.4 Service tickets — 70%

**Present:** `FinanceController` exposes create/list/get, assign, visits, start/complete, SLA pause/resume, customer confirm, close, reopen, visit history and ticket history. `FinanceService` validates customer and tenant linkage, supports priority/category/description and installation/product/serial references, assigns engineers, schedules visits, consumes inventory parts, and records service events. SLA policy and scheduled breach evaluation exist. Private service attachments have upload/list/download service paths and tenant/ticket access checks. A customer can create a ticket and confirm resolution.

**Gaps:** Ticket/order linkage is not present in the inspected service-request entity; warranty status is captured but there is no linked warranty claim. There is no general comment/thread endpoint. The SRS lifecycle includes a broader set of statuses and the code uses VISIT_SCHEDULED/CUSTOMER_CONFIRMED; state names and transitions need formal alignment. Customer notification triggers are absent. The service dashboard's assignment UI relies on prompt-based ID selection and is not a polished work queue. No browser E2E tests validate the complete customer-to-engineer lifecycle.

### 7.5 Warranty management — 30%

**Present:** `OperationsService.commission` derives product quantities from order lines and package components and creates warranty rows. Serialized products require each serial number. Warranty dates/status and customer/order/product/installation links are stored. Vendor and customer read APIs/UI are present.

**Gaps:** There is no warranty claim entity or workflow, no claim approval/rejection/resolution, no suspension/cancellation management, no document/certificate upload, no expiry notification scheduler, and no warranty claim-to-service ticket integration. The terms are generic manufacturer text and do not prove manufacturer coverage or exclusions.

### 7.6 Inventory transactions — 85%

**Present:** Warehouse management, balances and ledger are tenant scoped. Receipt, return, issue, damage, adjustment, transfer, order reservation, release and installation consumption are implemented. Balances distinguish on-hand, reserved and damaged; available is derived. Database checks constrain invalid quantities, and lock-based balance/warehouse reads are used in important concurrent movement paths. The inventory transaction entity rejects mutation and deletion, directing callers toward compensating movements. Service visit parts also decrement stock and create a service-linked transaction. Low-stock reporting exists.

**Gaps:** The required formal transaction reference number is not a first-class field; reason is free text. A complete reversal/compensating transaction API is not evident. There is no common general audit log/correlation ID. Repository queries often load full tenant lists and filter in service memory. Tests cover meaningful stock paths, a concurrent reservation scenario and cross-tenant product rejection, but broader MySQL concurrency, concurrent issue/transfer and all edge cases still need proof.

### 7.7 Payment tracking — 80%

**Present:** Invoices are created from eligible orders; invoice numbers are tenant/fiscal-year sequenced; invoice lines and tax snapshots are stored. Manual payment recording supports partial/full amounts and checks against outstanding balance. Payment status is reflected in invoice/order lifecycle logic. Payment list and PDF receipt are exposed. Manual refund records require a reason and refund permission. The payment webhook endpoint validates a signature and stores provider event IDs with a uniqueness constraint; a test verifies duplicate webhook event handling and invalid signature rejection. Customers can view their own invoices and payments.

**Gaps:** This is payment tracking, not a connected payment gateway. `PaymentGatewayAdapter` has no concrete implementation in the tree; manual refunds are supported, while gateway refunds explicitly fail without a provider refund adapter. No end-to-end checkout/settlement flow or real provider sandbox test is present. The report does not treat a generic webhook endpoint as a live provider integration. Full financial history/audit and reporting filters need further coverage.

### 7.8 Advanced reporting — 15%

**Present:** Platform tenant directory provides search/status/page/size filters and platform-only CSV/XLSX/PDF exports. The corresponding test checks tenant export formats and denies vendor access to platform exports.

**Gaps:** No vendor KPI dashboard or report service was found for sales, revenue, outstanding payments, lead conversion, inventory movement, product sales, installation performance or service tickets. There is no report center with date/status/warehouse/employee/product filters, no tenant report exports, and no asynchronous large-export job/expiry workflow. Client-side list pagination is not a substitute for server-side report pagination.

### 7.9 SMS and WhatsApp notifications — 0%

**Present:** `NotificationService` uses Spring `JavaMailSender` for password-reset, MFA and generic business email. It records PENDING/DELIVERED/FAILED and one attempt in `notification_delivery`. SMTP configuration is environment-driven. `NotificationServiceTest` covers email success/failure.

**Gaps:** No SMS or WhatsApp provider abstraction/implementation, tenant-specific sender/provider settings, template management, trigger subscriptions, customer consent/preferences, channel delivery receipts, provider references, retry worker, deduplication key or notification history UI/API was found. The implementation is **backend-only email**, synchronous; it is not an SMS/WhatsApp implementation and is not a reliable asynchronous notification platform.

## 8. End-to-end workflow validation

The following table evaluates the required workflow across the connected frontend, API, service, persistence, authorization, error handling, notification and test layers. "Partial" means some connected code exists but the full workflow is not proven.

| # | Required workflow | Result | Evidence and missing links |
|---:|---|---|---|
| 1 | Approved quotation/confirmed order → site survey | Partially Implemented | Lead-linked survey create and lifecycle APIs exist; direct approved-quotation/confirmed-order linkage is absent. |
| 2 | Site survey approval → installation scheduling | Not Implemented | Survey approval exists, but installation creation does not consume/validate an approved survey ID or enforce approval as a scheduling gate. |
| 3 | Installation scheduling → engineer assignment | Partially Implemented | Installation input carries engineer and team; same-tenant role validation exists. No separate assignment lifecycle, conflict detection or history. |
| 4 | Inventory reservation → installation consumption | Implemented but Not Runtime Verified | Reservation and installation completion consume reserved stock with transaction ledger updates. Relevant integration/concurrency tests exist; audited SHA was not executed. |
| 5 | Installation completion → warranty activation | Implemented but Not Runtime Verified | Commissioning after checklist and customer sign-off creates product warranties. No dedicated warranty integration test or runtime verification. |
| 6 | Customer issue → service-ticket creation | Partially Implemented | Customer portal can create service request; customer profile and optional installation/product links validated. No order/warranty linkage or notification. |
| 7 | Service ticket → engineer assignment and resolution | Partially Implemented | Assignment, visit scheduling, start/complete, parts consumption, resolution confirmation and closure/reopen exist. No E2E browser run or customer notification. |
| 8 | Invoice → partial/full payment tracking | Partially Implemented | Manual payment records, outstanding checks and receipts exist. No complete end-to-end financial test matrix or live gateway implementation. |
| 9 | Operational activity → dashboard and advanced reports | Not Implemented | Platform tenant dashboard/export exists but no vendor operational KPI/reporting pipeline. |
| 10 | Business events → SMS/WhatsApp notifications | Not Implemented | No SMS/WhatsApp channel implementation. Current email path is synchronous and not an async retry system. |

## 9. Tenant isolation and RBAC findings

### Positive evidence

- `OperationsService` requires vendor account context and tenant ID, then uses tenant-scoped order, warehouse, product, team and installation repository methods.
- `FinanceService` separates vendor finance access from customer self-service and uses tenant-scoped invoice/ticket/payment lookups.
- Engineer access is checked against assigned installation/ticket IDs for selected actions.
- Service attachment operations authorize the ticket before listing/downloading private files.
- `Release3OperationsIntegrationTest` includes foreign-tenant product rejection and concurrent reservation behavior.
- `Release4FinanceServiceAndPlatformTest` includes foreign-tenant service ticket/attachment access denial and platform-only export authorization.
- `SecurityConfig` requires authentication for non-public routes and returns 401/403 through configured handlers.

### Gaps and risks

1. The test set is selective, not an exhaustive role × endpoint × tenant-ID matrix. Cross-tenant tests are missing for many update/delete/transition/export/report operations.
2. Some entities use independent tenant and foreign-key columns with ordinary foreign keys rather than composite tenant-aware foreign keys. Application-level tenant checks are important and must be tested on every write path.
3. The frontend hides tabs based on permissions, but backend enforcement remains the authoritative boundary. A route/menu visibility check alone is not acceptance evidence.
4. Engineer access rules differ by action and need a consolidated permission matrix. For example, team management, installation operations, and service operations use different role/permission combinations.
5. No vendor report endpoints exist yet; when added, they must use the same tenant scope as transactional APIs and must not accept a caller-supplied tenant ID as authority.

**Recommendation:** Add parameterized security tests for Platform Super Admin, Vendor Admin, Operations Manager, Inventory Manager, Service Manager, Service Engineer, Accountant and Customer across all read/write/report/export endpoints. Verify 401/403/404 semantics and tampering via path IDs, body IDs and query filters.

## 10. Security findings

| Finding | Severity | Evidence / impact | Recommended action |
|---|---|---|---|
| Missing full engineer assignment/availability authorization model | High | Assignment checks validate tenant and selected roles, but no skill/territory/capacity policy exists | Add server-side assignment eligibility and conflict policy |
| No SMS/WhatsApp secret/configuration model | High | No providers exist; adding them without secure tenant configuration risks credential leakage | Store provider secrets outside DB/plain API response; tenant-admin-only rotation |
| No general audit log | High | Lead/order/service histories and inventory ledger exist, but there is no common audit stream for sensitive operations | Implement append-only audit event model with redaction/correlation |
| Survey controller lacks DTO validation annotation | Medium | `POST /surveys` accepts generic `Input` without `@Valid` | Introduce dedicated survey request DTO and validation |
| Incomplete cross-tenant negative tests | High | Existing tests cover selected endpoints only | Add systematic IDOR and export filter tests |
| Operational list endpoints return full lists | Medium | Several repository methods return unpaged tenant collections | Add pagination, filter allowlists and query limits |
| Notification failure is synchronous | High | Business service may call SMTP directly; failed delivery is stored but no worker retries | Use transactional outbox and asynchronous retry with backoff |
| Payment gateway is not connected | Low / scope-dependent | Adapter interface only; no concrete provider | Keep manual tracking and document limitation unless gateway is explicitly approved |

No secrets were observed in the inspected configuration; SMTP, JWT, database and payment webhook settings are environment-variable driven. This is a source inspection, not a deployed-secret scan.

## 11. Database and migration findings

- Migrations V11–V14 introduce operations, installation teams/checklists, technician roles, tax snapshots and immutable inventory-ledger dimensions.
- V18 introduces invoices, invoice items, payments, webhook events, refunds, service SLA policies, service requests and visits.
- V21 adds service calendar/reopen-policy fields; V22 adds private service attachments; V23 adds notification delivery; V24 adds platform MFA; V25/V26 add quotation/order financial snapshots.
- Inventory tables include tenant IDs, foreign keys, unique tenant/warehouse/product balance keys, checks and indexes. The inventory transaction entity enforces immutability in application lifecycle hooks.
- Installation team membership is persisted as JSON rather than a normalized team-member relation with constraints.
- No warranty claim, engineer capability/availability, SMS/WhatsApp provider/template/consent, vendor report/export job, or general audit-event schema was found. V27 adds survey lifecycle history only; it is not a platform-wide audit-event stream.
- Several foreign keys reference entity IDs without a composite `(tenant_id, id)` constraint. This means tenant consistency relies on service-layer checks and must be covered by integration tests.
- Migration existence is code evidence only. Clean-database migration success on the audited main SHA is **Unable to Verify**.

## 12. Frontend and backend integration gaps

- The vendor portal exposes surveys, operations and finance/service tabs. The survey form is create-only and uses a generic input payload.
- `OperationsPanel.tsx` provides inventory, order lifecycle, installation, checklist and warranty views. It does not expose a schedule calendar or engineer availability/workload view.
- `FinanceServicePanel.tsx` provides invoice/payment/service/SLA screens. Engineer assignment relies on prompt-based ID entry; this is functional but not a mature assignment UX.
- The customer portal exposes installations, warranties, invoices, payments and service requests. Customer service request creation is present.
- Platform tenant exports are implemented, but they are not the vendor reports defined by RPT-001/RPT-003/RPT-004.
- No SMS/WhatsApp setup, message template, consent or notification history screen is present.
- The frontend package defines a production build only; no frontend unit/E2E test runner or E2E workflow was found.

## 13. Missing or insufficient tests

Priority test gaps:

1. Site survey create/update/assign/complete/approve/reject/cancel, required-field validation, attachments and cross-tenant access.
2. Installation duplicate booking, engineer/team overlap, reschedule history, cancellation, checklist evidence, commissioning and warranty creation.
3. Engineer skill/territory/availability/workload eligibility, assignment/reassignment/removal and work-queue filtering.
4. Service ticket role matrix, all status transitions, comments, ticket-to-warranty/order linkage, customer notification and SLA business-calendar edge cases.
5. Warranty claims, claims decisions, expiry alert and customer/vendor visibility.
6. Inventory negative-stock, reversal, concurrent issue/transfer/reservation, duplicate transaction, service parts and MySQL lock behavior.
7. Payments full/partial/overpayment, duplicate payment callback, refund bounds, receipt content, invoice/payment status reconciliation and customer isolation.
8. Vendor reporting aggregation correctness, date filters, pagination, export authorization and cross-tenant filter tampering.
9. SMS/WhatsApp provider success/failure, retries, dedupe, consent, invalid recipient, secure credential isolation and provider callback processing.
10. Browser E2E for the ten end-to-end workflows and regression coverage for Phase 1 screens.

Existing backend tests provide a useful base but do not demonstrate complete Phase 2 acceptance.

## 14. Existing Phase 1 regression risks

No Phase 1 code was changed in this Stage 1 audit. The branch contains only this report. The following regression risks should be covered before Stage 2 changes:

- Authentication, MFA, refresh/logout, employee role assignment and tenant status enforcement.
- Vendor/customer/lead/quotation/order flows and immutable quotation/order financial snapshots.
- Invoice totals, tax calculations, credit notes, void/refund behavior and payment history.
- Existing inventory locking and immutable ledger behavior.
- Existing service attachment privacy and tenant isolation.
- SMTP email and platform MFA delivery behavior.
- Clean MySQL migration from an empty database through V26 and upgrade from a populated Phase 1 database.
- Frontend role-based tabs/routes and customer self-service portal.

## 15. Assumptions and items unable to verify

- The acceptance baseline is the supplied Phase 2 module list plus the two repository requirements documents. No separate Phase 2 sign-off document was found.
- The HLD/SRS is treated as authoritative for detailed behavior; features not specified there are not silently assumed.
- Live payment gateway integration is not treated as mandatory. Current code supports manual payment tracking and a generic signed webhook path; it does not prove a concrete gateway integration.
- Production provider credentials, live SMTP/SMS/WhatsApp delivery, production database contents, deployed Vercel/Render configuration and live runtime behavior were not accessible during this source audit.
- A local Git checkout, Maven build, frontend build, clean migration execution and live browser E2E run were not performed. Those results are therefore marked Unable to Verify rather than inferred.
- CI runs on `phase-1-commercial-mvp-remediation` are not considered proof for the audited `main` commit.
- Completion percentages are qualitative estimates based on connected requirements and gaps.

## 16. Prioritized implementation plan

### P0 — Critical

1. **Vendor reporting foundation:** tenant-scoped aggregate APIs, vendor KPI dashboard, date/status filters, server pagination and CSV/XLSX/PDF exports.
2. **Notification architecture:** transactional outbox, asynchronous worker, retry/backoff, idempotency/deduplication and provider abstraction. Add SMS and WhatsApp adapters only after provider and tenant credential requirements are agreed.
3. **Survey-to-installation business gate:** formal survey lifecycle, approval and linkage to quotation/order; prevent installation scheduling until required survey approval exists (where business policy requires it).

### P1 — High

4. **Engineer resource management:** engineer profile, skills, certifications, territories, availability, workload, assignment history and conflict-aware scheduling.
5. **Installation scheduling hardening:** calendar view, engineer/team overlap prevention, cancellation, reschedule history, evidence attachments and full lifecycle tests.
6. **Warranty claims:** warranty claim entity, claim status transitions, approval/rejection/resolution, supporting documents and expiry alerts.
7. **Security and audit:** general append-only audit log, tenant-aware DB constraints where practical, systematic role/tenant IDOR tests and report/export isolation tests.
8. **Service workflow completion:** comments, order/warranty linkage, state alignment, customer notifications and polished engineer work queue.

### P2 — Medium

9. **Inventory ledger completeness:** formal reference numbers, compensating movement API, broader concurrency tests and paginated transaction history.
10. **Payment tracking hardening:** comprehensive payment/refund audit/history and full/partial/overpayment regression tests. Keep gateway integration out of scope unless separately approved.
11. **Automated test suite:** browser E2E workflows, clean database migration test and frontend/backend regression suite.

## 17. Final recommendation

**Do not accept Phase 2 Operations in the current state.** The repository has a useful Release 3/4 operational foundation, but the Phase 2 scope is only about 43% complete by the qualitative module assessment. The largest acceptance blockers are vendor reporting, SMS/WhatsApp notifications, full survey lifecycle, engineer resource scheduling and warranty claims.

Proceed to Stage 2 only after review and explicit approval of this gap report. Implementation should be delivered in small, independently testable slices, with tenant/RBAC tests and Phase 1 regression checks included in each slice.

---

## 18. Stage 1 audit-only confirmation

- [x] Confirmed existing `feature/phase-2-operations` is based on latest `main` SHA `34b9dac90da5455b032c197579d8dce881ac923c` (23 commits ahead, 0 behind at audit snapshot).
- [x] Inspected relevant backend, frontend, migration, security, test, workflow and documentation sources on feature-branch HEAD `486d9942363a59d8d947143b6e62b832f58aacbb`.
- [x] Updated only this Phase 2 analysis and gap report; all pre-existing source changes on the branch predate this audit.
- [x] No application source, tests, migrations, API contracts, configuration or frontend code changed by this audit.
- [x] No Phase 2 implementation started as part of this audit task.
- [ ] Waiting for explicit approval: **"Approved. Implement the Phase 2 gaps."**

Stage 1 ends here. Do not begin Stage 2 until the user explicitly approves. The branch contained pre-existing Phase 2 work before this audit; no additional implementation was performed.


---

## 19. Stage 2 — implementation progress (approved 2026-11-08)

The user explicitly approved implementation. The Stage 1 audit figures above remain the baseline snapshot; this section records the subsequent code changes and supersedes the earlier “awaiting approval” recommendation.

**Post-implementation estimated coverage: 70%** (qualitative module estimate; not a claim that all acceptance criteria are satisfied).

| Module | Stage 1 baseline | Post-implementation estimate | Main additions |
|---|---:|---:|---|
| Site survey | 55% | 75% | GPS/electrical fields surfaced in forms; survey lifecycle retained; survey approval can be linked to installation |
| Installation scheduling | 55% | 75% | Two-hour engineer/team conflict checks, future-date validation, cancellation, schedule history, order rollback to MATERIAL_RESERVED |
| Engineer assignment | 30% | 60% | Skills, certifications, territory, availability window and max concurrent workload profile; capacity displayed in UI |
| Service tickets | 70% | 80% | Customer notifications on ticket creation, assignment and resolution |
| Warranty management | 30% | 70% | Customer claim submission, vendor approval/rejection/resolution workflow |
| Inventory transactions | 85% | 85% | Existing ledger/reservation/consumption implementation retained |
| Payment tracking | 80% | 80% | Existing manual payment, receipt, refund and webhook capabilities retained |
| Advanced reporting | 15% | 50% | Tenant-scoped, date-filtered operational KPI summary endpoint and UI |
| SMS / WhatsApp | 0% | 60% | Twilio adapter, opt-in controls, channel delivery records and scheduled retries |

### 19.1 Implementation file and schema inventory

- `backend/src/main/resources/db/migration/V28__engineer_profiles_and_warranty_claims.sql`: engineer profile, warranty claim, installation schedule history and notification channel/idempotency columns.
- `backend/src/main/resources/db/migration/V29__customer_messaging_consent.sql`: customer SMS/WhatsApp consent and persisted notification message body.
- `backend/src/main/resources/db/migration/V30__installation_approved_survey_link.sql`: optional approved-survey FK on installation.
- `backend/src/main/resources/db/migration/V31__service_ticket_comments.sql`: customer-visible and internal service comments.
- `backend/src/main/java/com/saumyasolar/api/Phase2OperationsController.java`: engineer profile, warranty claim, customer messaging-consent and operational report APIs.
- `backend/src/main/java/com/saumyasolar/domain/EngineerProfile.java`, `WarrantyClaim.java`, `InstallationScheduleHistory.java` and their repositories.
- `backend/src/main/java/com/saumyasolar/service/OperationsService.java`: schedule conflict/future-date checks, workload limit check, schedule history and cancellation handling; approved survey linkage is validated when supplied or linked through the quotation.
- `backend/src/main/java/com/saumyasolar/service/NotificationService.java`: consent-gated Twilio SMS/WhatsApp sends, email idempotency and scheduled retry processing for email/SMS/WhatsApp.
- `backend/src/main/java/com/saumyasolar/service/WarrantyExpiryScheduler.java`: daily expiry state transition and 30/7/1-day reminders.
- `backend/src/main/java/com/saumyasolar/api/ServiceCommentController.java`, `ServiceComment.java` and `ServiceCommentRepository.java`: tenant-scoped internal/customer comments.
- `backend/src/main/resources/application.yml`: backend-only Twilio environment-variable bindings.
- `frontend/src/Phase2AdminPanel.tsx`: engineer profile, warranty claim decision and reporting screens.
- `frontend/src/OperationsPanel.tsx`: survey GPS/electrical fields, installation reschedule/cancel/history, engineer/claim/report tabs.
- `frontend/src/main.tsx`: customer warranty-claim submission, SMS/WhatsApp preference controls and service comments.
- `README.md`: provider configuration and operational limitations.

### 19.2 New/extended API surface

| API | Purpose | Access |
|---|---|---|
| `GET/POST /api/v1/engineers` | List/save tenant engineer profile and workload | Vendor operations manager/admin with SURVEY_MANAGE |
| `GET /api/v1/warranty-claims` | List tenant claims | Vendor operations manager/admin/service manager |
| `POST /api/v1/customer/warranty-claims` | Submit claim for customer's own active warranty | Customer, own tenant/profile only |
| `GET /api/v1/customer/warranty-claims` | Customer claim history | Customer, own tenant/profile only |
| `POST /api/v1/warranty-claims/{id}/approve` | Approve claim | Vendor operations role |
| `POST /api/v1/warranty-claims/{id}/reject` | Reject claim | Vendor operations role |
| `POST /api/v1/warranty-claims/{id}/resolve` | Resolve approved claim | Vendor operations role |
| `GET /api/v1/reports/operations?from=&to=` | Tenant-scoped operational KPI snapshot | Vendor operations role |
| `GET /api/v1/reports/operations/export.csv?from=&to=` | CSV export of operational KPI snapshot | Vendor operations role |
| `GET/POST /api/v1/service-requests/{ticketId}/comments` | Customer-visible/internal ticket comments | Customer own ticket or assigned/authorized vendor user |
| `GET/PATCH /api/v1/customer/messaging-consent` | Read/update SMS and WhatsApp opt-in | Customer, own profile only |
| `POST /api/v1/installations/{id}/cancel` | Cancel installation with reason/history | Assigned engineer or authorized operations role, subject to existing installation access checks |
| `GET /api/v1/installations/{id}/schedule-history` | Read tenant-scoped schedule history | Authorized installation participant/operations role |

### 19.3 Verification evidence

GitHub Actions run **42480790865**, for implementation commit `a44d32b5c6ef34f82885b9b90f9e62a67ef90cd5`, completed all four workflow jobs successfully: backend compile/tests, frontend production build, clean MySQL migrations through V31, and Spring Boot startup/health smoke. Earlier run **42480136783** for code commit `5b35e81cae9fc7eeca0b663e2c42e55109bcde92` completed all four workflow jobs successfully:

- Backend compile and tests — PASS.
- Frontend TypeScript check and production build — PASS.
- Flyway migrations on clean MySQL 8.4 through V30 — PASS.
- Spring Boot startup and health smoke against MySQL 8.4/Mailpit — PASS.

Latest verification run: https://github.com/rambrij/saumya-solar-energy-product/actions/runs/42480790865
Earlier full-pass run: https://github.com/rambrij/saumya-solar-energy-product/actions/runs/42480136783

The subsequent commit `cfeb9f0f799423c77e69ee2bf11cd58665235c4b` changed only README documentation; it did not change application code. CI run 42480116054 also passed all four jobs for the immediately preceding application-code snapshot.

### 19.4 Remaining acceptance gaps — do not treat this as full Phase 2 completion

1. **Site survey:** no survey-specific protected image/document upload or customer sign-off workflow; the approved survey relationship remains optional for legacy orders/quotations that have no survey ID.
2. **Installation scheduling:** conflict detection currently uses a fixed two-hour window and is a pre-check rather than a fully serialized reservation/locking algorithm. No calendar UI, team availability calendar, or complete assignment audit is provided. Schedule history is limited to reschedule/cancel events.
3. **Engineer management:** availability is now checked against the two-hour installation slot for creation and rescheduling, and configured concurrent-job capacity is checked for installation assignments. Territory is still not checked against geocoded site locations; skills/certifications remain profile text/JSON rather than normalized/verified credentials. Capacity remains installation-focused, not a unified workload across surveys, installations and service visits. Capacity check is installation-focused and not a unified workload across surveys, installations and service visits.
4. **Service tickets:** customer notifications were added for create/assign/resolve and tenant-scoped customer-visible/internal comments are available. Full notification history UI, event-level preference matrix and service attachment-to-warranty linkage remain incomplete.
5. **Warranty:** claim workflow and expiry scheduler/alerts are present, but warranty suspension/cancellation, claim attachment upload and formal warranty certificate/document lifecycle are not implemented.
6. **Inventory:** transaction reference numbers are now generated for new ledger rows and backfilled for existing rows by V33, with a tenant-scoped unique constraint and append-only entity protection. Reversal/compensation endpoints and stronger concurrent multi-warehouse stress tests remain outstanding.
7. **Reporting:** the new endpoint returns a KPI snapshot and CSV export, not the complete requested report catalogue. Vendor/customer/product/project/engineer detail reports, XLSX/PDF export, pagination, sorting, caching and report performance testing remain outstanding.
8. **SMS/WhatsApp:** Twilio is an optional global provider adapter, not tenant-specific provider/sender configuration. Provider credentials must be configured in deployment secrets. The implementation uses customer consent and E.164 phone validation; it does not include tenant-specific templates, provider delivery webhooks/handset delivery receipts, or an external durable queue/outbox. Provider acceptance is not proof of handset delivery. Initial failed email and Twilio delivery attempts now receive a retry due timestamp, and the retry worker applies bounded exponential backoff (up to five attempts). No live Twilio credentials were available for runtime delivery verification.
9. **Cross-cutting:** no browser-based end-to-end test suite exercises the full ten workflows. The CI smoke verifies startup/health, not end-to-end business operations. Role/tenant negative tests for the new claims, engineer, reports and messaging APIs should be expanded. General audit-event coverage remains incomplete.

**Final Stage 2 status:** implementation is in progress but Phase 2 is **not yet ready for acceptance**. CI proves compilation, existing backend test suite, frontend production build, clean migration execution and application health smoke for the implementation snapshot. It does not prove live SMS/WhatsApp delivery, browser E2E, load/concurrency behaviour, or every acceptance criterion. Continue with the remaining gaps above before calling Phase 2 complete.


### 19.5 Incremental implementation follow-up — 2026-11-08

The following additional changes were made after the initial Stage 2 implementation:

- **Notification retry correctness:** failed initial SMTP and Twilio SMS/WhatsApp attempts now persist `next_attempt_at`, allowing the existing scheduled retry worker to select them. Added a regression assertion that a failed SMTP send receives a future retry timestamp.
- **Warranty expiry scalability:** replaced an unbounded `findAll()` scheduler scan with bounded repository queries for ACTIVE warranties expiring before today or on the 30/7/1-day reminder dates. Added migration V32 with an index on `(status, end_date)`. Updated scheduler unit tests for the date-scoped repository queries.
- **Engineer scheduling validation:** installation creation and rescheduling now enforce the configured engineer availability window for the full two-hour slot and check configured concurrent installation capacity. Conflict checks use null-safe ID comparisons. The two-hour overlap check remains an application pre-check and is not a database-serialized reservation.
- **Inventory compensation:** added `POST /api/v1/inventory/transactions/{id}/reverse`, tenant-scoped original-row lookup, warehouse/balance locking, one-compensation-only enforcement, non-negative balance validation, immutable `COMPENSATION` ledger rows linked to the original transaction, and V34's unique tenant/original-transaction constraint. Compensation is intentionally limited to standalone receipts, returns, issues, damage and adjustments. Reservation/release, installation consumption and paired transfer entries require dedicated workflows and are rejected rather than leaving related reservation/order/paired-transfer state inconsistent. Added an integration test for balance restoration and duplicate-reversal rejection.
- **Scheduling serialization:** creation and rescheduling now acquire pessimistic tenant-scoped locks on the engineer user and active installation team before checking two-hour conflicts. Engineer profile rows are also locked when present. This serializes bookings sharing either resource across application instances using the relational database.
- **Survey evidence:** added private tenant-scoped PDF/JPEG/PNG/WebP upload, list and download endpoints under `/api/v1/surveys/{surveyId}/attachments`. Uploads enforce a 10 MB cap, content-type allowlist, file-signature checks, safe generated storage keys, path traversal protection and tenant-scoped metadata lookups. An integration test verifies upload/download and cross-tenant denial. The complete CI run `42485215305` passed 41 backend tests, frontend production build, clean MySQL migrations through V35 and Spring Boot startup/health. Current storage is local filesystem; production needs durable shared object storage and backup policy.
- **Inventory traceability:** new ledger transactions receive generated `INV-...` references at persistence. Migration V33 backfills existing ledger rows and enforces a tenant-scoped unique reference. Added unit tests for reference generation and preservation.
- **Verification:** Actions run `42484965607` passed backend compilation and 40 tests, frontend production build, clean MySQL 8.4 migrations through V34, and Spring Boot startup/health. Follow-up run `42485033857` validates the final type-whitelist hardening. Passing CI does not replace live provider, multi-instance race, security and browser E2E verification.

**Acceptance remains open.** Remaining material work includes durable shared object storage for survey/service/warranty evidence, installation calendar UI, geospatial territory matching and unified engineer workload, dedicated reservation/consumption/paired-transfer compensation workflows, complete detailed reports and XLSX/PDF export, tenant-specific SMS/WhatsApp provider/template configuration and delivery webhooks, customer survey sign-off if adopted as a business control, and comprehensive cross-role/cross-tenant browser E2E coverage.


### 19.6 Inventory compensation and serialized scheduling — 2026-11-08

- Added a tenant-authorized inventory transaction compensation endpoint. It appends a linked immutable compensation row; it never edits or deletes the original ledger entry.
- Compensation locks the warehouse and balance, rejects a second compensation, and checks resulting on-hand/reserved/damaged values before persistence. Only standalone stock movements are eligible; linked reservation, installation-consumption and transfer rows are deliberately excluded pending dedicated multi-entity reversal logic.
- Added a unique database constraint on `(tenant_id, reversal_of_transaction_id)` to enforce at most one compensating row per original transaction.
- Installation creation and rescheduling now lock the tenant-owned engineer account and active team before evaluating overlapping two-hour slots. Profile lock is also taken when a profile exists. This closes the read-then-write race for shared engineer/team resources, subject to DB transaction/lock behavior.
- Integration coverage verifies a receipt compensation restores balances and duplicate compensation is rejected. CI run `42485215305` passed 41 backend tests, frontend build, clean MySQL migrations through V35 and application startup/health.


### 19.7 Site survey evidence — 2026-11-08

- Added `SiteSurveyAttachment`, tenant-scoped repository, private filesystem service and `SurveyAttachmentController` with upload/list/download endpoints.
- Added V35 to persist survey evidence metadata with tenant, survey and uploader foreign keys, unique storage key, size limit and query index.
- Added integration coverage for PDF evidence upload/download, safe filename normalization and cross-tenant access denial.
- Production caveat: this implementation stores bytes on the backend filesystem, matching the existing service attachment approach. Multi-instance/ephemeral deployments must configure durable shared object storage (for example S3-compatible storage), private access controls and retention/backup before production use.


### 19.8 Warranty claim evidence — 2026-11-08

- Added `WarrantyClaimAttachment`, tenant-scoped repository, private filesystem service and `WarrantyClaimAttachmentController` for upload/list/download at `/api/v1/warranty-claims/{claimId}/attachments`.
- Customer access is restricted to the customer's own claim; vendor access requires the supported operations role and `SURVEY_MANAGE` permission. Metadata and file lookups are tenant/claim scoped.
- Uploads enforce 10 MB, PDF/JPEG/PNG/WebP allowlisting, signature validation, safe filenames and generated storage keys. V36 adds claim/uploader/tenant foreign keys, a unique storage key, size constraint and scope index.
- Integration coverage verifies customer upload/download, vendor listing, and denial to a different customer.
- CI run `42485901046` passed all four jobs: 42 backend tests, frontend build, clean MySQL migrations through V36, and Spring Boot startup/health.
- The same local-filesystem production caveat applies: configure shared durable object storage before multi-instance or ephemeral production deployment.


### 19.9 Shared private object storage and report exports — 2026-11-08

- Added `PrivateObjectStorageService` with local and AWS S3-compatible providers. New survey, service-ticket and warranty-claim evidence writes can be stored in S3 using tenant/category-prefixed object keys; reads remain private and occur only after the owning survey/ticket/claim authorization checks.
- Existing local keys remain readable after provider selection changes. The service does not automatically migrate local files; a separately verified data migration is required before deleting legacy local storage.
- S3 requests use the AWS SDK default credential provider chain and AES-256 server-side encryption. Configuration is environment-only: `PRIVATE_STORAGE_PROVIDER`, `PRIVATE_STORAGE_S3_BUCKET`, `AWS_REGION`, optional `PRIVATE_STORAGE_S3_ENDPOINT`, and `PRIVATE_STORAGE_S3_PATH_STYLE`. No access keys are stored in source or application configuration.
- Added unit tests for local round-trip/delete, traversal rejection, unsupported provider and missing-bucket fail-fast. These tests do not establish connectivity to a live AWS account or S3-compatible endpoint.
- Added deployment documentation for least-privilege bucket permissions, public access blocking, versioning/retention, backup, and migration caveats.
- Added tenant-authorized operational report exports in CSV, XLSX and PDF formats at `/api/v1/reports/operations/export.{csv,xlsx,pdf}`. The React operations report screen now offers all three formats.
- Added upload/list/download controls for site-survey and warranty-claim evidence in the operations UI. Uploads use authenticated multipart requests; download actions use the authenticated endpoints.
- Added integration coverage for report response formats, date-range validation and customer denial, plus local private-storage unit tests.
- GitHub Actions run **42492744832** passed all four jobs on the report/documentation snapshot: **46 backend tests**, frontend typecheck/production build, clean MySQL 8.4 migrations through V36, and Spring Boot startup/health.
- Earlier storage-only CI run **42492400252** passed 45 backend tests and all other jobs. The later report-export integration run is the 46-test verification above.

**Important remaining caveats:** a live S3 bucket/IAM-role upload-download round trip has not been run; S3 deployment is code/configuration ready, not provider-runtime verified. No automatic migration of existing local files is included. Reporting remains a tenant KPI summary export rather than a complete catalogue of paginated vendor/customer/product/project/engineer/inventory/service reports. Twilio configuration remains global rather than tenant-specific and delivery webhooks/templates are outstanding. Full browser E2E and comprehensive role/tenant matrix tests are still outstanding.

**Current status:** Phase 2 remains **not accepted**. The new storage, report export and evidence UI improvements are code- and CI-verified within the limits above; live provider and full business workflow verification remain required.


### 19.10 Storage decision update — local filesystem only (2026-11-08)

The implementation direction was updated to use local file storage rather than S3. The previous S3 adapter and AWS SDK dependency have been removed. `PrivateObjectStorageService` now writes evidence under tenant/category-scoped directories and rejects path separators/traversal in generated filenames. Existing legacy flat storage keys remain readable and deletable; no migration of existing evidence records is needed for that compatibility path.

Docker Compose now mounts persistent named volumes for service-ticket, site-survey and warranty-claim evidence separately. Spring configuration exposes `SERVICE_ATTACHMENT_DIR`, `SURVEY_ATTACHMENT_DIR` and `WARRANTY_ATTACHMENT_DIR`. The README documents local persistent-disk requirements and backup/restore expectations. Tests cover local round trips, tenant/category directory separation, traversal rejection and legacy flat-key compatibility.

**Operational constraint:** local filesystem storage is suitable for a single backend instance with a persistent mounted disk/volume. It is not shared across multiple backend replicas. On any hosting platform, mount durable storage and point all three directory settings to it; ephemeral container storage will lose evidence during redeploy/replacement. Database and all evidence directories must be backed up/restored together.
