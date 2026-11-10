# Saumya Solar Energy Solution - Implementation Gap Analysis

**Audit baseline:** `main` at `347c5cde2739bd117958521424e1d8a208847014`  
**Audit branch:** `audit/implementation-gap-analysis`  
**Repository:** `rambrij/saumya-solar-energy-product`  
**Primary business source:** `saumya-solar-energy-solution-requirements.md`  
**Technical source:** `saumya-solar-production-ready-srs-hld.md`  
**Review date:** 2026-11-09

## Audit method, evidence standard and limitations

The GitHub recursive tree for the audited `main` commit contains **378 tracked paths**. The review cross-referenced the full requirement-document section structure against the backend API/service/security/repository/domain/migration/test layers and frontend route/navigation/panel/API integration surfaces. The report records requirement-level findings, not a filename-presence checklist.

**Important audit qualification:** this is a source-level static audit. A local clone/build/test run was not possible in the audit execution environment (GitHub network clone access was unavailable). CI workflow definitions and checked-in tests were inspected, but no new CI run, browser session, database migration run, penetration test, or live cross-tenant attack test was executed. Therefore, `IMPLEMENTED` means the reviewed source shows a connected implementation path and supporting evidence; it is not a claim that a fresh production-like end-to-end test passed. Where all paths or runtime behavior could not be proven, the status is deliberately `PARTIALLY IMPLEMENTED`.

Status definitions:
- `IMPLEMENTED`: source-level flow is connected across the applicable layers and no material gap was found in the reviewed path.
- `PARTIALLY IMPLEMENTED`: a material part exists, but acceptance criteria, integration, operational behavior, or proof is incomplete.
- `NOT IMPLEMENTED`: no corresponding implementation was found in the repository inventory/reviewed source.
- `INCORRECT IMPLEMENTATION`: actual behavior contradicts a requirement. No standalone code defect was conclusively established at this audit depth.
- `IMPLEMENTED BUT NOT INTEGRATED`: a screen/API exists as a shell but is not wired to a usable workflow.
- `REQUIREMENT/DOCUMENTATION CONFLICT`: documentation contradicts current code or the two documents cannot be reconciled without a product decision.

## 1. Executive Summary

**Atomic traceability rows analyzed: 54.** The IDs below are audit IDs assigned to group the functional and non-functional requirements into testable slices; the source documents do not provide a stable unique ID for every individual requirement.

| Status | Count |
|---|---:|
| Fully implemented | 25 |
| Partially implemented | 21 |
| Not implemented | 6 |
| Incorrect implementation | 0 |
| Implemented but not integrated | 1 |
| Requirement/documentation conflict | 1 |
| **Total** | **54** |

**Source-level completeness across the traceability rows:** 25 / 54 rows (46.3%) are classified fully implemented. This is a requirement-row ratio, not a runtime test pass rate or a claim that the entire product is production-ready.

### Critical blockers and material risks

1. **Tenant isolation is strong in many reviewed service paths but is not yet proven exhaustively.** The SRS calls for defense-in-depth, including cross-tenant negative tests and tenant-aware database relationships. Several services explicitly use tenant-scoped repository methods, but a complete endpoint-by-endpoint guessed-ID test campaign has not been demonstrated.
2. **Authentication rate limiting and password-login lockout have now been added in the Phase 1 branch update.** V52 and focused tests are present, but migration execution and automated test results remain unverified; proxy-aware client IP handling and audit events remain open.
3. **Inventory reservation expiry/reconciliation is explicitly documented as not implemented.** Abandoned reservations require manual release.
4. **CMS/blog is absent** despite being in SRS scope.
5. **In-app notification inbox is absent.** Email/SMS/WhatsApp delivery tracking is not the same capability.
6. **Real hosted payment checkout, settlement reconciliation and provider refunds are not enabled.** The adapter and webhook foundations exist.
7. **GST statutory treatment remains a business/accounting decision.** The code snapshots tax fields but the README explicitly leaves place-of-supply, e-invoice/IRN, reverse charge and statutory numbering unresolved.
8. **Backup/restore and production monitoring/alerting evidence is missing.**
9. **Automated browser-level cross-role E2E is missing.** The guide is manual; CI validates backend/frontend builds/tests and MySQL migrations.
10. **Stale README limitation:** the README says private service evidence upload/download is not implemented, while the current branch contains the controller, service, private storage service and V22 migration.

### Overall assessment

The codebase is materially beyond a basic MVP: CRM, quotation, inventory, installation, finance/service, subscription, analytics, branch, integration and offline-sync foundations are present. However, **do not label the product production-ready** until tenant-isolation tests, authentication hardening, reservation recovery, statutory finance decisions, observability/recovery, notification gaps and browser E2E are closed or formally accepted.

## 2. Requirement Traceability Matrix

| Requirement ID | Module | Requirement | Status | Backend | Frontend | Database | Security | Evidence | Gap |
|---|---|---|---|---|---|---|---|---|---|
| IAM-01 | Identity | Customer registration and login | IMPLEMENTED | AuthLifecycleService; Controller /auth/register,/auth/login | main.tsx register/login | app_user,customer_profile | CUSTOMER role; BCrypt; tenant activation | Controller.login/register; AuthLifecycleService; JwtService | Source path traced; runtime browser flow not executed |
| IAM-02 | Identity | Platform Super Admin MFA by email code | IMPLEMENTED | Controller /auth/mfa/verify; AuthLifecycleService | main.tsx MFA challenge screen | mfa_challenge; notification_delivery | Platform account type; one-time hashed code; expiry/attempt checks | AuthLifecycleService.createMfaChallenge/verifyMfaChallenge; NotificationService.sendMfaCode | Mail delivery depends on SMTP configuration |
| IAM-03 | Identity | JWT validation, refresh rotation, logout/revocation | PARTIALLY IMPLEMENTED | JwtFilter; AuthLifecycleService | authenticatedFetch.ts single-flight refresh; all authenticated binary/API callers routed through helper | refresh_token; app_user.token_version; V53 family_id/replaced_by_token_id | DB user/tenant/accountType/tokenVersion rechecked on each request | JwtService; JwtFilter.doFilterInternal; AuthLifecycleService.rotateRefresh/consumeRefresh/logout; RefreshTokenRepository.findByTokenForUpdate/findByFamilyIdForUpdate; V53 | Family-level replay revocation and concurrent frontend refresh handling added; localStorage token storage and HttpOnly cookie/CSRF migration remain open; refresh replay integration test still needed |
| IAM-04 | Identity | Password reset | IMPLEMENTED | AuthLifecycleService; Controller reset endpoints | main.tsx forgot/reset forms | password_reset_token | Single-use hashed token and expiry | NotificationService.sendPasswordReset; AuthLifecycleService | Requires reachable PUBLIC_BASE_URL and SMTP |
| IAM-05 | Identity | Login throttling, account lockout and authentication endpoint rate limits | PARTIALLY IMPLEMENTED | AuthenticationRateLimitFilter; AuthLifecycleService.authenticate | shared authenticatedFetch handles 429 and session expiry | V52 app_user lockout fields; auth_rate_limit_bucket | Persistent account lockout; DB-backed IP/path rate limiting | AuthenticationRateLimitFilter.requestPath/doFilterInternal; ClientAddressResolver; AuthenticationRateLimitCleanup; V52; ClientAddressResolverTest; AuthenticationRateLimitFilterTest; AuthLifecycleServiceTest | Trusted exact-IP proxy allowlist and scheduled cleanup added; CIDR ranges, per-account/email recovery limits, live ingress verification and rate-limit audit events remain |
| IAM-06 | Authorization | Permission catalogue and server-side role/permission checks | PARTIALLY IMPLEMENTED | Existing coarse permission checks retained; V53 catalogue and DataSeeder employeeRolePermissions expanded | RoleNavigation.tsx and role navigation continue filtering existing permission strings | permission; role; role_permission; user_role; V53 | JWT filter reloads roles and permissions from DB | JwtFilter.doFilterInternal; AppService.requirePermission; DataSeeder.employeeRolePermissions; V53; Release12TenantIntegrationTest | Fine-grained catalogue and employee grants added, but legacy broad permission codes are still enforced by multiple services; endpoint-by-endpoint least-privilege conversion and assigned-resource scopes remain |
| IAM-07 | Authorization | Vendor employee role creation and permission-level customization | PARTIALLY IMPLEMENTED | Controller employee creation uses predefined roles; AuthLifecycleService audits assignment/status changes | RoleNavigation.tsx; users editor | role; permission; role_permission; user_role; V53 | Vendor employee creation rejects platform role escalation; custom role/permission editor not implemented | Controller.createEmployee; AuthLifecycleService.createEmployee/setEmployeeEnabled; DataSeeder.employeeRolePermissions; Release12TenantIntegrationTest | SRS fine-grained predefined grants added and platform role escalation negative test added; custom roles, permission editor, audit tests for every role change and fine-grained backend endpoint enforcement remain |
| TEN-01 | Tenant | Platform tenant provisioning and vendor admin bootstrap | PARTIALLY IMPLEMENTED | TenantAdminService; Controller tenant endpoints | PlatformDashboard in main.tsx | tenant; app_user; role | Platform-only permission guard | TenantAdminService.create/provisioning methods; PlatformDashboard create handler | Lifecycle covers provisioning/activation/suspension, not full Pending→Verified→Active→Suspended→Closed lifecycle |
| TEN-02 | Tenant | Vendor-to-vendor data isolation across APIs | PARTIALLY IMPLEMENTED | AppService tenant-scoped lookups; FinanceService; OperationsService; EnterpriseSaasService | Vendor pages pass authenticated token; no UI-only isolation assumed | Tenant IDs broadly present; composite tenant FKs are not consistently evident in V1 schema | JWT tenant resolved from DB identity; services frequently use findByIdAndTenantId | AppService.vendor/requireCustomer; FinanceService.ensureTicketAccess; OperationsService.order/warehouse; V43 branch FKs | Strong service-layer scoping in reviewed flows; exhaustive endpoint-by-endpoint negative tests not proven |
| TEN-03 | Tenant | Customer ownership isolation | PARTIALLY IMPLEMENTED | CustomerAccountService; FinanceService; PaymentGatewayCheckoutService | Customer portal uses customer endpoints | customer_profile; customer_order; quotation; invoice; service_request | Customer identity checked in reviewed checkout/service paths | PaymentGatewayCheckoutService.authorizeInvoice; CustomerAccountService | Need systematic guessed-ID tests for every customer document/record endpoint |
| TEN-04 | Tenant | Branch and warehouse isolation | IMPLEMENTED | EnterpriseSaasService; OperationsService | EnterpriseSaasPanel; OperationsPanel | branch; branch_user; warehouse; branch_id columns | Composite tenant/branch FKs in V37/V43; service checks | EnterpriseSaasService.createBranch; OperationsService.warehouse; V37/V43 migrations | Branch scope exists; unassigned legacy branch_id records remain NULL by design |
| WEB-01 | Public Web | Public storefront, home, product catalogue and product detail routes | PARTIALLY IMPLEMENTED | Public API /api/v1/public/{storeSlug}/products and package/enquiry routes | PublicHome; PublicSiteHeader; main.tsx public route handling | product; product_category; solar_package | Public catalogue filters active tenant/store and omits internal costs | README Public storefront routes; main.tsx; Controller public mappings | Storefront and catalogue exist; several requirement pages/content modules remain static rather than CMS-driven |
| WEB-02 | Public Web | ROI calculator, testimonials and installation gallery content | PARTIALLY IMPLEMENTED | No complete dynamic data/API flow established | PublicHome.tsx | No corresponding CMS/gallery domain in tree | N/A | PublicHome.tsx; repository tree | Home shell exists; dynamic ROI/testimonial/gallery management is not evidenced |
| CAT-01 | Catalogue | Product/category CRUD, technical attributes and pricing | IMPLEMENTED | Controller; CatalogAdminService; AppService | main.tsx generic CRUD; RecordEditor.tsx | product; product_category; Flyway V1+ | Catalog permissions and tenant checks | CatalogAdminService; AppService.create/list; RecordEditor.tsx | Static source trace indicates connected CRUD path |
| CAT-02 | Catalogue | Configurable solar packages and package items/images | IMPLEMENTED | Controller package endpoints; PackageImageStorageService | package form and PackageImageUpload.tsx | solar_package; package_item; V16 image_url | Tenant and catalogue permission checks | PackageInput; PackageImageStorageService; main.tsx package submit | Configurable attributes/items; external subsidy rules not evidenced |
| CRM-01 | CRM | Customer records and saved addresses | IMPLEMENTED | CustomerAccountController/Service; AppService | CustomerProfilePanel; CustomerAddresses in main.tsx | customer_profile; customer_address; V49 | Customer profile and tenant scoping | CustomerAccountService; CustomerAddresses submit/delete | Address CRUD connected to API |
| CRM-02 | CRM | Enquiry capture and lead pipeline/follow-up/assignment/history | IMPLEMENTED | Controller; AppService; LeadHistoryRepository | main.tsx generic enquiry/lead screens | enquiry; sales_lead; lead_history | Tenant and employee ownership validation | AppService.create/list; requireLead/requireEmployee; V8 | Follow-up reminders/automation not separately evidenced |
| CRM-03 | CRM | Site survey scheduling, technical assessment, history and evidence | IMPLEMENTED | SurveyLifecycleController; SurveyLifecycleService; SurveyAttachmentController | OperationsPanel survey workflows | site_survey; site_survey_history; site_survey_attachment; V27/V35 | Tenant-scoped survey and attachment lookup | SurveyLifecycleService.update/history; ServiceAttachmentService pattern; V27/V35 | Survey management is integrated; validate every attachment edge case in runtime |
| QUO-01 | Quotation | Versioned quotations and immutable issued revisions | IMPLEMENTED | QuotationRevisionService; AppService; Controller | Quotation create/detail flow in main.tsx | quotation; quotation_item; V25/V26 snapshots | Tenant-scoped customer/lead/survey validation | QuotationRevisionService; AppService quotation methods | Revision service and financial snapshots are present |
| QUO-02 | Quotation | Configurable discount thresholds and approval | IMPLEMENTED | ApprovalWorkflowService; AppService | Quotation form/approval screens in main.tsx and EnterpriseSaasPanel | tenant discount policy; approval_workflow; approval_instance; V9/V39 | DISCOUNT_APPROVE and approval permission checks | ApprovalWorkflowService; V9; V39 | Approval workflow exists; business threshold configuration still needs scenario tests |
| QUO-03 | Quotation | Quotation PDF with vendor branding, terms, warranty and email delivery | PARTIALLY IMPLEMENTED | Controller PDF generation and NotificationService | Quotation UI invokes PDF/download/email actions where available | Quotation snapshots and package/vendor data | Tenant authorization on quotation access | Controller quotation PDF/email mappings; NotificationService | PDF flow exists but full document fidelity against every required field/branding and delivery attachment is not demonstrated |
| QUO-04 | Quotation | Customer acceptance/rejection of exact quotation version | IMPLEMENTED | Controller customer decision; AppService/QuotationRevisionService | Customer quotation actions in main.tsx | quotation decision actor/time/version columns; V3 | Customer ownership and version validation | Controller decision endpoints; V3; Release2SecurityTest | Decision audit is represented in schema and tests |
| ORD-01 | Orders | Idempotent accepted-quotation-to-order conversion | IMPLEMENTED | AppService conversion transaction | Vendor/customer quotation workflows | customer_order unique quotation; idempotency_record | Tenant and customer checks | AppService conversion method; IdempotencyRecordRepository; Release2SecurityTest | Idempotency and uniqueness are present |
| ORD-02 | Orders | Configurable order state machine and complete transition history | PARTIALLY IMPLEMENTED | OperationsService.transition/status; order history | OperationsPanel order actions | customer_order; order_status_history | Permission/tenant checks | OperationsService.transition/status/orderHistory | Transition rules exist but fully configurable state definitions and guards are not established |
| INV-01 | Inventory | Warehouse stock ledger, receipt/issue/return/damage/adjustment/transfer | IMPLEMENTED | OperationsService movement/receive/issue/transfer/adjust | OperationsPanel inventory tabs | warehouse; inventory_balance; inventory_transaction | Tenant and warehouse ownership checks | OperationsService.movement/receive/issue/transfer; V11/V12/V14 | Immutable ledger model and reversals are present |
| INV-02 | Inventory | Order stock reservation with concurrency protection | IMPLEMENTED | OperationsService.reserve/release | OperationsPanel reserve action | inventory_reservation; inventory_balance | Tenant/order/warehouse checks | OperationsService.reserve; Release3OperationsIntegrationTest | Concurrency tests exist in backend suite |
| INV-03 | Inventory | Reservation expiry and abandoned-order reconciliation job | NOT IMPLEMENTED | README explicitly says expiry/background reconciliation is not yet implemented | No expiry management UI found | Reservation expiry metadata/job not evidenced | Operational manual release only | README.md Known Release 3 validation limitations; OperationsService.release | Abandoned reservations require manual release/reconciliation |
| INS-01 | Installation | Installation scheduling, engineer/team assignment, checklist and commissioning | IMPLEMENTED | OperationsService create/reschedule/complete/commission; Phase2OperationsController | OperationsPanel installation/checklist/commission flow | installation_job; installation_team; checklist tables; V11/V12 | Tenant and same-tenant engineer/team validation | OperationsService.validateTeamMembers/createInstallation/commission; OperationsPanel | Commissioning and sign-off flow is represented |
| WAR-01 | Warranty | Warranty activation and warranty claims lifecycle | IMPLEMENTED | OperationsService.createWarranty; Phase2OperationsController | OperationsPanel warranty and claims views; customer portal | warranty; warranty_claim; V28 | Tenant/customer ownership and attachment authorization | OperationsService.createWarranty; Phase2OperationsController; Release4FinanceServiceAndPlatformTest | Expiry scheduler and claim attachment functionality are present |
| SVC-01 | Service | Service tickets, visits, assignment, customer confirmation and closure | IMPLEMENTED | FinanceService createTicket/assignTicket/scheduleVisit/startVisit/completeVisit/confirmService/closeTicket | FinanceServicePanel and customer service screens | service_request; service_visit; service_event | Ticket access is tenant/customer scoped | FinanceService.ensureTicketAccess/getTicket; FinanceServicePanel | Main lifecycle is wired; remaining SLA escalation gaps are separate |
| SVC-02 | Service | SLA calendars, pause/resume and breach detection | PARTIALLY IMPLEMENTED | FinanceService SLA calculations/evaluateSlaBreaches; ServiceSlaScheduler | FinanceServicePanel SLA policy controls | service_sla_policy; service_event; V18/V21 | Tenant policy ownership | FinanceService.calculateSlaDue/evaluateSlaBreaches; ServiceSlaScheduler | Breach events persist; automated escalation email/SMS/push policies remain future work |
| SVC-03 | Service | Private tenant-authorized service evidence upload/download | IMPLEMENTED | ServiceAttachmentController; ServiceAttachmentService; PrivateObjectStorageService | FinanceServicePanel upload/download | service_attachment; V22; private storage path | Ticket authorization then tenant/ticket attachment lookup | ServiceAttachmentService.upload/list/download; ServiceAttachmentController | Code implements feature despite stale README limitation statement |
| FIN-01 | Finance | GST-ready invoice generation, tax snapshots and PDF | PARTIALLY IMPLEMENTED | FinanceController; FinanceService | FinanceServicePanel/customer invoice screens | invoice; invoice_item; invoice_sequence; tax breakdown; V18/V20 | Finance permission and tenant checks | FinanceService.createInvoice; FinanceController.invoicePdf | GSTIN/tax components exist; place-of-supply, e-invoice/IRN, reverse charge and legally approved numbering remain open |
| FIN-02 | Finance | Manual payment recording and invoice balance reconciliation | IMPLEMENTED | FinanceService.recordPayment/netPaid | FinanceServicePanel payment form; customer payment view | payment; V18 | Finance permission and tenant checks | FinanceService.recordPayment/netPaid; FinanceServicePanel.paymentCreate | Manual UPI/bank/cash payment tracking is connected |
| FIN-03 | Finance | Real provider checkout and settlement reconciliation | PARTIALLY IMPLEMENTED | PaymentGatewayCheckoutService and provider adapter registry | Customer checkout flow in main.tsx | payment; payment idempotency; V42 | Invoice/customer ownership checks | PaymentGatewayCheckoutService.create; README Known Release 4 limitations | Adapter contract and event processing exist; README says no real hosted checkout/settlement reconciliation/refund provider enabled |
| FIN-04 | Finance | Signed, deduplicated payment webhooks | IMPLEMENTED | PaymentGatewayWebhookService; PaymentGatewayWebhookController | Provider callback is external, not browser UI | payment_webhook_event; payment; V18 | HMAC/signature validation and provider event uniqueness | PaymentGatewayWebhookService.accept; Release4FinanceServiceAndPlatformTest | Webhook idempotency/security tests present |
| FIN-05 | Finance | Refunds and cancellation with provider-side execution | PARTIALLY IMPLEMENTED | FinanceService.refund; payment gateway adapter contract | FinanceServicePanel refund prompt/action | payment_refund; V18/V19 | Refund permission and amount/reason checks | FinanceService.refund; FinanceServicePanel.refund | Manual refund records exist; provider refund execution is not enabled |
| SAAS-01 | SaaS | Subscription plans, tenant assignment/history and entitlement limits | IMPLEMENTED | EnterpriseSaasController/Service; SubscriptionEnforcementFilter; SubscriptionUsageService | EnterpriseSaasPanel vendor/platform sections | subscription_plan; tenant_subscription; assignment history; V45-V48 | PLATFORM_SUPER_ADMIN and SUBSCRIPTION_MANAGE; vendor read-only | EnterpriseSaasService; SubscriptionEnforcementFilter; README subscription APIs | Plan and assignment lifecycle integrated; auto-renew is metadata, not charging |
| RPT-01 | Reporting | Vendor sales/lead/category/product analytics | IMPLEMENTED | VendorAnalyticsController/Service | VendorAnalyticsPanel; RoleNavigation Insights menu | SQL aggregates scoped by tenant; no dedicated report fact tables | Requires VENDOR_ADMIN, REPORT_VIEW and BRANCH_MANAGE | VendorAnalyticsService.scope/breakdowns/trends/rankings; V51; VendorAnalyticsPanel | Insights navigation exists and API is connected; migration V51 grants vendor-admin permissions |
| RPT-02 | Reporting | Platform analytics for tenant resource/subscription usage | PARTIALLY IMPLEMENTED | EnterpriseSaasService platform usage endpoint | PlatformAnalyticsPanel | subscription_usage and tenant resource data | Platform Super Admin permission | PlatformAnalyticsPanel fetches /platform/analytics/tenant-usage | Current screen is tenant usage only, not full platform sales/order/subscription performance dashboard |
| RPT-03 | Reporting | Vendor report exports (PDF/Excel/CSV) and broad operational reports | PARTIALLY IMPLEMENTED | Platform tenant export and operational report APIs exist | Platform tenant export controls; vendor analytics has no export controls evidenced | No separate report/export job store | Export permissions where exposed | Controller tenant export; FinanceController/Operations report endpoints; main.tsx | Platform exports exist; complete vendor PDF/Excel/CSV report centre is not established |
| NOT-01 | Notifications | Email/SMS/WhatsApp notification delivery with consent and retry | PARTIALLY IMPLEMENTED | NotificationService; scheduled retry; Twilio channels | No complete notification centre/preferences for all roles | notification_delivery; V23; customer consent V29 | SMS/WhatsApp only for active customer with opt-in | NotificationService.sendBusinessNotification/sendConsentedChannels/retryFailedDeliveries | Email/SMS/WhatsApp channels exist; synchronous email, provider acceptance vs handset delivery, durable outbox and provider webhooks remain gaps |
| NOT-02 | Notifications | In-app notification inbox and read/unread state | NOT IMPLEMENTED | No in-app notification domain/API established | No in-app notification inbox screen | No notification inbox/read-state table found | N/A | Repository tree: no notification inbox entity/controller; NotificationService persists delivery attempts only | Email/SMS/WhatsApp delivery records are not an in-app inbox |
| CMS-01 | CMS | Blog and CMS for banners, FAQs, testimonials, legal pages and publishing lifecycle | NOT IMPLEMENTED | No CMS/blog controller/service/domain in source inventory | No CMS/blog admin screens or routes | No blog/content tables or migrations in V1-V51 inventory | CMS publishing role permissions not evidenced | Repository tree backend domain/API and frontend/src; migrations V1-V51 | Explicit SRS scope; implementation absent |
| AUD-01 | Audit | Append-only audit trail for all privileged/financial/operational changes and audit search UI | PARTIALLY IMPLEMENTED | AuditEvent and selected service writes; platform audit APIs | EnterpriseSaasPanel audit view | audit_event; append-only triggers; V37/V44 | AUDIT_VIEW and platform authorization | EnterpriseSaasService.audit; V37 append-only triggers; V44 metadata | Storage/UI exists but comprehensive before/after actor/request coverage across all business mutations is not proven |
| INT-01 | Integrations | Provider-neutral integration registry, secrets references and sync jobs | PARTIALLY IMPLEMENTED | IntegrationManagementService; GenericHttpIntegrationAdapter; IntegrationSyncScheduler; SecretProviderRegistry | EnterpriseSaasPanel integration management | integration_connection; integration_sync_job; integration_secret_reference; V40/V44 | Tenant integration permissions and secret-reference access | IntegrationManagementService; GenericHttpIntegrationAdapter; IntegrationSyncScheduler | Framework is present; business-specific accounting/CRM integrations and provider webhooks remain configuration/provider dependent |
| OFF-01 | Offline | Offline business operation queue, conflict resolution and attachment sync | PARTIALLY IMPLEMENTED | OfflineSyncService; OfflineSyncController | OfflineSyncPanel; service worker registration | offline_sync_operation; offline_sync_attachment; V41/V44 | Tenant and actor ownership | OfflineSyncService; OfflineSyncPanel | Sync inbox/conflict model exists; browser service worker and offline UI coverage do not prove all core workflows are safely offline |
| NFR-01 | Reliability | Backup/restore process and tested recovery objectives | NOT IMPLEMENTED | No backup/restore job or runbook implementation found in repository inventory | No operations screen | No backup metadata or recovery tables | Operational access controls not established | SRS 16.2/22; README deployment instructions | Compose volumes and migration CI are not evidence of tested backup restoration |
| NFR-02 | Observability | Production metrics, tracing, alerting and structured operational monitoring | NOT IMPLEMENTED | Health endpoint and logs/config exist; no full metrics/tracing/alert integration established | No monitoring dashboard | No telemetry persistence requirement | Actuator health only is not sufficient | SecurityConfig permits /actuator/health; README startup instructions | Health check exists but production alerting, trace correlation, dashboards and runbooks are not evidenced |
| NFR-03 | Security | HTTPS, secret management, secure upload limits and password hashing | PARTIALLY IMPLEMENTED | BCrypt cost 12; external secret env; upload validators/private storage | File upload UI | Private service attachment storage; environment configuration | Tenant-authorized private downloads | SecurityConfig; PlatformAdminBootstrap; ServiceAttachmentService; application.yml | Good foundations; deployment TLS enforcement and comprehensive secret scanning/rotation policy are external/unproven |
| NFR-04 | Quality | Automated unit, integration, API, security, tenant and end-to-end test suite | PARTIALLY IMPLEMENTED | Numerous service/security/integration tests; CI Maven verify and MySQL migration job | Four frontend test files; no browser E2E suite found | Migration CI runs V1-V51 | Release2/3/4 security and tenant tests | backend/src/test/java; frontend/tests; .github/workflows/release-validation.yml; docs/features/13-end-to-end-testing-guide.md | CI has backend/frontend/migration checks; browser-level cross-role E2E remains a manual guide |
| NFR-05 | Performance | Pagination/filtering and bounded queries across operational lists | PARTIALLY IMPLEMENTED | Tenant admin manually pages after loading all tenants; several service list methods return full collections | Platform directory pagination; vendor table client-side paging/filtering | Indexes present in migrations | Tenant checks remain applied | TenantAdminService.page; main.tsx generic list; V37/V43 indexes | Some screens paginate, but backend pagination is not consistently implemented for all list APIs |
| DOC-01 | Documentation | Implementation documentation accurately reflects current source | REQUIREMENT/DOCUMENTATION CONFLICT | ServiceAttachmentController/ServiceAttachmentService implement private upload/list/download | FinanceServicePanel wires upload/download | V22 service_attachment migration | Ticket tenant checks present | README.md Known Release 4 limitations line 653 vs ServiceAttachmentController and ServiceAttachmentService | README says private service attachment service is not implemented although code and migration now implement it |
| UI-01 | Platform UI | Platform settings/configuration screen wired to platform configuration APIs | IMPLEMENTED BUT NOT INTEGRATED | Platform configuration API surface exists only partially; no settings-specific controller flow confirmed | main.tsx renders static Platform Settings explanatory placeholder | No settings-specific persistence surface established | Platform permissions gate menu | main.tsx Platform page settings branch | Screen is a placeholder and does not expose configuration operations; do not count as completed settings feature |

## 3. Missing Features

### Authentication throttling and password-login lockout

**Requirement ID:** IAM-05  
**Source:** `saumya-solar-energy-solution-requirements.md`, section 18 Security; SRS sections 10.1 and 15.1/15.3.  
**Expected Behavior:** Rate-limit login, reset and sensitive endpoints; protect password authentication against brute force and credential stuffing; apply account/IP/device controls and observable lockout behavior.  
**Technical Design:** SRS requires session security, secure password handling, rate controls and abuse protection.  
**Current Implementation:** BCrypt password hashing and platform MFA challenge attempt limits exist. `SecurityConfig` defines stateless JWT authentication but no general rate-limit filter or login-failure lockout is visible in the reviewed security flow.  
**Gap:** MFA attempt counting only applies after successful password authentication and does not throttle ordinary login attempts.  
**Affected Layers:** Backend, Database, Security, Tests.  
**Required Implementation:** Add distributed rate limiting for login/reset/MFA and sensitive APIs; persist or safely externalize failure windows/lockout state; use generic responses; add abuse telemetry and tests for lockout expiry and distributed requests.  
**Priority:** Critical.

### In-app notification inbox

**Requirement ID:** NOT-02  
**Source:** Business requirements sections 3, 14 and 15; SRS sections 3.1, 10.12 and 11.2/11.3.  
**Expected Behavior:** Users can view their notifications in the application, distinguish read/unread state and open the associated business record.  
**Technical Design:** Notification module includes an in-app channel alongside email/SMS/WhatsApp.  
**Current Implementation:** `NotificationService` persists delivery attempts in `notification_delivery` and sends external channels. No notification inbox API, inbox domain model or read-state UI was found in the repository inventory.  
**Gap:** Delivery attempts are operational records, not user-facing in-app notifications.  
**Affected Layers:** Backend, Frontend, Database, Integration, Tests.  
**Required Implementation:** Add tenant/user-scoped notification records, unread/read state, event-to-route metadata, list/count/mark-read APIs, UI inbox and authorization tests.  
**Priority:** Medium.

### CMS and blog publishing

**Requirement ID:** CMS-01  
**Source:** Business requirements section 16; SRS sections 10.13, 11, 13.8 and 20.  
**Expected Behavior:** Authorized platform/vendor users manage blogs, banners, FAQs, testimonials, contact details, About Us, legal pages and educational content through Draft → Review → Published → Archived.  
**Technical Design:** CMS module with role-protected authoring, publishing lifecycle and public read APIs.  
**Current Implementation:** No CMS/blog controller, service, domain entity, migration or admin screen appears in the 378-path repository tree. Static public content is rendered through `PublicHome.tsx` and related components.  
**Gap:** Dynamic CMS and blog workflow is absent.  
**Affected Layers:** Backend, Frontend, Database, Security, Tests.  
**Required Implementation:** Define content/page/blog models, slug/version/publish lifecycle, tenant/global ownership, media storage, public read APIs, author/reviewer permissions and admin screens.  
**Priority:** High.

### Backup/restore and recovery runbooks

**Requirement ID:** NFR-01  
**Source:** Business requirements section 18 Reliability; SRS sections 16.2 and 22.  
**Expected Behavior:** Documented, automated backups with retention, encryption, restore validation and defined RPO/RTO.  
**Technical Design:** Production deployment and recovery process with rehearsed restore.  
**Current Implementation:** Docker Compose, Flyway migrations and deployment configuration exist; no tested backup/restore implementation or runbook was identified.  
**Gap:** Persistent volumes and migration scripts do not prove recoverability.  
**Affected Layers:** Backend/Operations, Integration, Tests, Documentation.  
**Required Implementation:** Provide database/object-storage backup jobs, encrypted retention, restore scripts, periodic restore rehearsal, RPO/RTO targets and incident runbooks.  
**Priority:** Critical.

### Production observability and alerting

**Requirement ID:** NFR-02  
**Source:** Business requirements section 18 Reliability; SRS sections 16.5 and 22.  
**Expected Behavior:** Metrics, tracing, structured logs, error/latency/database alerts, scheduler and provider-delivery monitoring.  
**Technical Design:** Operational observability and monitoring/alerting across the modular monolith and scheduled work.  
**Current Implementation:** Health endpoint, application logging and scheduled services exist. A complete metrics/tracing/alert configuration and runbook is not evidenced.  
**Gap:** Health-only visibility does not meet production monitoring requirements.  
**Affected Layers:** Backend, Integration, Tests, Operations.  
**Required Implementation:** Add metrics and correlation IDs, tracing, dashboards, alert thresholds, scheduler failure monitoring, provider delivery monitoring and incident runbooks.  
**Priority:** High.

### Inventory reservation expiry and recovery

**Requirement ID:** INV-03  
**Source:** Business requirements section 8; SRS sections 10.8, 12.3, 18 and 20.4.  
**Expected Behavior:** Reservations expire or are reconciled after abandoned/failed orders, releasing stock safely and recording the action.  
**Technical Design:** Reservation expiry/recovery job with transactional ledger release.  
**Current Implementation:** `OperationsService.reserve/release` exists. README explicitly states reservation expiry/background reconciliation is not yet implemented.  
**Gap:** Reservations can remain allocated without automatic expiry/recovery.  
**Affected Layers:** Backend, Database, Frontend/Operations, Tests.  
**Required Implementation:** Add expiry timestamps/policy, scheduled reconciliation, idempotent release ledger events, operational override/retry view and concurrency/restart tests.  
**Priority:** Critical.

## 4. Partially Implemented Features

### Tenant lifecycle and onboarding

- **Requirement:** Pending → Verified → Active → Suspended → Closed with platform-managed onboarding and vendor profile.
- **Existing implementation:** Platform tenant provisioning, admin bootstrap, search, edit, status update and tenant export are present.
- **Working portions:** Tenant creation, platform-only access, admin identity association, activation/suspension and status filtering.
- **Missing portions:** Full verification/closure workflow, onboarding checklist and explicit vendor lifecycle state machine are not demonstrated. Inactive count/filter is retained for compatibility but the README says there is no separate INACTIVE transition.
- **Evidence:** `TenantAdminService.java` (`authorize`, `page`, `update`); `Controller.java`; `main.tsx` (`PlatformDashboard`); README subscription/admin notes.
- **Required changes:** Define lifecycle states/transitions, verification evidence, closure/archive behavior, audit reasons and UI state actions.
- **Priority:** High.

### Vendor employee role customization

- **Requirement:** Vendor Admin creates employees and assigns roles with permission-level control.
- **Existing implementation:** Seeded employee role permission maps and user/role assignment screens/API exist.
- **Working portions:** Predefined roles, employee tenant checks, permission-based navigation and API guards.
- **Missing portions:** A complete tenant-scoped custom role editor and permission assignment workflow is not evidenced.
- **Evidence:** `DataSeeder.employeeRolePermissions`; `Controller.java`; `RecordEditor.tsx`; `RoleNavigation.tsx`.
- **Required changes:** Provide controlled role CRUD, permission matrix, protected-role policy, role change audit and privilege-escalation tests.
- **Priority:** High.

### Customer portal and public content

- **Requirement:** Customer dashboard, products/packages, enquiries, quotations, orders, invoices, payments, installations, warranty, service, documents and notifications.
- **Existing implementation:** Customer portal includes profile, saved addresses, catalogue, enquiry, quotation/order, invoice/payment, installation, warranty claims, service and consent screens.
- **Working portions:** Many customer API flows are connected; customer checkout verifies invoice/order ownership.
- **Missing portions:** Complete customer notification inbox, document centre and a fully dynamic public content/CMS workflow are absent.
- **Evidence:** `main.tsx` (`Customer`, `CustomerAddresses`, customer API calls); `CustomerAccountController.java`; `FinanceService.java`; `PaymentGatewayCheckoutService.java`.
- **Required changes:** Complete the customer menu/API traceability and add negative tests for every customer-owned resource ID.
- **Priority:** High.

### Quotation PDF and email delivery

- **Requirement:** Version-specific PDF with vendor branding, customer details, pricing/taxes, terms, warranty, payment schedule and validity, emailed to customer.
- **Existing implementation:** Quotation revisions and PDF/email endpoints exist.
- **Working portions:** Versioned data and financial snapshots are represented; customer decision is tied to a version.
- **Missing portions:** Evidence does not establish complete PDF fidelity for all mandatory fields or attachment delivery/retry.
- **Evidence:** `QuotationRevisionService.java`; `Controller.java`; `NotificationService.java`; `main.tsx` quotation workflows.
- **Required changes:** Render from immutable revision snapshot; test PDF contents and version identity; attach PDF to email; retry delivery and record result.
- **Priority:** Medium.

### Order state machine

- **Requirement:** Configurable allowed transitions, guards, cancellation paths and full history.
- **Existing implementation:** `OperationsService.transition` validates order transitions and persists `OrderStatusHistory`.
- **Working portions:** Transition service and actor/reason history.
- **Missing portions:** Configurable state definitions/transition rules are not demonstrated; current status behavior is code-driven.
- **Evidence:** `OperationsService.java`; `OrderStatusHistory.java`; `V11__release3_operations.sql`; `OperationsPanel.tsx`.
- **Required changes:** Persist workflow definitions/transition rules or explicitly approve fixed state machine; add transition matrix tests for all roles.
- **Priority:** High.

### SLA escalation

- **Requirement:** SLA clocks, pause/resume, breach and escalation.
- **Existing implementation:** Business calendar policy, SLA calculations, pause/resume and breach events.
- **Working portions:** SLA due calculation and scheduled evaluation.
- **Missing portions:** README states automated SLA email/SMS/push escalation and provider-specific delivery policy remain future work.
- **Evidence:** `FinanceService.java`; `ServiceSlaScheduler.java`; `FinanceServicePanel.tsx`; README Known Release 4 limitations.
- **Required changes:** Escalation policy, notification events, retry/dead-letter monitoring and tests.
- **Priority:** Medium.

### GST and finance compliance

- **Requirement:** GST-ready invoice and statutory tax fields/numbering.
- **Existing implementation:** Invoice numbering, tax snapshots, GSTIN, CGST/SGST/IGST fields and PDF.
- **Working portions:** Manual payments, invoice snapshots, credit notes and payment records.
- **Missing portions:** README calls out unresolved place-of-supply rules, e-invoice/IRN, reverse charge and approved numbering.
- **Evidence:** `FinanceService.java`; `FinanceController.java`; migrations V18/V20; README Known Release 4 limitations.
- **Required changes:** Obtain accounting/legal decision, implement approved tax rules and acceptance tests before statutory use.
- **Priority:** Critical for production invoicing.

### Payment gateway and refunds

- **Requirement:** Hosted checkout, authenticated provider webhooks, reconciliation and controlled refunds.
- **Existing implementation:** Provider-neutral adapter, checkout service, idempotency key, webhook signature verification and event deduplication.
- **Working portions:** Provider callback processing and manual refund records.
- **Missing portions:** No real hosted checkout provider, settlement reconciliation or provider-side refund implementation is enabled.
- **Evidence:** `PaymentGatewayCheckoutService.java`; `PaymentGatewayWebhookService.java`; `PaymentGatewayController.java`; `FinanceService.java`; README Known Release 4 limitations.
- **Required changes:** Select provider, implement create/verify/refund/reconcile, test retries/out-of-order events and never trust browser redirect as payment proof.
- **Priority:** High if online payment is in release scope.

### Reporting and exports

- **Requirement:** Platform and vendor reports, dashboards, PDF/Excel/CSV exports.
- **Existing implementation:** Vendor analytics breakdowns/trends/rankings; platform tenant resource usage; platform tenant exports and operational reports.
- **Working portions:** Vendor analytics API/UI is connected. V51 grants REPORT_VIEW and BRANCH_MANAGE to tenant VENDOR_ADMIN roles.
- **Missing portions:** Platform analytics is primarily tenant usage, not full platform sales/order/subscription performance. Vendor report export centre is not established.
- **Evidence:** `VendorAnalyticsController.java`; `VendorAnalyticsService.java`; `VendorAnalyticsPanel.tsx`; `PlatformAnalyticsPanel.tsx`; V51; `main.tsx`.
- **Required changes:** Define metric catalogue, tenant/platform dashboards, filter/export APIs and export authorization tests.
- **Priority:** Medium.

### Notifications

- **Requirement:** Centralized email, SMS, WhatsApp and in-app notifications with preferences and reliable retry.
- **Existing implementation:** Email sender, Twilio SMS/WhatsApp, consent checks, delivery persistence and scheduled retry.
- **Working portions:** Business event delivery paths and opt-in controls.
- **Missing portions:** No in-app inbox, durable queue/outbox, provider delivery webhooks, and some provider-specific delivery policies remain unimplemented. Email uses synchronous `SimpleMailMessage` rather than an asynchronous event pipeline.
- **Evidence:** `NotificationService.java`; `NotificationDelivery.java`; `CustomerAccountController.java`; V23/V29; README notification limitations.
- **Required changes:** Add outbox/queue, delivery state machine, in-app channel, templates/preferences, dead-letter handling and channel-specific tests.
- **Priority:** Medium.

### Audit trail

- **Requirement:** Who/what/when/tenant, before/after values and request context for privileged/financial/operational actions.
- **Existing implementation:** Append-only `audit_event` with database triggers and a platform audit view; selected services emit events.
- **Working portions:** Append-only database protection and tenant/actor/time metadata.
- **Missing portions:** Consistent event coverage for every sensitive mutation is not proven; not every service writes before/after snapshots.
- **Evidence:** `AuditEvent.java`; `EnterpriseSaasService.audit`; `V37__enterprise_saas_foundation.sql`; `V44__phase3_offline_storage_secrets_audit.sql`; `EnterpriseSaasPanel.tsx`.
- **Required changes:** Publish an audit event catalogue, add cross-module audit coverage, correlation IDs and tests for immutable/no-secret records.
- **Priority:** High.

### Integration and offline sync

- **Requirement:** Provider-neutral adapters, secret references, durable sync jobs, idempotency and conflict handling.
- **Existing implementation:** Integration registry, generic HTTP adapter, scheduler, secret provider abstraction and offline sync inbox/conflict model.
- **Working portions:** Core extension points, job records and tenant-scoped management.
- **Missing portions:** Provider-specific accounting/CRM integrations, durable external queue/outbox, webhook delivery policies and complete offline coverage remain incomplete.
- **Evidence:** `IntegrationManagementService.java`; `GenericHttpIntegrationAdapter.java`; `IntegrationSyncScheduler.java`; `OfflineSyncService.java`; `OfflineSyncPanel.tsx`; V40-V44.
- **Required changes:** Select actual providers, define supported entities/contracts, secure payload validation, retries and reconciliation tests.
- **Priority:** Medium.

### Backend pagination

- **Requirement:** Bounded, sortable, filterable list APIs.
- **Existing implementation:** Platform tenant directory is paginated in UI/API contract; some vendor tables page/filter client-side.
- **Working portions:** Search/status filters and page controls.
- **Missing portions:** `TenantAdminService.page` loads all tenants and filters/pages in memory; many operational list methods return full collections.
- **Evidence:** `TenantAdminService.page`; `main.tsx` generic list table; repository indexes in V37/V43.
- **Required changes:** Move filtering/sorting/page to database queries, set maximum page size and add query-plan/load tests.
- **Priority:** Medium.

## 5. Incorrect Implementations

No standalone source-code behavior was conclusively classified as `INCORRECT IMPLEMENTATION` based on the static evidence reviewed. This is not a clean bill of health: incomplete or unexecuted flows are classified as partial rather than incorrectly implemented.

**Confirmed documentation contradiction:** README claims private service evidence upload/download is not implemented. Current code has:
- `ServiceAttachmentController` at `/api/v1/service-requests/{ticketId}/attachments` with upload/list/download.
- `ServiceAttachmentService.upload/list/download` with ticket authorization and tenant/ticket-scoped attachment lookup.
- `PrivateObjectStorageService` for private object storage.
- V22 migration for `service_attachment`.
- `FinanceServicePanel.tsx` calls the attachment endpoints.

Expected behavior and actual code appear aligned for this capability; the README limitation should be corrected after runtime verification. This is reported as a documentation conflict, not a code defect.

## 6. Backend Gaps

1. Add authentication rate limiting and password-login failure/lockout controls.
2. Add in-app notification API/domain and user-scoped read state.
3. Implement CMS/blog/page publishing APIs and validation.
4. Implement reservation expiry/reconciliation scheduler.
5. Select and implement a real payment provider; add settlement reconciliation and provider refund execution.
6. Close GST statutory rule decisions and implement approved invoice fields/numbering.
7. Add SLA escalation policies and channel delivery.
8. Add a durable notification/integration outbox or queue with retry/dead-letter semantics.
9. Complete audit event coverage for business mutations.
10. Add backend pagination for unbounded list endpoints.
11. Add database-backed backup/restore automation and operational runbooks.
12. Add metrics/tracing/alerting and scheduler failure monitoring.
13. Complete dynamic employee role/permission administration.
14. Add tenant/customer negative tests for every API resource family.
15. Verify cross-tenant relationship integrity for all tenant-owned foreign keys, not only service-layer lookups.

## 7. Frontend Gaps

1. CMS/blog authoring, preview, review and publish screens are absent.
2. In-app notification inbox and unread badge are absent.
3. Platform Settings is a static explanatory placeholder in `main.tsx`, not a working settings workflow.
4. Platform analytics focuses on tenant usage; broader platform sales/order/finance analytics are missing.
5. Vendor report export centre is not evidenced in `VendorAnalyticsPanel.tsx`.
6. Employee role/permission matrix editing is incomplete; the UI selects an existing role name.
7. Some forms use generic editors and prompts rather than dedicated validated workflows (e.g. service assignment/visit completion in `FinanceServicePanel.tsx`).
8. Browser-level E2E tests for role switching and cross-tenant negative scenarios are absent.
9. Customer notification/document centre is incomplete.
10. Public informational content is not CMS-driven.

## 8. Database Gaps

1. The schema contains broad tenant ownership columns and many indexes, but tenant isolation is not proven at the database boundary for every tenant-owned relationship.
2. V1 uses single-column foreign keys for some tenant-bound relationships (for example `product.category_id` references `product_category(id)`); the SRS calls for composite tenant-aware constraints where appropriate. Review all relationships and add composite foreign keys/unique keys where safe.
3. Tenant lifecycle has no demonstrated full verification/closure state machine.
4. No notification inbox/read-state schema exists.
5. No CMS/blog/content schema exists in V1-V51 migration inventory.
6. Reservation expiry/reconciliation persistence/job state is not demonstrated.
7. No backup/restore execution metadata or recovery verification records are present.
8. Existing migrations are extensive (V1-V51) and CI contains a MySQL 8.4 migration validation job; however, this audit did not execute migrations locally.
9. V37/V43 use composite tenant/branch foreign keys for branch-owned relationships, which is a positive defense-in-depth pattern to extend.
10. Audit append-only triggers are present in V37 and subscription assignment history immutability is strengthened by V48.

## 9. Security and Role/Permission Gaps

| Role | JWT identity | Backend authorization | Tenant/customer boundary | Frontend navigation | Audit result |
|---|---|---|---|---|---|
| Platform Super Admin | PLATFORM account type; global tenant null; roles/permissions reloaded by JwtFilter | Platform services check PLATFORM_SUPER_ADMIN plus permission | Platform-wide access is intentional; tenant APIs are guarded | Platform menu includes dashboard, subscriptions, analytics, audit, tenants, settings | Good foundations; rate limiting and complete audit coverage remain |
| Vendor Admin | VENDOR account type and tenant ID | Service checks account type, tenant and permission; analytics requires VENDOR_ADMIN + REPORT_VIEW + BRANCH_MANAGE | Many reviewed lookups use tenant-scoped repository methods | Vendor Insights/Analytics menu is role-gated; V51 grants analytics permissions | Connected path; exhaustive endpoint/negative tests still required |
| Vendor Employee | VENDOR tenant identity; assigned role permissions loaded from DB | Per-service permission checks; some elevated role checks | Employee assignment validation is present in reviewed service paths | Menu filters permissions/roles | Predefined roles exist; custom role/permission administration incomplete |
| Customer | CUSTOMER account type and tenant ID | CustomerAccountService/FinanceService and checkout enforce ownership in reviewed paths | Customer profile/user linkage checked in reviewed checkout/service flows | Customer menu contains own orders, invoices, quotations, enquiries, addresses, service and claims | Need complete guessed-ID tests across all customer APIs |
| Service Engineer / Technician | Vendor user with service/technician roles | Service assignment and ticket/visit checks exist | Tenant/ticket ownership enforced in reviewed service paths | Role menu is permission-driven | Validate assignment-only scope across every list/detail/download endpoint |

### Security flow observations

- `SecurityConfig` is stateless, BCrypt strength 12, CORS allowlist-based and uses explicit 401/403 handlers.
- `JwtFilter` loads the user from the database, rejects disabled users, compares tenant/account type/token version and rejects suspended tenants; authorities are rebuilt from current DB roles and permissions.
- `JwtService` signs a 2-hour JWT; refresh token lifecycle is stored server-side and rotated/revoked by `AuthLifecycleService`.
- `TenantAdminService.authorize` requires PLATFORM + PLATFORM_SUPER_ADMIN + PLATFORM_MANAGE.
- `EnterpriseSaasService.platform/vendor` and `VendorAnalyticsService.scope` add role/permission checks.
- `FinanceService`, `OperationsService` and `AppService` contain tenant-scoped lookups and explicit permission helpers.
- `SecurityConfig` permits `/uploads/**`. Public product/package media may require this, but every file placed there must be intended public content. Private service evidence correctly uses a separate private-storage path and authenticated controller.
- No general API rate limiting or password-login lockout was evidenced.
- A fresh end-to-end test should cover missing/expired/revoked token, changed role, disabled user, suspended tenant, cross-tenant IDs, customer guessed IDs, branch restrictions, private attachments and unauthorized exports.

## 10. Multi-Tenant Isolation Gaps

### Reviewed positive controls

- `JwtFilter.doFilterInternal` verifies token tenant/account type against the current database user and rejects suspended tenants.
- `AppService.vendor` requires VENDOR account type and non-null tenant.
- `AppService.requireCustomer/requireLead/requireEmployee` validate tenant ownership.
- `FinanceService.ensureTicketAccess` and `ServiceAttachmentService` enforce tenant/ticket scoping.
- `OperationsService.order/warehouse/product` use tenant-scoped accessors.
- `EnterpriseSaasService.vendor/platform` distinguish vendor and platform contexts.
- `VendorAnalyticsService.scope` binds SQL tenantId from authenticated Principal, not a caller-supplied tenant ID.
- V37/V43 add composite tenant/branch constraints.

### Remaining proof/gaps

1. Not every controller/service/repository method has been independently exercised with a foreign tenant ID.
2. Some base foreign keys are single-column rather than composite tenant-aware relationships; a malformed cross-tenant association could rely entirely on service validation.
3. Customer ownership negative tests need complete coverage for quotation, order, invoice, payment, installation, warranty, service, attachment and exports.
4. Branch scope is additive and nullable for legacy records; define how NULL branch records appear in branch-filtered reporting and employee permissions.
5. Scheduled jobs and integration/offline workers must be tested for tenant context leakage.
6. Test guessed IDs, list/search/export endpoints, bulk operations, attachment IDs and background processing—not only direct GET detail endpoints.

**Required acceptance gate:** two tenants plus two customers, execute read/write/delete/export/download attempts using foreign IDs for every tenant-owned resource; all unauthorized resources must be denied or consistently hidden as not found, with no side effects.

## 11. End-to-End Workflow Gaps

| Workflow | Classification | Where it breaks / evidence |
|---|---|---|
| Customer registration → enquiry → lead | Partially working | API/UI components and lead history exist; no fresh browser E2E executed |
| Lead → site survey → evidence/history | Partially working | Survey lifecycle and attachment APIs exist; manual/automated E2E not executed |
| Survey → quotation → discount approval → PDF → customer decision | Partially working | Revision/approval/decision code exists; PDF content/attachment fidelity not proven |
| Accepted quotation → one order | Partially working | Idempotent service and unique quotation order link exist; runtime retry/concurrency test not run in this audit |
| Order → reservation → installation → commissioning → warranty | Partially working | Operations services and integration tests exist; reservation expiry/recovery missing |
| Invoice → manual payment | Partially working | Finance services/UI exist; statutory GST decisions remain open |
| Invoice → hosted checkout → webhook → settlement/refund | Broken for real provider use | Adapter/webhook foundation exists but no real hosted provider/settlement/refund implementation enabled |
| Customer service request → assignment → visit → resolution → confirmation | Partially working | FinanceService lifecycle and UI exist; SLA notification escalation remains future work |
| Platform subscription assignment → vendor entitlement enforcement | Partially working | Plan/assignment APIs, UI and enforcement filter exist; no fresh runtime role/expiry test run |
| In-app notification inbox | Not implemented | No inbox model/API/UI |
| CMS publishing | Not implemented | No CMS model/API/UI |
| Cross-tenant denial for every business object | Partially working / unproven | Many service guards exist; complete negative E2E suite absent |
| Backup → restore → application recovery | Not implemented | No restore rehearsal evidence |

## 12. Backend-to-Frontend Integration Gaps

1. **Platform Settings:** `main.tsx` renders a static placeholder; it is not an integrated configuration workflow.
2. **Vendor Analytics:** `VendorAnalyticsPanel.tsx` calls breakdowns/trends/rankings and loads branch/category/product filters. The backend checks VENDOR_ADMIN, REPORT_VIEW and BRANCH_MANAGE; V51 grants these permissions to tenant VENDOR_ADMIN roles. If an existing session lacks them, refresh/re-authenticate after the migration and verify the database role grants.
3. **Platform Analytics:** `PlatformAnalyticsPanel.tsx` calls `/platform/analytics/tenant-usage`; the panel covers tenant resource usage, not the full platform analytics catalogue.
4. **Notifications:** business email/SMS/WhatsApp send paths are connected, but no in-app inbox API/UI exists.
5. **Service attachments:** frontend uses authenticated `/service-requests/{id}/attachments` endpoints and backend private storage; README needs correction.
6. **Customer checkout:** `main.tsx` sends an Idempotency-Key to `/payment-gateways/invoices/{id}/checkout`; actual provider hosted checkout remains disabled until provider configuration/implementation.
7. **Generic CRUD:** `main.tsx` uses generic API paths and `RecordEditor.tsx`; validate every form's request DTO and server validation, especially role assignment and business-specific workflows.
8. **Error handling:** shared API helper parses structured errors and clears session on 401; some panels make direct fetch calls and implement their own error parsing. Standardize response handling and ensure 403 does not silently become an empty lookup.
9. **No automated contract test evidence:** API DTO/TypeScript response shape parity should be checked for all endpoints, not only panels with existing tests.

## 13. Missing Tests

### Missing or insufficiently evidenced test coverage

- Login brute-force/rate-limit and password-reset abuse tests.
- Complete platform/vendor/customer/employee permission matrix API tests.
- Exhaustive tenant isolation tests for read/write/delete/search/export/download/bulk/background paths.
- Branch-scope and NULL-branch visibility tests.
- Customer guessed-ID tests for every owned resource.
- Automated browser E2E for public registration, vendor sales, platform subscription, customer approval, finance, installation and service.
- CMS and in-app notification tests (features absent).
- Reservation expiry/reconciliation and crash-recovery tests.
- Real provider checkout, settlement reconciliation and provider refund tests.
- Notification outbox retry/dead-letter/provider webhook tests.
- Full audit event coverage and append-only behavior tests across all modules.
- Backup restore and RPO/RTO rehearsal tests.
- Performance/load tests for high-volume lists, reports and analytics.
- Accessibility/responsive browser tests for all role workspaces.

### Existing test evidence

Backend test tree includes service, integration, security and migration-related coverage, including `Release2SecurityTest`, `Release3OperationsIntegrationTest`, `Release4AccessAndPackageTest`, `Release4FinanceServiceAndPlatformTest`, `SubscriptionEnforcementFilterTest`, `VendorAnalyticsServiceTest`, `SurveyLifecycleIntegrationTest` and related service tests.

Frontend test tree contains `EnterpriseSaasPanel.test.tsx`, `PlatformAnalyticsPanel.test.tsx`, `customer-portal-copy.test.ts` and `role-navigation.test.ts`.

CI workflow `.github/workflows/release-validation.yml` runs Maven verification, frontend tests/build and MySQL 8.4 migration validation. The manual E2E guide is `docs/features/13-end-to-end-testing-guide.md`. No Playwright/Cypress/browser E2E suite was found in the repository tree.

## 14. Documentation vs Code Conflicts

| Topic | Document statement | Code evidence | Classification / action |
|---|---|---|---|
| Private service evidence | README Known Release 4 limitations says private tenant-authorized upload/download is not implemented | ServiceAttachmentController, ServiceAttachmentService, PrivateObjectStorageService, V22 migration and FinanceServicePanel upload/download | Outdated README; verify live deployment and update documentation |
| Tenant INACTIVE status | README says inactive count/filter is retained but no separate INACTIVE transition exists | TenantAdminService.page accepts INACTIVE filter; status update path toggles ACTIVE/SUSPENDED | Documented limitation; do not present INACTIVE as a lifecycle transition |
| Payment gateway | README says no real hosted checkout, settlement reconciliation or provider refund is enabled | Adapter registry, checkout and webhook services exist | Correctly documented partial capability; distinguish adapter foundation from live provider |
| GST compliance | README says statutory tax decisions remain required | Finance schema/code snapshots GST/tax fields | Open business decision; implementation must not claim statutory compliance |
| SLA escalation | README says breach events persist but automated notification escalation is future work | FinanceService SLA evaluation and NotificationService | Partial implementation; update acceptance criteria |
| CMS | SRS explicitly scopes CMS/blog and publishing; repository has no CMS domain/API/UI | No blog/content code or migrations in inventory | Missing implementation |
| In-app notifications | SRS includes in-app channel; NotificationService stores delivery attempts and sends external channels | No inbox/read-state model/API/UI | Missing implementation |

## 15. Undocumented Existing Features

These are significant code capabilities not fully described in the primary business requirements or detailed SRS baseline. They are not automatically defects:

- Multi-branch and branch membership administration.
- Subscription plans, assignments, history, usage metering and append-only assignment history.
- Tenant branding and custom domains.
- Configurable approval workflows and approval instances.
- Integration connection registry, generic HTTP adapter, sync jobs and secret provider abstraction.
- Offline business synchronization inbox, conflict resolution and attachment staging.
- Tenant storage ledger/metering.
- Engineer profiles, warranty claims and customer messaging consent.
- Platform tenant resource-usage analytics.
- Subscription enforcement filter and expiry scheduler.
- Customer saved addresses and payment gateway adapter foundation.

Maintain these in a separate product capability catalogue and ensure each feature has an owner, permissions, API contract, tests and release designation.

## 16. Priority-Based Gap List

### Critical

1. Add rate limiting and password-login abuse protection.
2. Complete tenant/customer cross-resource isolation negative tests and fix any failures.
3. Implement reservation expiry and abandoned-order reconciliation.
4. Confirm GST statutory rules/numbering before production invoice use.
5. Implement and rehearse database/object-storage backup and restore.
6. Resolve any critical/high findings from a formal security review before production.

### High

1. Implement CMS/blog and public content publishing.
2. Complete tenant onboarding lifecycle and employee role/permission management.
3. Complete order state machine configuration or formally approve fixed transitions.
4. Select/implement payment provider, settlement reconciliation and refund execution if online payment is required.
5. Complete platform/vendor dashboards and authorized exports.
6. Complete audit event coverage.
7. Add production observability, tracing and alerting.
8. Add automated cross-role browser E2E suite.

### Medium

1. In-app notification inbox and notification outbox.
2. SLA escalation notifications.
3. Complete quotation PDF/email attachment fidelity.
4. Complete integration provider implementations and offline sync coverage.
5. Standardize API error handling and request/response contracts.
6. Add server-side pagination for all high-volume list APIs.
7. Improve accessibility/responsive validation and user-facing workflow forms.

### Low

1. Correct stale README service attachment statement.
2. Maintain capability catalogue for undocumented features.
3. Improve API/DTO documentation and examples.
4. Consolidate duplicate/direct frontend fetch helpers.
5. Add additional operational runbooks and support diagnostics.

## 17. Recommended Implementation Sequence

### Phase 1 — Authentication and security blockers

- **Features:** rate limits, password-login lockout, token/session abuse controls, role matrix, security negative tests.
- **Backend impact:** SecurityConfig, authentication lifecycle, filters and permission services.
- **Frontend impact:** lockout/rate-limit feedback and session-expiry behavior.
- **Database impact:** login-attempt/lockout records if persistence is selected.
- **Dependencies:** none; first priority.
- **Expected result:** controlled authentication abuse and tested 401/403/privilege boundaries.

### Phase 2 — Tenant isolation and data integrity

- **Features:** full cross-tenant/customer/branch negative tests; composite tenant foreign keys; background job scoping.
- **Backend impact:** controllers, services, repositories and scheduled jobs.
- **Frontend impact:** ensure filters never imply authorization.
- **Database impact:** composite tenant-aware keys and constraints where applicable.
- **Dependencies:** Phase 1 identity and permission baseline.
- **Expected result:** verified no-cross-tenant read/write/export/download behavior.

### Phase 3 — Core lifecycle and inventory reliability

- **Features:** reservation expiry/reconciliation, order state transition policy, tenant lifecycle completion.
- **Backend impact:** OperationsService, TenantAdminService and schedulers.
- **Frontend impact:** reservation state/release and tenant onboarding actions.
- **Database impact:** expiry metadata, transition policy/history and lifecycle audit.
- **Dependencies:** Phase 2.
- **Expected result:** no permanently abandoned reservations and explicit lifecycle transitions.

### Phase 4 — Finance and provider decisions

- **Features:** approved GST rules, invoice numbering, real payment provider, settlement reconciliation and provider refunds.
- **Backend impact:** FinanceService, PaymentGatewayAdapter/Registry/WebhookService.
- **Frontend impact:** hosted checkout result, payment status and refund visibility.
- **Database impact:** statutory invoice snapshots, reconciliation and provider refund event records.
- **Dependencies:** business/accounting approval and selected payment provider.
- **Expected result:** auditable invoice/payment state aligned with provider and approved tax rules.

### Phase 5 — Complete frontend workflows and CMS

- **Features:** CMS/blog, platform settings, customer document/notification centre, role editor, expanded analytics.
- **Backend impact:** content and notification inbox APIs, role management APIs, platform metrics.
- **Frontend impact:** authoring/publishing, notification inbox, settings and reporting screens.
- **Database impact:** CMS content/version tables and user notification/read-state tables.
- **Dependencies:** permission model and content ownership decisions.
- **Expected result:** every documented role has usable, integrated screens for its required capabilities.

### Phase 6 — Notifications and integrations

- **Features:** durable outbox/queue, dead-letter handling, SLA escalation, provider-specific adapters and webhooks.
- **Backend impact:** NotificationService, integration scheduler and provider adapters.
- **Frontend impact:** notification preferences and delivery status.
- **Database impact:** outbox, inbox, retry and provider event state.
- **Dependencies:** provider selection, consent and security design.
- **Expected result:** traceable, retry-safe multi-channel communication.

### Phase 7 — Observability, backup and recovery

- **Features:** metrics, tracing, alerting, backup/restore and operational runbooks.
- **Backend impact:** instrumentation and scheduler/provider health signals.
- **Frontend impact:** optional operations diagnostics; dashboards for operators.
- **Database impact:** backup metadata only if required; tested backup target/retention.
- **Dependencies:** deployment environment and RPO/RTO approval.
- **Expected result:** measurable service health and rehearsed recovery.

### Phase 8 — Test automation and production readiness

- **Features:** browser E2E, API contract, security, tenant isolation, concurrency, load and restore tests.
- **Backend impact:** test fixtures, integration suites and CI gates.
- **Frontend impact:** Playwright/Cypress role-based scenarios and accessibility tests.
- **Database impact:** MySQL 8.4 migration validation and realistic tenant fixtures.
- **Dependencies:** all prior phases.
- **Expected result:** release gate evidence for the full customer-to-service journey and production deployment.

---

**Final audit conclusion:** The repository contains a substantial connected SaaS implementation, not merely UI scaffolding. The most important remaining risks are not missing CRUD screens alone: they are authentication abuse controls, exhaustive tenant-isolation proof, reservation recovery, statutory finance decisions, missing CMS/in-app notifications, provider completion, operational recovery/observability and automated browser E2E. Treat the status counts as static traceability results for the IDs in this report; rerun the audit after CI and manual E2E evidence is available.


---

## Phase 1 Authentication and Security Update (2026-11-09)

**Working branch:** `audit/implementation-gap-analysis`  
**Implementation commit at report update:** see branch HEAD.  
**Scope:** authentication rate limiting, password lockout, token lifecycle hardening, generic authentication errors, frontend refresh/error behavior and targeted tests.

### Validation of original findings

| Original gap | Validation against current source | Implementation result | Evidence |
|---|---|---|---|
| IAM-05 login throttling and account lockout absent | Confirmed: no general auth limiter or persisted password-failure counter existed in the baseline. MFA had its own challenge attempt limit only. | Partially implemented | `AuthenticationRateLimitFilter`; `AuthLifecycleService.authenticate`; V52 |
| IAM-03 token lifecycle | Existing JWT validation, token-version revocation, refresh rotation and logout existed. Rotation lacked a locked refresh-token read; access-token TTL was hardcoded. | Enhanced; still partial against complete SRS | `JwtService`; `RefreshTokenRepository.findByTokenForUpdate`; `AuthLifecycleService.rotateRefresh` |
| IAM-06 backend authorization | Existing service-level permission checks and DB-reloaded roles/permissions confirmed. | Existing capability retained; matrix alignment and exhaustive negative tests remain partial | `AppService.requirePermission`; `JwtFilter.doFilterInternal`; `SecurityConfig` |
| TEN-02/TEN-03 tenant/customer boundaries | Existing identity-derived tenant and numerous tenant-scoped repository lookups confirmed; prior audit already classified exhaustive proof as partial. | No broad authorization rewrite; further endpoint-by-endpoint negative tests remain required | `JwtFilter.doFilterInternal`; `AppService`; `Release2SecurityTest` |

### Changes made

- Added persisted consecutive failed-login count, lockout expiry and last successful login timestamp to `app_user`.
- Added transactional, pessimistically locked case-insensitive user lookup before password verification to serialize concurrent failure increments.
- Added configurable lockout threshold and duration. Successful login and successful password reset clear the lockout state.
- Added a MySQL-backed rate-limit bucket keyed by a SHA-256 digest of endpoint path and remote address. This is shared across application instances using the same database.
- Applied rate limits to login, refresh, forgot-password, reset-password and MFA verification POST endpoints.
- Added consistent HTTP 429 response with `Retry-After` and a non-sensitive response body.
- Added a locked refresh-token lookup and a single transaction for consume-and-rotate. A revoked-token replay revokes all still-active refresh tokens for that user.
- Made access-token seconds and refresh-token days configurable; the access-token default is now 900 seconds.
- Sanitized 401 and 403 API exception details.
- Added frontend access-token refresh on 401 for calls using the shared JSON API helper. The rotated refresh token is persisted and used for subsequent calls and logout. Added clearer 403 and 429 messages.
- Added unit tests for login lockout, expiry recovery, disabled accounts and authentication rate-limit behavior.

### Requirements traceability

| Requirement | Source document and section | Current implementation / change | Gap remaining | Test coverage |
|---|---|---|---|---|
| Login rate limiting | Business requirements §18 Security; SRS §15.3 | DB-backed filter for `/auth/login` | Proxy address configuration and runtime verification | `AuthenticationRateLimitFilterTest` (added, not run) |
| Refresh rate limiting | SRS §15.3; §13.2 Authentication APIs | Same filter for `/auth/refresh` | Same deployment/runtime verification | Filter test covers threshold mechanism, not each route |
| Password-reset request/confirmation throttling | Business requirements §18; SRS §15.3 | Recovery limit for forgot/reset endpoints | Per-route policy and email-based abuse controls could be refined | Filter test covers threshold mechanism |
| MFA verification throttling | SRS §9 and §15.1 | MFA-specific per-IP limit; existing per-challenge attempt limit retained | Concurrent challenge-attempt test and resend policy | Existing MFA logic; no new concurrent MFA test |
| Failed password attempts and lockout | SRS IAM-006; SRS §15.1 | DB fields + transactional locked account lookup; configurable threshold/duration | No progressive backoff or dedicated audit event yet | `AuthLifecycleServiceTest` (added, not run) |
| Counter reset on success/expiry | SRS IAM-006 | Successful login and expiry path reset count; password reset clears lockout | Runtime verification | `AuthLifecycleServiceTest` |
| Disabled user/tenant on refresh | SRS §15.1; §8.2 | Existing `consumeRefresh` checks user enabled and active tenant | Dedicated automated cases not added | Existing source logic; additional integration tests needed |
| Access-token expiry/revocation | SRS §15.1 | Configurable 15-minute default; JWT filter reloads DB user/tenant/roles and token version | HttpOnly cookie migration not performed | Existing tests/source; fresh runtime test not run |
| Refresh rotation/replay | SRS IAM-005; SRS §20.7 | Locked row read, revoke-before-issue in one transaction; replay revokes remaining user sessions | Session-family identifier not modelled; current policy revokes all user sessions | No new concurrency/replay integration test executed |
| Logout/session revocation | SRS IAM-001/IAM-005 | Existing logout revokes refresh sessions and increments token version | Logout remains user-wide, not one device only | Existing source; runtime test not run |
| Backend permission boundaries | SRS §9.1–9.3, §15.2 | Existing backend permission checks preserved | Current permission catalogue is coarser/different from SRS catalogue; role matrix must be reconciled before changing production grants | Existing `Release2SecurityTest`; broader 401/403 tests remain |
| Tenant isolation | Business requirements §13; SRS §8, §20.1 | Identity-derived tenant and existing tenant-scoped service checks retained | Complete guessed-ID negative tests across every resource are still needed | Existing cross-tenant category test only |
| Frontend session expiry/429/403 | SRS §11, §15.3 | Shared API helper handles 401 refresh, invalid session, 403 and 429 | Several direct binary upload/download and bespoke fetch calls bypass shared helper; localStorage remains | No frontend test added |
| Authentication security audit | SRS IAM-007; SRS §15.6 | Existing audit event infrastructure identified | Login success/failure/lockout events are not yet wired into audit persistence | Missing |

### Existing capabilities reused

- BCrypt work factor 12 from `SecurityConfig`.
- Existing `JwtService`, `JwtFilter`, `Principal`, `token_version` and DB reload of permissions.
- Existing `RefreshToken` entity/table and `RefreshTokenRepository`.
- Existing `AuthLifecycleService` for reset, MFA, tenant-active and session lifecycle.
- Existing Flyway migration mechanism; no manual schema changes required.
- Existing shared frontend `api` helper and session shape.

### Files created

- `backend/src/main/java/com/saumyasolar/security/AuthenticationRateLimitFilter.java`
- `backend/src/main/resources/db/migration/V52__authentication_lockout_and_rate_limit.sql`
- `backend/src/test/java/com/saumyasolar/service/AuthLifecycleServiceTest.java`
- `backend/src/test/java/com/saumyasolar/security/AuthenticationRateLimitFilterTest.java`
- `PHASE_1_AUTHENTICATION_SECURITY_IMPLEMENTATION.md`

### Files modified

- `backend/src/main/java/com/saumyasolar/domain/AppUser.java`
- `backend/src/main/java/com/saumyasolar/repo/AppUserRepository.java`
- `backend/src/main/java/com/saumyasolar/repo/RefreshTokenRepository.java`
- `backend/src/main/java/com/saumyasolar/security/JwtService.java`
- `backend/src/main/java/com/saumyasolar/security/SecurityConfig.java`
- `backend/src/main/java/com/saumyasolar/service/AuthLifecycleService.java`
- `backend/src/main/java/com/saumyasolar/api/Controller.java`
- `backend/src/main/java/com/saumyasolar/api/ApiExceptionHandler.java`
- `backend/src/main/resources/application.yml`
- `frontend/src/main.tsx`
- `IMPLEMENTATION_GAP_ANALYSIS.md`

### Validation status and limitations

The GitHub repository connector was used to inspect and update the branch. It does not provide a local shell/build runner in this execution, and no CI check run was available for the latest commits. Therefore:
- Maven compilation and backend tests: **not run**.
- Frontend install, TypeScript build and Vitest: **not run**.
- MySQL Flyway migration execution: **not run**.
- Browser E2E, cross-tenant attack tests and load tests: **not run**.

Do not interpret added tests as passing tests. The Phase 1 scope is **partially implemented, not complete** until the commands in the implementation report pass in CI or a local environment.

### Additional residual risks

1. The rate limiter uses `HttpServletRequest.getRemoteAddr()`. If deployed behind a reverse proxy/load balancer that presents a shared source address, configure the trusted ingress to supply a verified client address and implement a trusted-proxy-only resolver. Do not blindly trust client-supplied `X-Forwarded-For`.
2. The rate-limit bucket table needs a scheduled retention/cleanup task; old buckets are not currently purged.
3. Authentication success/failure/lockout audit events are not yet persisted.
4. Existing JWTs remain stored in browser localStorage. A secure HttpOnly/Secure/SameSite cookie migration requires coordinated CSRF, CORS and frontend/backend contract work and was not silently introduced.
5. Some direct binary API calls bypass the shared frontend refresh helper.
6. Refresh replay currently revokes all active refresh sessions for the user, not a separately modelled token family.
7. The SRS permission catalogue and current application permission catalogue differ materially. Do not automatically grant new fine-grained permissions without a reviewed migration and role-by-role access analysis.
8. This implementation does not prove every protected endpoint returns the required 401/403 response under all role and tenant combinations.



---

## Phase 1 follow-up validation and hardening (2026-11-09)

### Additional source changes

- **Refresh token families:** V53 adds `family_id`, `replaced_by_token_id`, and `created_at` to `refresh_token`; legacy rows are isolated into one-token families. Rotation preserves the family and links the replaced token. Replay revokes only the affected family (legacy null-family fallback revokes all user refresh sessions).
- **Authentication audit events:** the existing append-only `audit_event` table is reused. Login success/failure/lockout, refresh rotation/replay, logout, password-reset request/completion, MFA challenge delivery/verification, employee role assignment and employee enable/disable events are written through `AuthenticationAuditService`. Audit persistence is part of the calling transaction; failures are no longer silently swallowed.
- **Trusted proxy handling:** `ClientAddressResolver` trusts `X-Forwarded-For` only when the immediate remote address is explicitly allowlisted. Configure exact proxy IPs through `TRUSTED_PROXY_IPS`; arbitrary client-supplied forwarded headers are ignored.
- **Rate-limit retention:** `AuthenticationRateLimitCleanup` deletes buckets older than two days using the existing scheduler. Configure `AUTH_RATE_LIMIT_CLEANUP_CRON`.
- **Frontend refresh coverage:** new `frontend/src/authenticatedFetch.ts` centralizes refresh-aware requests, preserves request headers and binary/FormData bodies, shares concurrent refresh attempts to avoid token replay races, and dispatches a session-expired event. Authenticated API calls, uploads, downloads, offline sync, exports and analytics panels now use it. Remaining `fetch()` calls in `PublicHome.tsx` are public unauthenticated requests; the other two in the helper are the protected request itself and the refresh call.
- **Role grants:** V53 seeds the SRS fine-grained permission catalogue and predefined employee role grants. `DataSeeder.employeeRolePermissions()` also provisions those grants for tenants created after migration. Existing coarse grants are retained for compatibility and remain enforced by legacy service checks; this is not yet least-privilege completion.
- **Negative tests:** added refresh-family replay, trusted-proxy spoof rejection, employee platform-role escalation, missing/malformed JWT and audit-event persistence tests; added frontend refresh, concurrent refresh and invalid-session tests.

### Updated status

| Requirement | Current result | Evidence | Remaining limitation |
|---|---|---|---|
| Authentication audit persistence | Implemented in source; test added | `AuthenticationAuditService`; `AuthLifecycleService.auditAuth`; `AuthenticationAuditServiceTest`; existing append-only `audit_event` schema | CI result for latest commit pending; failed MFA/refresh denial and all security-event categories still need broader coverage |
| Refresh family replay | Implemented in source; focused unit test added | `RefreshToken.familyId`; `replacedByTokenId`; `AuthLifecycleService.consumeRefresh/rotateRefresh`; V53; `AuthLifecycleServiceTest` | No DB-backed concurrent replay integration test yet |
| Trusted proxy resolution | Implemented in source; unit tests added | `ClientAddressResolver.resolve`; `TRUSTED_PROXY_IPS`; `ClientAddressResolverTest` | Exact IP allowlist only (no CIDR); production ingress configuration must be verified |
| Rate-limit cleanup | Implemented in source | `AuthenticationRateLimitCleanup.removeExpiredBuckets`; `Application @EnableScheduling`; `AUTH_RATE_LIMIT_CLEANUP_CRON` | Scheduler/retention execution not yet observed in a running deployment |
| All authenticated frontend requests refresh-aware | Implemented in source | `authenticatedFetch.ts`; `EnterpriseSaasPanel`, `FinanceServicePanel`, `OfflineSyncPanel`, `OperationsPanel`, `PackageImageUpload`, `Phase2AdminPanel`, `PlatformAnalyticsPanel`, `VendorAnalyticsPanel`, `main.tsx` | Frontend tests/build for latest commit pending |
| SRS permission matrix | Partially implemented | V53; `DataSeeder.employeeRolePermissions`; `Controller.createEmployee`; escalation integration test | Legacy broad permissions are still checked; custom role editor and assigned-record scopes remain |
| Token storage recommendation | Reviewed, not migrated | `frontend/src/main.tsx` session persistence; SRS §15.1 | Access and refresh tokens remain in localStorage; HttpOnly/Secure/SameSite refresh-cookie + CSRF migration is a release blocker for strict SRS compliance |

### HttpOnly-cookie review decision

The current browser session persists both access and refresh tokens in localStorage. This exposes long-lived refresh credentials to any successful same-origin script injection. The SRS's HttpOnly-cookie recommendation is therefore not met.

A safe migration needs coordinated backend cookie issuance/clearing, SameSite/Secure policy, CORS credentials, CSRF token/header validation, frontend session bootstrap, refresh/logout contract compatibility and production origin verification. It was not introduced as an untested partial cookie change. **Keep this as an explicit production-readiness blocker.**

### CI observations

- The MySQL 8.4 migration job completed successfully through V53 in workflow run `42657544449`.
- An earlier backend run failed because the role-map edit accidentally removed two existing demo-seeding methods; those methods were restored in `DataSeeder.java`. A subsequent compile/test run is required to verify the repair.
- The rate-limit unit test previously failed because the mock request had an empty servlet path; the filter now falls back to request URI and strips the context path.
- The first frontend run after extracting `authenticatedFetch.ts` failed on an incorrectly escaped API base URL regex; the regex was corrected in commit `8b33a819`.
- The latest workflow run must be checked before declaring any backend/frontend tests or builds passed. Earlier green results do not prove the latest tree.

### Current Phase 1 decision

**Still PARTIALLY IMPLEMENTED — do not merge/deploy as production-ready.** The additional source work closes authentication auditing, refresh-family handling, trusted proxy configuration/cleanup and direct-fetch refresh gaps. The remaining critical blockers are the uncompleted fine-grained backend permission enforcement, localStorage refresh-token exposure, and successful latest-commit CI/test verification. Comprehensive tenant-resource negative tests and database-backed refresh replay race tests are also outstanding.


### Latest CI verification addendum (2026-11-09)

Workflow run [42659338279](https://github.com/rambrij/saumya-solar-energy-product/actions/runs/42659338279) completed successfully for commit `8888b5dd22df6e3270d0d6cbd9eaaae23fc9b12d`:
- Backend compile and tests: 108 tests, 0 failures, 0 errors, 0 skipped.
- Frontend component tests and production build: passed.
- MySQL 8.4 migration validation through V53: passed.
- Spring Boot package, startup and health check against MySQL 8.4/Mailpit: passed.

The H2 test profile now initializes `auth_rate_limit_bucket` through `schema-test.sql`; integration-test rate limits are raised only in the test profile. A test exposed that `EnterpriseSaasService.enforceUserLimit` executed a pessimistic tenant lock without an active transaction on subscribed-tenant requests. The method is now transactional, and the employee platform-role escalation integration test exercises that path.

The latest source commit after this successful run adds stricter numeric-only trusted-proxy address parsing. Its workflow run was still in progress when this addendum was written. Permission enforcement and localStorage refresh-token storage remain explicit Phase 1 gaps; successful builds do not close those design gaps.


Final source verification: workflow run [42659414140](https://github.com/rambrij/saumya-solar-energy-product/actions/runs/42659414140) for source commit `681c3267cf9b1adeb00d83432955f6283054b8c6` completed with all four jobs successful: backend compile/tests (108 tests, 0 failures/errors), frontend tests/build, MySQL V1–V53 migration validation, and Spring Boot startup/health against MySQL 8.4 and Mailpit. Only documentation files were changed after that verified source commit.


---

## Phase 1 security blocker closure addendum — 2026-11-09

The previously open Phase 1 production blockers have been addressed in code on this branch. See PHASE_1_AUTHENTICATION_SECURITY_IMPLEMENTATION.md §18 for the complete traceability and file-level evidence.

| Previously open gap | Closure | Evidence |
|---|---|---|
| Authentication audit-event persistence | Reused append-only audit_event; persisted login, lockout, refresh/replay, logout, password-reset, MFA, role and account-state events. | AuthenticationAuditService; AuthLifecycleService; AuthenticationAuditServiceTest |
| Fine-grained permission reconciliation | Replaced legacy broad checks across core services/controllers with SRS operation permissions; standard role grants reconciled; V54 removes deprecated broad employee grants. | DataSeeder.employeeRolePermissions; V54__reconcile_employee_role_permissions.sql; AppService; CatalogAdminService; OperationsService; FinanceService; SurveyLifecycleService; OfflineSyncService; PaymentGatewayCheckoutService; QuotationRevisionService; Phase2OperationsController; ServiceCommentController; WarrantyClaimAttachmentService |
| Custom role lifecycle | Tenant-scoped create, edit, delete-unused and employee assignment; strict vendor permission allowlist; platform/system roles protected. | Controller role/permission endpoints; AuthLifecycleService; RoleManagementPanel; AuthLifecycleServiceTest |
| Assigned-resource scope | Sales Executive and field-engineer resource access is restricted to assigned records; Sales Manager remains tenant-wide for leads. | AppService assigned filters; SurveyLifecycleService; OperationsService; FinanceService; CatalogAdminService; Release12TenantIntegrationTest |
| HttpOnly refresh-token storage | Refresh token moved out of JSON/localStorage to HttpOnly Secure SameSite cookie; double-submit CSRF on refresh/logout; access token memory-only; legacy localStorage session cleared. | AuthCookieService; Controller; Models.Session; SecurityConfig; authenticatedFetch.ts; main.tsx; AuthCookieServiceTest; Release12TenantIntegrationTest |
| Direct upload/download session refresh | Authenticated direct requests use common refresh/retry helper; FormData and binary responses preserved. | authenticatedFetch.ts; main.tsx and authenticated panel components; authenticatedFetch.test.ts |
| Refresh-token family replay | Reuse revokes active tokens in the family under database locking. | RefreshToken family fields; AuthLifecycleService; V53; concurrent replay integration test |
| Reverse-proxy IP trust and bucket retention | Forwarded IP headers are trusted only from allowlisted immediate peers; scheduled two-day bucket cleanup. | ClientAddressResolver; AuthenticationRateLimitFilter; AuthenticationRateLimitCleanup; ClientAddressResolverTest; AuthenticationRateLimitCleanupTest |
| Cross-tenant and privilege tests | Added cross-tenant negative checks for orders, quotations, invoices, service requests and installations; assigned-lead and platform-role escalation tests. | Release12TenantIntegrationTest |

### Phase 1 validation status

Workflow run [42669708678](https://github.com/rambrij/saumya-solar-energy-product/actions/runs/42669708678) passed backend compile/tests (116 tests, no failures), frontend production build, MySQL 8.4 Flyway migrations through V54, and Spring Boot startup/health check.

A subsequent run was triggered after adding AuthenticationRateLimitCleanupTest and updating the SRS/report. Its completion must be checked before treating the latest documentation/test-only commits as finally validated.

### Operational caveat

Code-level proxy trust and cleanup are implemented and tested, but deployed CORS_ORIGINS and TRUSTED_PROXY_IPS values have not been inspected. The Render workspace has not been confirmed. Production rollout requires exact CORS origin(s), HTTPS, secure cookie attributes and the immediate trusted proxy IP allowlist. The resolver currently supports exact IPs, not CIDR ranges.

The refresh API contract is now cookie-based; external/mobile clients using refresh-token request bodies must be migrated. Do not roll out until those clients and the production proxy/cookie settings are verified.


## Phase 1 production-hardening addendum — 2026-11-09

This addendum supersedes older Phase 1 gap statements in this file where they conflict with the current branch implementation.

### Authorization and role matrix
- Added restricted global `PLATFORM_SUPPORT` with only `TENANT_VIEW` and `AUDIT_VIEW`; tenant directory and audit-log reads are audited.
- Retained existing `MANAGER`, `INSTALLER` and `TECHNICIAN` roles for backward compatibility; they are not standard SRS §9.1 roles, have limited grants, and remain assigned-resource scoped.
- Platform tenant mutations remain restricted to `PLATFORM_SUPER_ADMIN`.
- V55 removes deprecated broad grants from all tenant-owned roles, including pre-existing custom roles.
- V56 removes platform-only grants from tenant-owned roles.
- New tenant/vendor-admin provisioning and development seeding exclude legacy broad and platform-only permissions.
- Sales and service/installation assigned-resource scope now derives from fine-grained assignment permissions for the reviewed flows.
- Custom roles without `LEAD_ASSIGN` or `SERVICE_ASSIGN` are restricted to their assigned records.
- Role assignment returns a safe DTO rather than the JPA user entity.
- Denied reserved-role and platform-permission escalation attempts are written in a separate `REQUIRES_NEW` audit transaction.

### Browser session and CSRF
- The browser access JWT is memory-only (900-second default), not persisted in localStorage or sessionStorage.
- The refresh token is HttpOnly, Secure and SameSite cookie scoped to `/api/v1/auth`; it is not returned in JSON or accepted from the browser refresh/logout request body.
- CSRF uses a host-only readable double-submit cookie and `X-CSRF-Token`; `GET /api/v1/auth/csrf` bootstraps new tabs.
- All identified authenticated upload/download API paths use the shared `authenticatedFetch` refresh/retry behavior.
- Automated tests cover fresh-tab CSRF bootstrap and ensure session helper does not persist access credentials in localStorage.

### Trusted proxy and rate-limit cleanup
- `ClientAddressResolver` supports exact IPv4/IPv6 and CIDR allowlists through `TRUSTED_PROXY_IPS`.
- It rejects malformed hostnames and catch-all `/0` networks and trusts forwarded chains only through configured proxy hops.
- Two-day rate-limit bucket cleanup remains scheduled.
- CIDR matching and untrusted-peer rejection have automated unit tests.

### New database migrations
- V55: all-tenant-role legacy permission cleanup and restricted Platform Support role.
- V56: platform-only permission removal from tenant roles.
- The CI MySQL 8.4 migration job was extended to apply V54–V56 and assert the final grant state.

### Additional negative tests
- Custom sales role can see only assigned leads and cannot reassign them.
- Custom service viewer can see only tickets assigned to its user and cannot read another engineer's ticket.
- Every seeded employee role receives its declared permission set and no legacy/platform permissions.
- Platform Support can read tenant directory/audit logs, cannot mutate platform state, and its reads are audited.
- A rejected custom-role platform permission escalation is audited despite request rollback.

### Remaining limitations
1. Final CI validation is pending for the latest source commit after the above role, service-scope, audit, CIDR and integration-test changes.
2. Actual Render ingress proxy addresses/ranges, `CORS_ORIGINS`, cookie attributes and HTTPS forwarding chain cannot be verified from repository source; production environment verification remains required.
3. Cross-tab refresh is now serialized with Web Locks on supported browsers. Browsers without Web Locks retain per-tab coalescing only and may trigger token-family replay protection; define a supported-browser policy or add a separately reviewed fallback.
4. Cross-site third-party cookie restrictions may affect Vercel/Render default domains; use same-site custom subdomains or a same-origin reverse proxy where possible.
5. External/mobile clients using the old JSON refresh-token contract must migrate or receive a separately designed client flow.

**Phase 1 status remains conditional, not production-approved, until latest CI passes and deployment proxy/CORS/cookie settings are verified.**


### Cross-tab refresh follow-up

`frontend/src/authenticatedFetch.ts` now uses the browser Web Locks API with the shared lock name `saumya-auth-refresh`. The lock encloses CSRF-cookie bootstrap and refresh rotation. Every queued tab fetches the latest CSRF value after acquiring the lock, then rotates the refresh cookie currently shared by the browser. This prevents simultaneous refresh requests from separate supported tabs from reusing the same old refresh token. The frontend test suite verifies lock acquisition and the CSRF-before-refresh sequence. Browsers without Web Locks fall back to per-tab single-flight behavior and remain a documented compatibility limitation.


### Render deployment alignment finding (read-only verification)

The available Render workspace contains a service named `soumya-solar-api` (`https://soumya-solar-api.onrender.com`) configured to deploy from `https://github.com/rambrij/soumya-solar-energy-solution` on `main`, not from the audited `saumya-solar-energy-product` repository or `audit/implementation-gap-analysis` branch.

Its Render ingress IP allowlist is `0.0.0.0/0` (public ingress). This is not the application's trusted reverse-proxy list and must not be interpreted as one. The available service details do not reveal environment variable values, so production `TRUSTED_PROXY_IPS`, `CORS_ORIGINS`, refresh-cookie Secure/SameSite/domain settings and forwarded-header behavior remain unverified. No deployment or Render configuration was modified.

**Production blocker:** the currently inspected Render service is not aligned with the repository being hardened. Repointing it to this repository/branch and verifying runtime environment values requires explicit deployment approval.


### Final source CI result — 2026-11-09

Final source commit `aca7facda4fbd4133f79b732c9b5b1bc0ce1e931` passed [GitHub Actions run 42674221540](https://github.com/rambrij/saumya-solar-energy-product/actions/runs/42674221540):
- Backend compile and tests: **127 passed, 0 failures, 0 errors, 0 skipped**.
- Frontend typecheck, **20 component tests**, and production build: **PASS**.
- MySQL 8.4 migration validation V1–V56, including explicit V54/V55/V56 assertions: **PASS**.
- Spring Boot package/startup/health against MySQL 8.4 and Mailpit: **PASS**.
- Overall workflow conclusion: **SUCCESS**.

The report and gap analysis were updated after this source commit; no application source changes were made after the passing CI run.

The remaining production blocker is outside the source tree: the inspected Render API service still points at the legacy `saumya-solar-energy-solution` repository on `main`, not this repository/branch. Its runtime CORS, cookie and trusted-proxy environment values are not exposed in the service inspection response. Do not claim production deployment readiness until the intended Render service is explicitly aligned and its runtime settings are verified.


---

## Phase 2 — Tenant isolation and data integrity (2026-11-10)

### Scope and audit method

This section extends the existing analysis. The source-of-truth order is the business requirements document, then the SRS/HLD, then this gap analysis. README was not used to validate Phase 2 behavior. The source audit covered all API controller mapping inventories, service/repository interfaces, scheduled components, migration V1–V58 and the frontend source/test inventory. Evidence below is file path plus line number from the audited branch; line numbers refer to the post-change source unless stated otherwise.

**Stack detected:** Java 21, Spring Boot 3.5.7, Maven, Spring Data JPA, MySQL 8.4, Flyway SQL migrations, React/TypeScript, Vite and Vitest. Test frameworks: JUnit 5/Spring Boot Test/Mockito and Vitest/Testing Library. No separate ORM tenant filter or universal tenant-aware base repository was found; the current design is service-level tenant-scoped repository methods.

### Phase 2 audit table

| Item | Requirement ref | Status (Implemented/Partial/Missing) | Evidence (file:line) | Planned fix |
|---|---|---|---|---|
| Authenticated tenant identity is reloaded and matched to JWT tenant/account claims | SRS §8.2, §15; TEN-02 | Implemented | `backend/src/main/java/com/saumyasolar/security/JwtFilter.java:36-56` | Retain DB-backed identity check; add broader tampered-claim integration cases |
| Generic vendor list endpoints (users/catalog/customers/enquiries/leads/surveys/quotes/orders) | Business req §13; SRS §8.2, §20.4 | Partial | `backend/src/main/java/com/saumyasolar/service/AppService.java:157-240` | Expand guessed-ID/list/export tests for every resource and permission/assignment combination |
| Generic create endpoints and linked category/product/customer validation | SRS §8.2, §10.4 | Partial | `AppService.java:298-344`; `Controller.java:439-444` | Add cross-tenant body-ID mutation tests and DB FK coverage for all relationships |
| Lead update/history and employee assignment | SRS §9.3, §10.3 | Partial | `AppService.java:650-696`; `Controller.java:472-477` | Complete negative tests for reassignment, branch scope and history IDOR |
| Survey create/update and survey-linked quotation validation | SRS §10.5, §8.4 | Partial | `AppService.java:717-767`; `SurveyLifecycleService.java`; `SurveyLifecycleController.java` | Add full guessed-ID and attachment boundary tests |
| Quotation create/action/revision/PDF endpoints | SRS §10.6, §8.4 | Partial | `Controller.java:491-516`; `AppService.java:751-933`; `QuotationRevisionService.java` | Exercise every action, revision and PDF using Tenant A against Tenant B IDs |
| Package/product/category update/delete and image upload | SRS §10.4, §8.2 | Partial | `Controller.java:559-628`; `AppService.java:458-526` | Complete update/delete/upload cross-tenant tests; verify object key ownership |
| Customer profile/address/order endpoints | Business req §3; SRS §10.3, §10.12 | Partial (core ownership negative tests added) | `CustomerAccountController.java`; `CustomerAccountService.java`; `Release12TenantIntegrationTest.customerAddressEndpointsEnforceAuthenticatedCustomerOwnershipAndIgnoreTamperedOwnerIds`; `Release12TenantIntegrationTest.customerCannotReadAnotherCustomersOrdersInvoicesOrServiceTickets` | Extend coverage to every customer-owned route, write action and download |
| Inventory/warehouse/order/installations/warranty endpoints | SRS §10.8-10.11 | Partial | `Controller.java:696-916`; `OperationsService.java:90-1055` | Expand branch/customer IDOR and tampered body IDs; keep 404 for out-of-scope resources |
| Finance invoice/payment/refund/service endpoints | SRS §10.12-10.14 | Partial | `FinanceController.java:37-263`; `FinanceService.java:131-450,563-993` | Add tenant/customer/branch tests for every mutation and read path |
| Operations reports CSV/PDF | Business req §14; SRS §10.16, §8.3 | Partial (JSON/CSV tenant isolation test added) | `Phase2OperationsController.java:290-383`; `Release12TenantIntegrationTest.vendorCannotReadForeignSurveyOrDownloadForeignServiceEvidence` | Add equivalent XLSX/PDF content assertions and branch-filter export tests |
| Platform tenant CSV/PDF exports | SRS §9, §10.2 | Partial | `Controller.java:300-426`; `TenantAdminService.java` | Keep platform-only authorization; test non-platform denial and audit of export |
| Quotation PDF download | SRS §10.6, §8.3 | Partial | `Controller.java:516`; `AppService.java:921-933` | Add cross-tenant and assigned-sales PDF negative tests |
| Invoice/payment PDF and receipts | SRS §10.12, §8.3 | Partial | `FinanceController.java:72-114`; `FinanceService.java:131-450` | Add customer ownership and cross-tenant binary response tests |
| Private service/survey/warranty attachment download | SRS §8.3, §10.14 | Partial (service/survey parent IDOR tests added) | `ServiceAttachmentService.java`; `SurveyAttachmentService.java`; `WarrantyClaimAttachmentService.java`; `Release12TenantIntegrationTest.vendorCannotReadForeignSurveyOrDownloadForeignServiceEvidence` | Add warranty-claim attachment negative test and direct attachment-ID tampering tests for each module; no signed URL is currently used by these controller paths |
| Service SLA scheduler | SRS §8.3, §10.14 | Implemented (source) | `ServiceSlaScheduler.java:7-22`; `FinanceService.java:1037-1070` | Scheduler now iterates tenants and passes explicit tenant ID; add multi-tenant scheduler integration test |
| Subscription expiry scheduler | SRS §8.3, §10.2 | Implemented (source) | `SubscriptionExpiryScheduler.java:14-48`; `TenantSubscriptionRepository.java` | Scheduler now iterates tenants and uses tenant-qualified query; add multi-tenant scheduler integration test |
| Warranty expiry/reminder scheduler | SRS §8.3, §10.11 | Implemented (source) | `WarrantyExpiryScheduler.java:13-57`; `WarrantyRepository.java`; `WarrantyExpirySchedulerTest.java` | Tenant-qualified expiry/reminder queries and test fixture added; add DB-backed multi-tenant test |
| Integration queue scan/claim/process | SRS §8.3, §10.17 | Implemented (source) | `IntegrationSyncScheduler.java:11-130`; `IntegrationSyncJobRepository.java` | Queue discovery, atomic claim and reload now include tenant ID; add concurrent multi-tenant scheduler test |
| Scheduled jobs tenant context lifecycle | SRS §8.3 | Implemented (scheduled jobs) | `security/TenantExecutionContext.java:1-35`; `ServiceSlaScheduler.java:20-24`; `SubscriptionExpiryScheduler.java:32-50`; `WarrantyExpiryScheduler.java:44-62`; `IntegrationSyncScheduler.java:39-45` | Each tenant iteration opens an explicit ThreadLocal scope, queries by the context tenant ID and restores/clears the previous scope in try-with-resources. Async/event listeners must use the same scope before being enabled |
| Product → category FK | SRS §8.3, §11 | Implemented by V57 | `V1__complete_release2.sql`; `V57__tenant_aware_catalogue_foreign_keys.sql` | V57 adds composite (tenant_id,id) parent key and tenant-aware FK after fail-closed integrity preflight |
| Package item → package/product FKs | SRS §8.3, §11 | Implemented by V57 | `V1__complete_release2.sql`; `V57__tenant_aware_catalogue_foreign_keys.sql` | V57 requires non-null package_item.tenant_id and adds both composite tenant-aware FKs |
| Branch relationships | SRS §8.3, §11 | Implemented for branch-owned records | `V37__enterprise_saas_foundation.sql`; `V43__branch_scope_business_records.sql` | Existing composite tenant/branch FKs retained; broader customer/employee relationship coverage remains |
| Sales, order, inventory, installation, warranty, finance, service, evidence, offline, integration and actor FKs | SRS §8.3, §11 | Implemented by V58 (schema constraints); production preflight still required | `V58__operational_tenant_composite_foreign_keys.sql` (89 composite tenant FKs); `tenant-isolation-v58.sql` | Run V58 fail-closed preflight against a production snapshot; remediate all mismatches before deployment |
| Repository query predicates (derived/JPQL/native) | SRS §8.2-8.3 | Partial | `repo/*.java`; example `CustomerOrderRepository.java:13-25`, `ProductRepository.java:9-15` | Tenant-aware derived methods are common; exhaustive query-by-query static/runtime proof remains |
| Frontend tenant/customer/branch filters and route params | SRS §6.2, §8.2 | Partial | `frontend/src/EnterpriseSaasPanel.tsx`; `OperationsPanel.tsx`; `FinanceServicePanel.tsx`; `authenticatedFetch.ts` | Client filters remain UX only; add API-level tampering tests and verify 403/404 handling per workflow |
| Phase 1 identity and permission baseline | SRS §8.2, §9 | Implemented as prerequisite baseline | `JwtFilter.java:36-60`; `PHASE_1_AUTHENTICATION_SECURITY_IMPLEMENTATION.md`; V54–V56 | Do not redo Phase 1; tenant-resource coverage still needs Phase 2 negative tests |

### What changed in Phase 2

1. **SLA breach processing:** removed the global `ServiceRequestRepository.findAll()` scan. `FinanceService.evaluateSlaBreaches(UUID tenantId)` rejects a null tenant and reads only `findByTenantIdOrderByCreatedAtDesc(tenantId)`. The scheduler iterates tenants and passes the explicit tenant ID.
2. **Subscription expiry:** replaced the global expiry query with `findByTenantIdAndStatusInAndEndsAtBefore`; the scheduler loops over tenants and audits each expired subscription with that subscription's tenant ID.
3. **Warranty expiry/reminders:** replaced unscoped warranty queries with tenant-qualified repository methods. The scheduler loops over tenants; customer lookup already includes the warranty tenant. Updated unit tests to exercise tenant-qualified query calls.
4. **Integration sync:** queued and stale job discovery now includes tenant ID; atomic claim includes both job ID and tenant ID; reload uses `findByIdAndTenantId`; connection lookup remains tenant-qualified. All scheduled workers now use `TenantExecutionContext` with try-with-resources cleanup; SLA service rejects a missing or mismatched active tenant scope.
5. **Database:** V57 protects product/category and package-item/package/product relationships. New V58 adds 89 composite tenant-aware foreign keys across sales, survey, quotation, order, inventory, installation, warranty, invoice, payment/refund, service, evidence, offline sync, integration, branch membership, engineer profiles, approval actors, audit branch and notification ownership. V58 performs fail-closed orphan/cross-tenant preflight checks before adding constraints and adds composite parent keys/indexes.
6. **Database negative tests:** `tenant-isolation-v57.sql` and `tenant-isolation-v58.sql` are executed in MySQL 8.4 CI. V58 assertions prove cross-tenant lead-history, service-comment and service-request/customer references are rejected.
7. **Endpoint negative tests:** expanded `Release12TenantIntegrationTest` for customer-owned address tampering, customer-to-customer order/invoice/service isolation, generic vendor/customer list isolation across 13 list routes, cross-tenant branch CRUD/membership/analytics, foreign survey/service-attachment parent access, operations report/CSV export scoping and vendor denial of platform tenant export. `DataSeeder` now includes `BRANCH_MANAGE` so seeded vendor admins receive the same branch permission that V51 grants in migrated databases.
8. **CI:** release validation applies/asserts V57 and V58 on MySQL 8.4 and runs the expanded backend integration suite.

### Gaps deferred / remaining evidence

- **Full endpoint-level negative matrix:** 222 HTTP mappings have been inventoried. Negative tests now cover the critical tenant/customer/branch and private attachment/report paths listed in the matrix below, but not every action of every mapping. Authentication lifecycle, platform-only management, all CRUD variants for each domain resource, warranty-claim attachment downloads, and every XLSX/PDF export still require dedicated negative tests.
- **Customer and branch matrix:** core cross-customer addresses/orders/invoices/service and cross-tenant branch membership/analytics/CRUD tests now exist. Systematic ID-guessing remains for all customer resources and branch-assigned record families; null-branch visibility remains a product policy question.
- **Database constraints:** V57 + V58 cover catalogue and the operational/financial/evidence relationships listed in V58. Remaining legacy links not yet converted include global/platform identity relations and resource links not represented by a tenant_id/id pair. Run V58 preflight on production data before rollout; do not bypass a failure.
- **Background jobs:** scheduled loops are explicitly tenant-qualified. The integration sync worker also carries tenant ID from the persisted job. No current `@Async` method was identified in the reviewed backend inventory; asynchronous event listeners and future queue consumers need explicit tenant context before use.
- **Signed URLs:** reviewed attachment controllers stream bytes after checking parent and attachment ownership; no signed-URL route was found in those paths. If object storage later returns signed URLs, the token must bind tenant + resource and expire.
- **Frontend:** source has authenticated API helpers and role/branch UI filters. UI filters are not an authorization control; complete API negative tests are still needed to demonstrate tampered client filters cannot broaden data access.
- **Migration data preflight:** V57 and V58 contain fail-closed SQL checks. CI passes against an isolated MySQL 8.4 migration database. This is not a substitute for running the same preflight against a production snapshot; existing production data has not been inspected.

### Document conflicts

- The business requirements describe vendor/tenant separation and customer identities as tenant-specific; the SRS §8.1 explicitly says a customer may have separate identities per vendor and cross-vendor customer unification is not required. These are consistent.
- The SRS §8.2 allows tenant resolution from trusted JWT/server-side identity or verified public storefront slug. No conflict: public storefront slug is a separate unauthenticated discovery path; authenticated business writes must use principal-derived tenant.
- The SRS requires tenant-owned entities to have non-null tenant_id, while V1 contains nullable tenant_id on some older relationship tables. V57 makes package_item.tenant_id NOT NULL after a preflight check; V58 explicitly blocks nullable legacy sales tenant IDs before creating composite constraints.
- No business-vs-technical conflict was found that changes Phase 2 scope.

### Verification status and baseline

- **Local baseline/build:** not run. A local clone was attempted but the execution environment could not resolve github.com. The repository's GitHub Actions run for base-branch commit `295e284900d7aa7f85dc002f8aa2f905d4ec36df` is the available baseline.
- **Baseline backend:** 128 tests, 1 failure, 0 errors, 0 skipped. Existing failure: `Release12TenantIntegrationTest.customSalesRoleCannotReadUnassignedLeadsOrAssignOutsideItsScope` expected the blocked platform-permission escalation audit event but did not find it.
- **Baseline frontend:** 22 tests, 1 failure. Existing failure: `public-navigation.test.tsx` queries `Our Products` with `getByRole`, but the responsive header renders two matching links.
- **Baseline MySQL migration and application startup jobs:** passed on that base commit.
- **Phase 2 branch CI:** run `42779608893` on commit `969ec08c171861b7929086b4e73d8cbf269d9387` passed all four jobs: backend 136 tests, 0 failures/errors; frontend 22 tests, 0 failures plus production build; MySQL 8.4 V1–V58 migrations plus V57/V58 cross-tenant FK assertions; and Spring Boot startup against MySQL 8.4/Mailpit. Later commits add CSV export assertions and documentation; their latest CI run is tracked separately. No local test pass is claimed; verification was performed in GitHub Actions.
- **Lint/format:** no dedicated lint/format scripts or Maven formatter plugin were found in the inspected build configuration; frontend `npm run build` performs TypeScript typecheck plus Vite production build. The existing frontend duplicate-responsive-link test and role-escalation audit persistence regression were corrected after the base CI exposed those pre-existing failures. The audit remains in the caller transaction to preserve its actor foreign-key ordering; the specific role-escalation service methods commit on `ResponseStatusException` so the denied-attempt event survives.

### Migration and rollback notes

- V57 and V58 are additive and ordered after V56/V57. They do not rewrite IDs or edit any previously applied migration.
- Before rollout, run the preflight statements against a production-like snapshot and resolve any mismatch rather than bypassing the checks.
- Forward rollback for V57: drop `fk_package_item_product_tenant`, `fk_package_item_package_tenant`, and `fk_product_category_tenant`; drop the three V57 `uq_*_tenant_id` indexes; restore `package_item.tenant_id` nullable only if the schema contract explicitly requires it.
- Forward rollback for V58: drop the 89 named `fk_*_tenant` constraints in V58, then drop the V58 `uq_*_tenant_id` indexes. Do not drop existing V37/V43/V49/V57 constraints or indexes. Prefer a reviewed forward corrective migration in shared environments; Flyway Community does not automatically reverse applied versioned migrations.
- V57/V58 add many composite indexes and may take metadata/DDL locks. Apply during a controlled migration window, inspect production preflight output, and verify MySQL DDL behavior in staging first.

### Open Questions

1. Should the Phase 2 acceptance boundary include every legacy single-column tenant-owned FK in the full schema, or may the remaining relationships be delivered as a separately approved Phase 2.1 migration set?
2. Are branch-unassigned legacy records intentionally visible to all branch memberships, as current `EnterpriseSaasService` behavior allows for null branch IDs?
3. Which browser versions are supported for the frontend's branch and role filtering UX? (Authorization remains server-side regardless.)


### Phase 2 endpoint security test matrix (post-V58)

The matrix below records the endpoint families exercised by automated negative tests. It is not a claim that every mapping in the 222-route inventory has an individual test. Unlisted operations remain Partial and must not be treated as production-accepted.

| Endpoint family | Negative scenarios executed | Automated evidence | Status / remaining |
|---|---|---|---|
| Catalogue product | Cross-tenant category ID on create; cross-tenant product excluded from list | `Release12TenantIntegrationTest.productCannotReferenceAnotherTenantsCategory`; `productListDoesNotExposeAnotherTenantsProducts`; `tenant-isolation-v57.sql` | Implemented for create/list and DB category relationship; update/delete/image still Partial |
| Catalogue update/delete and customer order create | Foreign product/category/package update and delete IDs return 404; customer product-order creation with a foreign product ID plus tampered tenant/customer IDs is rejected | `Release12TenantIntegrationTest.genericVendorListEndpointsNeverReturnForeignTenantResources` | Implemented for these catalogue mutation and customer-order create paths; category/product/package image upload and all other resource mutation routes remain Partial |
| Generic vendor/customer lists | Cross-tenant rows excluded from `/users`, `/categories`, `/products`, `/packages`, `/customers`, `/enquiries`, `/leads`, `/surveys`, `/quotations`, `/orders`, `/catalog-categories`, `/catalog-products` and `/catalog-packages` | `Release12TenantIntegrationTest.genericVendorListEndpointsNeverReturnForeignTenantResources` | Implemented for list predicates; remaining customer-specific `/customer-enquiries` and `/customer-orders` are covered by the customer-specific tests above, while every other read/write verb remains separately tracked |
| Enquiry/lead | Foreign employee assignment rejected; assigned-sales user cannot read/update another user's lead; cross-tenant lead history read denied | `Release12TenantIntegrationTest.leadCannotBeAssignedToEmployeeFromAnotherTenant`; `salesExecutiveCanOnlyReadAndUpdateAssignedLeads`; `customSalesRoleCannotReadUnassignedLeadsOrAssignOutsideItsScope`; V58 SQL | Partial: all lead action variants and enquiry IDOR not individually tested |
| Order/quotation/invoice/service/installation | Foreign tenant guessed IDs denied for order history, quotation revisions, invoice items, service ticket and installation checklist | `Release12TenantIntegrationTest.vendorCannotReadAnotherTenantsOrdersQuotesInvoicesServiceOrInstallationRecords` | Partial: write/action/PDF/payment/refund paths still require per-route tests |
| Customer-owned data | Customer A cannot list customer B addresses/orders/invoices/service tickets; address update/delete guessed IDs return 404; tampered tenant/customer IDs in address create are ignored | `Release12TenantIntegrationTest.customerAddressEndpointsEnforceAuthenticatedCustomerOwnershipAndIgnoreTamperedOwnerIds`; `customerCannotReadAnotherCustomersOrdersInvoicesOrServiceTickets` | Partial: profile, payment, installation, warranty, quotation and document actions still require tests |
| Branch | Foreign branch membership, analytics, update, deactivate and assignment access denied | `Release12TenantIntegrationTest.vendorBranchEndpointsCannotReadOrMutateAnotherTenantsBranch` | Partial: assigned-branch record filters and null-branch behavior remain |
| Private evidence | Foreign service-ticket attachment list/download parent and foreign survey detail/attachment list denied | `Release12TenantIntegrationTest.vendorCannotReadForeignSurveyOrDownloadForeignServiceEvidence` | Partial: warranty-claim attachment, direct foreign attachment IDs and upload tampering remain |
| Reporting/export | Tenant operations report and CSV export use caller tenant; vendor cannot invoke platform tenant export | `Release12TenantIntegrationTest.vendorCannotReadForeignSurveyOrDownloadForeignServiceEvidence` | Partial: XLSX/PDF output assertions, filter tampering and platform export audit remain |
| Database constraints | Cross-tenant category/package/product, lead-history, service-comment and service-request/customer links rejected | `tenant-isolation-v57.sql`; `tenant-isolation-v58.sql`; MySQL 8.4 migration CI | Implemented for tested constraints; all 88 V58 constraints have preflight and schema enforcement; production snapshot preflight remains |
| Scheduled jobs | Tenant-qualified SLA, subscription expiry, warranty expiry and integration queue tests; execution context cleanup test | `ServiceSlaSchedulerTest`; `SubscriptionExpirySchedulerTest`; `WarrantyExpirySchedulerTest`; `TenantExecutionContextTest` | Partial: multi-tenant database-backed concurrent worker test and async event tests remain |

### Controller endpoint inventory (one row per HTTP mapping)

The route annotations below were enumerated from every `*Controller.java` under `backend/src/main/java/com/saumyasolar/api/`. All are marked **Partial** until the corresponding cross-tenant/customer/branch negative tests exist; current service checks are not treated as proof that every verb and identifier path is closed.

| Controller | HTTP mapping | Status | Evidence | Required verification |
|---|---|---|---|---|
| BranchAnalyticsController.java | `@GetMapping("/{branchId}/analytics")` | Partial | `backend/src/main/java/com/saumyasolar/api/BranchAnalyticsController.java:19` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/auth/login")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:109` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/auth/mfa/verify")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:125` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/auth/csrf")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:141` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/auth/refresh")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:149` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/auth/logout")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:167` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PatchMapping("/users/{userId}/role")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:175` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/users")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:191` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/auth/register")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:210` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/auth/forgot-password")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:217` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/auth/reset-password")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:222` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/me")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:228` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping( "/{type:tenants\|users\|categories\|products\|packages\|customers\|enquiries\|leads\|surveys\|quotations\|orders\|customer-orders\|catalog-categories\|catalog-products\|catalog-packages\|customer-enquiries}" )` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:233` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping({ "/tenant/settings", "/settings" })` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:240` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PatchMapping({ "/tenant/settings/discount-threshold", "/settings/discount-threshold" })` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:245` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/roles")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:250` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/permissions")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:255` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/roles")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:264` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PatchMapping("/roles/{roleId}")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:273` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@DeleteMapping("/roles/{roleId}")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:282` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/customer/enquiries")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:295` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/platform/tenants")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:300` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/platform/tenants/{id}")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:311` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PatchMapping("/platform/tenants/{id}")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:316` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping(value = "/platform/tenants/export", produces = "text/csv")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:324` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping( value = "/platform/tenants/export.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" )` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:342` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping(value = "/platform/tenants/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:376` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/tenants")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:426` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/{type:categories\|products\|packages\|customers\|enquiries}")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:439` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PatchMapping("/tenants/{id}/status")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:444` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PatchMapping("/users/{id}/enabled")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:449` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/enquiries/{id}/convert")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:467` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PatchMapping("/leads/{id}")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:472` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/leads/{id}/history")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:477` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/surveys")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:482` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/quotations")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:491` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/quotations/{id}/{action:approve\|reject-approval\|send\|accept\|reject}")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:496` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/quotations/{id}/convert-to-order")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:501` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/quotations/{id}/revisions")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:506` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/quotations/{id}/revisions")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:511` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping(value = "/quotations/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:516` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PatchMapping("/packages/{id}")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:559` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@DeleteMapping("/packages/{id}")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:568` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping(value = "/packages/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:573` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/public/{storeSlug}/enquiries")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:584` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/public/{storeSlug}/packages")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:589` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/public/{storeSlug}/products")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:594` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/catalog/products")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:600` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PatchMapping("/products/{id}")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:605` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@DeleteMapping("/products/{id}")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:614` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PatchMapping("/categories/{id}")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:619` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@DeleteMapping("/categories/{id}")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:628` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PatchMapping("/customers/{id}")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:633` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@DeleteMapping("/customers/{id}")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:642` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PatchMapping("/users/{id}")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:647` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@DeleteMapping("/users/{id}")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:659` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/customer/product-orders")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:676` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/customer/product-orders")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:681` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/orders/{id}/vendor-decision")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:686` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/warehouses")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:696` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/warehouses")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:701` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/inventory/balances")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:706` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/inventory/transactions")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:711` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/inventory/transactions/{id}/reverse")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:716` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/inventory/receipts")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:725` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/inventory/returns")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:730` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/inventory/issues")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:735` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/inventory/low-stock")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:740` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/inventory/damages")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:745` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/inventory/adjustments")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:750` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/inventory/transfers")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:758` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/orders/{id}/reserve")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:763` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/orders/{id}/release-reservation")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:772` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/orders/{id}/transition")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:781` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/orders/{id}/history")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:786` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/installation-teams")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:791` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/installation-teams")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:796` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PatchMapping("/installation-teams/{id}")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:801` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/installation-checklist-templates")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:810` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/installation-checklist-templates")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:815` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@DeleteMapping("/installation-checklist-templates/{id}")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:823` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/installations")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:831` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/installations")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:836` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/installations/{id}/checklist")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:844` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PatchMapping("/installations/{id}/checklist/{itemId}")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:852` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/installations/{id}/reschedule")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:862` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/installations/{id}/cancel")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:871` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/installations/{id}/schedule-history")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:880` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/installations/{id}/complete")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:888` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@PostMapping("/installations/{id}/commission")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:897` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/warranties")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:906` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/customer/installations")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:911` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Controller.java | `@GetMapping("/customer/warranties")` | Partial | `backend/src/main/java/com/saumyasolar/api/Controller.java:916` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| CustomerAccountController.java | `@GetMapping("/profile")` | Partial | `backend/src/main/java/com/saumyasolar/api/CustomerAccountController.java:46` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| CustomerAccountController.java | `@PatchMapping("/profile")` | Partial | `backend/src/main/java/com/saumyasolar/api/CustomerAccountController.java:55` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| CustomerAccountController.java | `@GetMapping("/addresses")` | Partial | `backend/src/main/java/com/saumyasolar/api/CustomerAccountController.java:64` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| CustomerAccountController.java | `@PostMapping("/addresses")` | Partial | `backend/src/main/java/com/saumyasolar/api/CustomerAccountController.java:73` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| CustomerAccountController.java | `@PutMapping("/addresses/{id}")` | Partial | `backend/src/main/java/com/saumyasolar/api/CustomerAccountController.java:83` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| CustomerAccountController.java | `@DeleteMapping("/addresses/{id}")` | Partial | `backend/src/main/java/com/saumyasolar/api/CustomerAccountController.java:96` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@GetMapping("/integrations")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:108` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@PostMapping("/integrations")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:113` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@PatchMapping("/integrations/{id}/status")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:118` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@GetMapping("/integrations/{id}/sync-jobs")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:127` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@PostMapping("/integrations/{id}/sync-jobs")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:132` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@PostMapping("/integrations/sync-jobs/{jobId}/retry")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:141` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@GetMapping("/approval-workflows")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:146` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@PostMapping("/approval-workflows")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:151` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@GetMapping("/approval-instances")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:156` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@GetMapping("/approval-instances/{id}")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:161` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@GetMapping("/approval-instances/{id}/decisions")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:166` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@PostMapping("/approval-instances/{id}/decisions")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:171` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@GetMapping("/vendor/branding")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:180` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@PatchMapping("/vendor/branding")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:185` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@GetMapping("/public/{slug}/branding")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:190` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@GetMapping("/public/store-context")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:195` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@GetMapping("/vendor/domains")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:210` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@PostMapping("/vendor/domains")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:215` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@PostMapping("/vendor/domains/{id}/verify")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:220` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@GetMapping("/branches")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:225` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@PostMapping("/branches")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:230` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@PutMapping("/branches/{id}")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:235` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@PostMapping("/branches/{id}/activate")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:240` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@PostMapping("/branches/{id}/deactivate")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:246` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@PostMapping("/branches/{id}/users")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:252` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@GetMapping("/branches/{id}/users")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:262` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@DeleteMapping("/branches/{id}/users/{userId}")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:267` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@GetMapping("/subscription-plans")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:273` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@GetMapping("/platform/subscription-plans")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:278` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@GetMapping("/platform/subscription-plans/{planId}")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:283` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@PutMapping("/platform/subscription-plans/{planId}")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:288` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@PostMapping("/platform/subscription-plans/{planId}/activate")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:293` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@PostMapping("/platform/subscription-plans/{planId}/deactivate")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:298` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@DeleteMapping("/platform/subscription-plans/{planId}")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:303` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@GetMapping("/platform/tenants/search")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:309` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@GetMapping("/platform/tenants/{tenantId}/subscriptions")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:314` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@GetMapping("/platform/tenants/{tenantId}/subscriptions/{subscriptionId}")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:319` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@PutMapping("/platform/tenants/{tenantId}/subscriptions/{subscriptionId}")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:328` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@GetMapping("/platform/tenants/{tenantId}/subscription-history")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:338` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@PostMapping("/platform/subscription-plans")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:343` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@PostMapping("/platform/tenants/{tenantId}/subscriptions/{subscriptionId}/cancel")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:348` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@PostMapping("/platform/tenants/{tenantId}/subscriptions")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:357` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@PatchMapping("/warehouses/{id}/branch")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:368` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@GetMapping("/subscription")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:377` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@GetMapping("/platform/analytics/tenant-usage")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:386` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| EnterpriseSaasController.java | `@GetMapping("/audit-events")` | Partial | `backend/src/main/java/com/saumyasolar/api/EnterpriseSaasController.java:395` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@GetMapping("/finance/orders-awaiting-invoice")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:37` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@GetMapping("/invoices")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:42` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@PostMapping("/invoices")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:47` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@GetMapping("/invoices/{id}/items")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:52` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@PostMapping("/invoices/{id}/credit-notes")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:57` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@GetMapping("/invoices/{id}/credit-notes")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:62` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@PostMapping("/invoices/{id}/void")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:67` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@GetMapping(value = "/invoices/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:72` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@GetMapping("/payments")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:109` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@GetMapping(value = "/payments/{id}/receipt.pdf", produces = MediaType.APPLICATION_PDF_VALUE)` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:114` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@PostMapping("/payments")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:133` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@PostMapping("/payments/{id}/refund")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:138` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@GetMapping("/refunds")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:148` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@PostMapping("/payment-webhooks/{provider}")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:153` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@GetMapping("/service-requests")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:168` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@GetMapping("/service-requests/{id}")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:173` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@PostMapping("/service-requests")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:178` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@PostMapping("/service-requests/{id}/assign")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:183` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@PostMapping("/service-requests/{id}/visits")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:188` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@PostMapping("/service-requests/{id}/visits/{visitId}/start")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:193` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@PostMapping("/service-requests/{id}/visits/{visitId}/complete")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:198` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@PostMapping("/service-requests/{id}/sla/pause")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:208` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@PostMapping("/service-requests/{id}/sla/resume")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:213` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@PostMapping("/service-requests/{id}/confirm")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:218` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@PostMapping("/service-requests/{id}/close")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:223` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@PostMapping("/service-requests/{id}/reopen")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:228` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@GetMapping("/service-requests/{id}/visits")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:233` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@GetMapping("/service-requests/{id}/history")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:238` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@GetMapping("/service-customers")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:243` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@GetMapping("/service-engineers")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:248` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@PostMapping("/service-settings/reopen-policy")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:253` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@GetMapping("/service-sla-policies")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:258` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| FinanceController.java | `@PostMapping("/service-sla-policies")` | Partial | `backend/src/main/java/com/saumyasolar/api/FinanceController.java:263` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| IntegrationAdapterCapabilitiesController.java | `@GetMapping("/capabilities")` | Partial | `backend/src/main/java/com/saumyasolar/api/IntegrationAdapterCapabilitiesController.java:27` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| OfflineSyncController.java | `@PostMapping("/operations")` | Partial | `backend/src/main/java/com/saumyasolar/api/OfflineSyncController.java:24` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| OfflineSyncController.java | `@GetMapping("/operations")` | Partial | `backend/src/main/java/com/saumyasolar/api/OfflineSyncController.java:30` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| PaymentGatewayController.java | `@GetMapping("/availability")` | Partial | `backend/src/main/java/com/saumyasolar/api/PaymentGatewayController.java:18` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| PaymentGatewayController.java | `@PostMapping("/invoices/{invoiceId}/checkout")` | Partial | `backend/src/main/java/com/saumyasolar/api/PaymentGatewayController.java:23` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| PaymentGatewayWebhookController.java | `@PostMapping("/{provider}")` | Partial | `backend/src/main/java/com/saumyasolar/api/PaymentGatewayWebhookController.java:16` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Phase2OperationsController.java | `@GetMapping("/engineers")` | Partial | `backend/src/main/java/com/saumyasolar/api/Phase2OperationsController.java:115` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Phase2OperationsController.java | `@PostMapping("/engineers")` | Partial | `backend/src/main/java/com/saumyasolar/api/Phase2OperationsController.java:142` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Phase2OperationsController.java | `@PatchMapping("/customer/messaging-consent")` | Partial | `backend/src/main/java/com/saumyasolar/api/Phase2OperationsController.java:187` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Phase2OperationsController.java | `@GetMapping("/customer/messaging-consent")` | Partial | `backend/src/main/java/com/saumyasolar/api/Phase2OperationsController.java:198` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Phase2OperationsController.java | `@GetMapping("/warranty-claims")` | Partial | `backend/src/main/java/com/saumyasolar/api/Phase2OperationsController.java:205` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Phase2OperationsController.java | `@GetMapping("/customer/warranty-claims")` | Partial | `backend/src/main/java/com/saumyasolar/api/Phase2OperationsController.java:212` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Phase2OperationsController.java | `@PostMapping("/customer/warranty-claims")` | Partial | `backend/src/main/java/com/saumyasolar/api/Phase2OperationsController.java:219` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Phase2OperationsController.java | `@PostMapping("/warranty-claims/{id}/approve")` | Partial | `backend/src/main/java/com/saumyasolar/api/Phase2OperationsController.java:240` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Phase2OperationsController.java | `@PostMapping("/warranty-claims/{id}/reject")` | Partial | `backend/src/main/java/com/saumyasolar/api/Phase2OperationsController.java:246` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Phase2OperationsController.java | `@PostMapping("/warranty-claims/{id}/resolve")` | Partial | `backend/src/main/java/com/saumyasolar/api/Phase2OperationsController.java:266` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Phase2OperationsController.java | `@GetMapping("/reports/operations")` | Partial | `backend/src/main/java/com/saumyasolar/api/Phase2OperationsController.java:290` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Phase2OperationsController.java | `@GetMapping(value = "/reports/operations/export.csv", produces = "text/csv")` | Partial | `backend/src/main/java/com/saumyasolar/api/Phase2OperationsController.java:338` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Phase2OperationsController.java | `@GetMapping( value = "/reports/operations/export.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" )` | Partial | `backend/src/main/java/com/saumyasolar/api/Phase2OperationsController.java:350` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| Phase2OperationsController.java | `@GetMapping(value = "/reports/operations/export.pdf", produces = "application/pdf")` | Partial | `backend/src/main/java/com/saumyasolar/api/Phase2OperationsController.java:383` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| ServiceAttachmentController.java | `@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)` | Partial | `backend/src/main/java/com/saumyasolar/api/ServiceAttachmentController.java:28` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| ServiceAttachmentController.java | `@GetMapping List<ServiceAttachment> list(@PathVariable UUID ticketId, Authentication a) {` | Partial | `backend/src/main/java/com/saumyasolar/api/ServiceAttachmentController.java:37` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| ServiceAttachmentController.java | `@GetMapping("/{attachmentId}")` | Partial | `backend/src/main/java/com/saumyasolar/api/ServiceAttachmentController.java:42` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| ServiceCommentController.java | `@GetMapping public List<ServiceComment> list(@PathVariable UUID ticketId, Authentication a) {` | Partial | `backend/src/main/java/com/saumyasolar/api/ServiceCommentController.java:63` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| ServiceCommentController.java | `@PostMapping @Transactional public ServiceComment add(@PathVariable UUID ticketId, @Valid @RequestBody CommentInput x, Authentication a) {` | Partial | `backend/src/main/java/com/saumyasolar/api/ServiceCommentController.java:71` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| SubscriptionUsageController.java | `@GetMapping("/usage")` | Partial | `backend/src/main/java/com/saumyasolar/api/SubscriptionUsageController.java:23` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| SubscriptionUsageController.java | `@GetMapping("/storage")` | Partial | `backend/src/main/java/com/saumyasolar/api/SubscriptionUsageController.java:28` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| SurveyAttachmentController.java | `@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)` | Partial | `backend/src/main/java/com/saumyasolar/api/SurveyAttachmentController.java:26` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| SurveyAttachmentController.java | `@GetMapping public List<SiteSurveyAttachment> list(@PathVariable UUID surveyId, Authentication a) {` | Partial | `backend/src/main/java/com/saumyasolar/api/SurveyAttachmentController.java:35` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| SurveyAttachmentController.java | `@GetMapping("/{attachmentId}")` | Partial | `backend/src/main/java/com/saumyasolar/api/SurveyAttachmentController.java:40` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| SurveyLifecycleController.java | `@GetMapping("/{id}")` | Partial | `backend/src/main/java/com/saumyasolar/api/SurveyLifecycleController.java:29` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| SurveyLifecycleController.java | `@GetMapping("/{id}/history")` | Partial | `backend/src/main/java/com/saumyasolar/api/SurveyLifecycleController.java:34` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| SurveyLifecycleController.java | `@PatchMapping("/{id}")` | Partial | `backend/src/main/java/com/saumyasolar/api/SurveyLifecycleController.java:39` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| SurveyLifecycleController.java | `@PostMapping("/{id}/start")` | Partial | `backend/src/main/java/com/saumyasolar/api/SurveyLifecycleController.java:47` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| SurveyLifecycleController.java | `@PostMapping("/{id}/submit")` | Partial | `backend/src/main/java/com/saumyasolar/api/SurveyLifecycleController.java:52` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| SurveyLifecycleController.java | `@PostMapping("/{id}/approve")` | Partial | `backend/src/main/java/com/saumyasolar/api/SurveyLifecycleController.java:57` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| SurveyLifecycleController.java | `@PostMapping("/{id}/reject")` | Partial | `backend/src/main/java/com/saumyasolar/api/SurveyLifecycleController.java:62` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| SurveyLifecycleController.java | `@PostMapping("/{id}/cancel")` | Partial | `backend/src/main/java/com/saumyasolar/api/SurveyLifecycleController.java:70` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| VendorAnalyticsController.java | `@GetMapping("/breakdowns")` | Partial | `backend/src/main/java/com/saumyasolar/api/VendorAnalyticsController.java:31` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| VendorAnalyticsController.java | `@GetMapping("/trends")` | Partial | `backend/src/main/java/com/saumyasolar/api/VendorAnalyticsController.java:45` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| VendorAnalyticsController.java | `@GetMapping("/rankings")` | Partial | `backend/src/main/java/com/saumyasolar/api/VendorAnalyticsController.java:62` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| WarrantyClaimAttachmentController.java | `@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)` | Partial | `backend/src/main/java/com/saumyasolar/api/WarrantyClaimAttachmentController.java:26` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| WarrantyClaimAttachmentController.java | `@GetMapping public List<WarrantyClaimAttachment> list(@PathVariable UUID claimId, Authentication a) {` | Partial | `backend/src/main/java/com/saumyasolar/api/WarrantyClaimAttachmentController.java:35` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |
| WarrantyClaimAttachmentController.java | `@GetMapping("/{attachmentId}")` | Partial | `backend/src/main/java/com/saumyasolar/api/WarrantyClaimAttachmentController.java:40` | Tenant/customer/branch IDOR test; body/query/header tampering; export/download where applicable |

### Repository interface inventory

All 69 Spring Data repository interfaces in `com.saumyasolar.repo` are enumerated here. This is an interface inventory, not a claim that every method has been proven safe: Spring Data's inherited `findAll` and `findById` remain unscoped unless the service performs an ownership check. Custom JPQL and derived methods with `TenantId` predicates were found in several interfaces; customer and branch restrictions still require service checks and endpoint tests. The `@Query` / method signatures explicitly reviewed include `AppUserRepository`, `ApprovalInstanceRepository`, `CustomerAddressRepository`, `CustomerOrderRepository`, `EngineerProfileRepository`, `IntegrationSyncJobRepository`, `InventoryBalanceRepository`, `InvoiceRepository`, `InvoiceSequenceRepository`, `PaymentRepository`, `QuotationRepository`, `RefreshTokenRepository`, `TenantStorageLedgerRepository`, and `WarehouseRepository`. All remain Partial pending call-site and negative-test coverage.

| Repository | Status | Evidence | Planned fix |
|---|---|---|---|
| AppUserRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/AppUserRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| ApprovalDecisionRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/ApprovalDecisionRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| ApprovalInstanceRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/ApprovalInstanceRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| ApprovalWorkflowRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/ApprovalWorkflowRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| AuditEventRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/AuditEventRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| BranchMembershipRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/BranchMembershipRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| BranchRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/BranchRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| CreditNoteRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/CreditNoteRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| CustomerAddressRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/CustomerAddressRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| CustomerOrderRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/CustomerOrderRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| CustomerProfileRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/CustomerProfileRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| EngineerProfileRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/EngineerProfileRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| EnquiryRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/EnquiryRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| IdempotencyRecordRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/IdempotencyRecordRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| InstallationChecklistItemRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/InstallationChecklistItemRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| InstallationChecklistTemplateRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/InstallationChecklistTemplateRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| InstallationJobRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/InstallationJobRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| InstallationScheduleHistoryRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/InstallationScheduleHistoryRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| InstallationTeamRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/InstallationTeamRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| IntegrationConnectionRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/IntegrationConnectionRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| IntegrationSyncJobRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/IntegrationSyncJobRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| IntegrationWebhookEventRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/IntegrationWebhookEventRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| InventoryBalanceRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/InventoryBalanceRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| InventoryReservationRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/InventoryReservationRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| InventoryTransactionRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/InventoryTransactionRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| InvoiceItemRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/InvoiceItemRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| InvoiceRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/InvoiceRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| InvoiceSequenceRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/InvoiceSequenceRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| LeadHistoryRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/LeadHistoryRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| MfaChallengeRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/MfaChallengeRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| NotificationDeliveryRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/NotificationDeliveryRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| OfflineSyncAttachmentRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/OfflineSyncAttachmentRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| OfflineSyncOperationRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/OfflineSyncOperationRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| OrderItemRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/OrderItemRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| OrderStatusHistoryRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/OrderStatusHistoryRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| PackageItemRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/PackageItemRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| PasswordResetTokenRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/PasswordResetTokenRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| PaymentRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/PaymentRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| PaymentWebhookEventRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/PaymentWebhookEventRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| PermissionRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/PermissionRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| ProductCategoryRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/ProductCategoryRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| ProductRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/ProductRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| QuotationItemRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/QuotationItemRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| QuotationRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/QuotationRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| RefreshTokenRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/RefreshTokenRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| RefundRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/RefundRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| RoleRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/RoleRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| SalesLeadRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/SalesLeadRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| ServiceAttachmentRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/ServiceAttachmentRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| ServiceCommentRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/ServiceCommentRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| ServiceEventRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/ServiceEventRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| ServiceRequestRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/ServiceRequestRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| ServiceSlaPolicyRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/ServiceSlaPolicyRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| ServiceVisitRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/ServiceVisitRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| SiteSurveyAttachmentRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/SiteSurveyAttachmentRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| SiteSurveyHistoryRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/SiteSurveyHistoryRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| SiteSurveyRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/SiteSurveyRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| SolarPackageRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/SolarPackageRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| SubscriptionAssignmentHistoryRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/SubscriptionAssignmentHistoryRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| SubscriptionPlanRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/SubscriptionPlanRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| SubscriptionUsageRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/SubscriptionUsageRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| TenantDomainRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/TenantDomainRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| TenantRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/TenantRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| TenantStorageLedgerRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/TenantStorageLedgerRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| TenantSubscriptionRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/TenantSubscriptionRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| WarehouseRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/WarehouseRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| WarrantyClaimAttachmentRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/WarrantyClaimAttachmentRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| WarrantyClaimRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/WarrantyClaimRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
| WarrantyRepository.java | Partial | `backend/src/main/java/com/saumyasolar/repo/WarrantyRepository.java` | Verify each declared and inherited query at every service call site; tenant/customer/branch predicate tests; add tenant-qualified methods where missing |
