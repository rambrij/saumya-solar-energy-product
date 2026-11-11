# Cleanup traceability

**Audit branch:** `cleanup/final-code-cleanup`  
**Audit base:** `audit/implementation-gap-analysis`  
**Phase:** 0 — documentation and evidence inventory only  
**Scope rule:** this document is a traceability index, not authorization to delete. A path is removable only after a separate usage/reference search and an explicit entry in `CLEANUP_DECISIONS.md`.

## Sources of truth

| Priority | Source | Sections reviewed |
|---|---|---|
| 1 | `saumya-solar-energy-solution-requirements.md` | §§1–20: actors, public/customer/vendor workflows, CRM, quotations, orders, inventory, installation, service, vendor/team management, tenant isolation, reports, communications, CMS, architecture, security, audit, reliability, entities and roadmap |
| 2 | `saumya-solar-production-ready-srs-hld.md` | §§1–27 and appendices: scope, architecture, tenant isolation, roles/permissions, functional modules, screen catalogue, workflows, REST API catalogue, DB design, security, NFRs, integrations, background jobs/events, validation, acceptance, testing, DevOps, seed data and definition of done |
| 3 | `IMPLEMENTATION_GAP_ANALYSIS.md` | Audit method and traceability; gaps and conflicts; backend/frontend/database/security/tenant/workflow/test evidence; Phase 1–6 implementation and verification addenda |

README.md was not used as a validation source.

## Implementation map

The source documents define the business and technical scope. The implementation map below records concrete branch-head locations confirmed from the repository tree and the existing gap/decision records. API endpoint-level and individual entity-to-table references must be reconciled against controller mappings and the baseline SQL before any deletion or migration rewrite.

| Requirement / module | Business reference | SRS/HLD reference | Backend implementation | Frontend screen / implementation | Configuration and SQL |
|---|---|---|---|---|---|
| Platform and vendor identity, authentication, session lifecycle | §1 | §§9, 10.1, 13.2–13.3, 15.1–15.2 | `backend/src/main/java/com/saumyasolar/api/Controller.java`; `security/`; `service/`; `domain/`; `repo/` | `frontend/src/main.tsx`; `frontend/src/authenticatedFetch.ts`; `frontend/src/RoleNavigation.tsx` | `backend/src/main/resources/application*.yml`; Flyway V1 and subsequent security/schema deltas |
| Tenant/vendor onboarding and lifecycle | §§1, 11, 13 | §§8, 10.2, 12.5, 13.3, 14, 20.1 | `EnterpriseSaasController.java`, `Controller.java`, domain/repo/service packages | `main.tsx`; `Phase2AdminPanel.tsx`; `RoleNavigation.tsx` | Flyway V1–V14; tenant composite-FK and lifecycle additions are recorded in the gap analysis |
| User, team, RBAC, custom roles and permissions | §§1, 12 | §§9, 10.1, 11.3–11.4, 13.3, 15.2, 20.2 | `security/`, `Controller.java`, `EnterpriseSaasController.java`, auth/role services and repositories | `RoleManagementPanel.tsx`, `RoleNavigation.tsx`, `main.tsx` | V1 plus permission-catalogue/grant migrations; application security configuration |
| Public website, storefront, solar packages and product catalogue | §§2, 17, 19 | §§3.1, 7, 10.4, 11.1, 13.4 | `Controller.java`, `CatalogModels.java`, catalogue/product/package services and repositories | `PublicHome.tsx`, `PublicSiteHeader.tsx`, `main.tsx`, `PackageImageUpload.tsx` | V1 baseline; object-storage and upload properties in application configuration |
| Customer registration, portal, profile and documents | §§1–3 | §§10.1, 10.3, 11.2, 13.2, 13.5–13.7 | `CustomerAccountController.java`, `Controller.java`, customer/account/document services and repositories | `main.tsx`; customer portal/document views | V1 and later customer/document-related deltas; storage configuration |
| Enquiries, leads, follow-ups, site surveys and CRM | §§2, 5 | §§10.3, 10.5, 11.3, 12.1, 13.5 | `Controller.java` and CRM/survey services, domain and repository packages | `main.tsx`; CRM/operations workspace | V1 and relevant later schema deltas |
| Quotation creation, revision, PDF and email | §§2, 6 | §§10.6, 11.3, 12.1–12.2, 13.5 | `Controller.java`, quotation services/domain/repositories, PDF/export infrastructure | `main.tsx`; quotation editor and operations workspace | V1 and relevant later schema deltas; mail/storage configuration |
| Orders, state policy, payment lifecycle and status history | §§3, 7 | §§10.7, 10.10, 12.1–12.3, 13.6–13.7, 19 | `Controller.java`, `FinanceController.java`, `FinanceModels.java`, order/finance services and repositories | `main.tsx`, `FinanceServicePanel.tsx`, operations workspace | V1–V5 and later finance deltas; Flyway configuration |
| Inventory, warehouse, stock movements and reservation expiry | §8 | §§10.8, 12.3, 13.6, 14, 20.4 | `Controller.java`, inventory services/domain/repositories | `OperationsPanel.tsx`, `main.tsx` | V1 plus reservation/operations schema deltas |
| Installation, commissioning, checklists and assignments | §9 | §§10.9, 12.1, 13.6, 20.5 | `Controller.java`, installation services/domain/repositories | `OperationsPanel.tsx`, `main.tsx` | V1 plus later tenant/assignment/index deltas |
| Warranty, service requests, visits and SLA evaluation | §10 | §§10.11, 12.4, 13.7, 20.6 | `Controller.java`, service/warranty services/domain/repositories | `OperationsPanel.tsx`, `FinanceServicePanel.tsx`, `main.tsx` | V1 plus later warranty/service schema deltas |
| Invoices, receipts, refunds and reconciliation | §§3, 7, 14 | §§10.10, 13.7, 14, 19 | `FinanceController.java`, `FinanceModels.java`, finance services/domain/repositories | `FinanceServicePanel.tsx`, `main.tsx` | V1 and V5 finance/provider/reconciliation schema; Flyway |
| Notifications, preferences, inbox, outbox and channel adapters | §15 | §§10.12, 11.3–11.4, 16.5, 17–18 | `NotificationService`, `NotificationDelivery`, `UserNotification`, `UserNotificationService`, `IntegrationSyncJob`, `IntegrationSyncScheduler`; notification adapter registry and API controllers | `NotificationInboxPanel.tsx`, `main.tsx` | V7–V8 inbox/permission; V12–V14 outbox/preferences/provider-event and tenant FK changes; notification/mail configuration |
| CMS, blog, banners, testimonials and public content | §16 | §§10.13, 11.1, 11.3, 13.8 | `CmsController.java`, CMS domain/service/repository packages | `PublicHome.tsx`, `main.tsx` | V6 CMS/revisions; media storage configuration |
| Reporting, analytics, exports and downloads | §14 | §§10.14, 11.3–11.4, 13.8, 16 | `BranchAnalyticsController.java`, `ExportController.java`, report/export services and repositories | `VendorAnalyticsPanel.tsx`, `PlatformAnalyticsPanel.tsx`, `OperationsPanel.tsx`, `main.tsx` | V9/V11 analytics indexes and V1 report/export schema; export storage configuration |
| Platform settings, audit and administration | §§1, 11, 18 | §§10.15, 11.4, 13.8, 15.6 | `EnterpriseSaasController.java`, audit/settings services/domain/repositories | `PlatformSettingsPanel.tsx`, `PlatformAnalyticsPanel.tsx`, `Phase2AdminPanel.tsx` | V10 typed platform settings; V1 audit/admin schema |
| External integrations and offline synchronization | §§15, 17 | §§17–18, 16.5 | `IntegrationAdapterCapabilitiesController.java`, integration adapter/service/scheduler packages | `OfflineSyncPanel.tsx`, `OperationsPanel.tsx` | Integration and scheduler configuration; schema in V1 and later deltas |
| Security, audit, observability, health and reliability | §18 | §§15–16, 20.7–20.8, 21–22, 25 | `security/`, `config/`, `ApiExceptionHandler.java`, health/audit/monitoring services | `main.tsx` and operational/admin screens | `application*.yml`, Docker Compose, CI workflows; V1 and security/audit schema deltas |

## API contract traceability

The API source of truth is SRS/HLD §13 (standards and API groups §13.1–13.8). The corresponding implementation is in `backend/src/main/java/com/saumyasolar/api/`; the principal aggregated REST controller is `Controller.java`. Specialized controllers confirmed in the branch tree include:

- `CustomerAccountController.java` — customer account/portal API family.
- `EnterpriseSaasController.java` — platform/vendor administration and SaaS API family.
- `FinanceController.java` — finance/payment/refund/reconciliation API family.
- `CmsController.java` — CMS authoring and public content API family.
- `ExportController.java` — export job and private download API family.
- `BranchAnalyticsController.java` — branch analytics API family.
- `IntegrationAdapterCapabilitiesController.java` — integration capability API family.
- `ApiExceptionHandler.java` — common API error contract.

Mock payment simulator/settlement fixture controllers are explicitly profile/config gated per the existing finance decision record. Their exact route exposure and production-profile tests must be rechecked before any contract or file removal. The exact HTTP method + path inventory remains governed by the controller annotations and existing endpoint matrix; no endpoint is approved for removal in Phase 0.

## Screen traceability

SRS/HLD §11 is the screen catalogue. Confirmed frontend entry point is `frontend/src/main.tsx`. The supporting named screen components above cover public content, customer/vendor operations, finance, notifications, role management, analytics, platform settings, offline sync and administration. Screen names and route guards must be cross-checked against `main.tsx` and the role navigation configuration before changing route/menu code.

## Database and migration traceability

The configured migration system is Flyway at `backend/src/main/resources/db/migration`. The **post-cleanup branch contains exactly one active versioned migration**:

| Migration | Scope |
|---|---|
| `V1__baseline.sql` | Complete clean-install schema, constraints, indexes, triggers, permissions/reference data and seed initialization |

The baseline consolidates the original active V1–V14 migration chain. The initial source-tree inventory had 14 migration files; V2–V14 were removed from the active directory after their full SQL statements were incorporated into V1. The baseline was generated from all 14 source files and preserves their statements in execution order, while grouping schema DDL before reference/seed DML.

| Consolidated SQL group | Statement count |
|---|---:|
| `CREATE TABLE` | 84 |
| `ALTER TABLE` (columns/checks/foreign keys/table constraints) | 14 |
| Standalone `CREATE INDEX` | 6 |
| `CREATE TRIGGER` | 8 |
| Reference and initialization DML (`INSERT`, `UPDATE`, `DELETE`, etc.) | 67 |
| Unclassified SQL statements | 0 |

The 84 tables include all 74 original baseline tables plus the ten later domain tables: `order_transition_policy`; `payment_reconciliation_report` and `payment_reconciliation_line`; `cms_content` and `cms_content_revision`; `user_notification`; `platform_setting`; `notification_outbox`, `notification_preference` and `notification_provider_event`.

Tenant isolation constraints, including the composite tenant foreign keys from notification outbox/provider events to delivery records, remain represented in the consolidated schema. The append-only order-status and CMS-revision triggers, order transition policy seed, permission catalogue/grants, notification status checks, indexes and all other previous V2–V14 effects are retained in V1.

**Schema equivalence is a CI gate, not yet a claim:** `.github/workflows/release-validation.yml` fetches the pre-cleanup `audit/implementation-gap-analysis` branch, applies its original 14 migrations to an empty MySQL 8.4 database, applies the new V1 baseline to a separate empty database, dumps both schemas (including triggers), normalizes generated AUTO_INCREMENT counters and diffs the dumps. See `docs/CLEANUP_SCHEMA_DIFF.md` for the run record. The baseline remains intended for fresh installations only; no production database migration or Flyway history transformation is performed.

## Configuration and delivery traceability

- Backend runtime configuration: `backend/src/main/resources/application*.yml` and environment variables consumed by Spring configuration.
- Local infrastructure: root `docker-compose.yml`; backend and frontend Docker/build configuration.
- Frontend build/runtime configuration: `frontend/package.json`, frontend build configuration and environment variable references.
- CI/release gates: `.github/workflows/*.yml`; release-validation workflow is specifically `.github/workflows/release-validation.yml`.
- Scripts: root `scripts/`, `start-clean.ps1`, and any scripts referenced by compose, package scripts or workflows.

The exact property-to-bean, environment-variable-to-property, and workflow-to-script references must be captured by repository-wide reference searches in the removal inventory before deletion.

## Phase 0 outcome and limitations

- The three mandated source documents were fetched in full from the target branch and their complete heading structures were indexed above. README.md was excluded.
- The target branch was created from `audit/implementation-gap-analysis`.
- No runtime, configuration, SQL, test, CI, or existing documentation file was removed or modified in this phase. The existing `docs/CLEANUP_DECISIONS.md` was preserved and extended separately.
- The initial branch-tree inventory confirms V1–V14 and the listed major controllers/screens. A complete route-annotation inventory, full dependency/property reference graph, and migration equivalence run are still required before phases 1–3.
- A local clone was attempted but this execution environment could not resolve `github.com`; therefore local Maven/npm/MySQL/Playwright commands cannot be honestly reported as executed from this session. GitHub Actions remains the available execution/verification route.


## Complete REST controller and endpoint inventory

The inventory below was extracted from every Java class under `backend/src/main/java/com/saumyasolar/api/` that declares a Spring HTTP mapping. The `/api/v1` prefix is declared at the controller class level where shown. It is an implementation inventory; it does not imply that undocumented paths are required by the SRS. No endpoint in this inventory is removable without a caller/reference search and a documented-contract comparison.

| Controller | Base path | Methods and endpoint paths (relative to base where applicable) | Requirement group |
|---|---|---|---|
| `Controller.java` | `/api/v1` | POST `/auth/login`, `/auth/mfa/verify`, `/auth/refresh`, `/auth/logout`, `/auth/register`, `/auth/forgot-password`, `/auth/reset-password`; GET `/auth/csrf`, `/me`; PATCH `/users/{userId}/role`, `/users/{id}/enabled`, `/users/{id}`; POST `/users`; GET `/users` is not declared in this class-level list; GET `/roles`, `/permissions`; POST `/roles`; PATCH `/roles/{roleId}`; DELETE `/roles/{roleId}`; POST `/customer/enquiries`; GET `/platform/tenants`, `/platform/tenants/{id}`, `/platform/tenants/export` (CSV), `/platform/tenants/export.pdf`; PATCH `/platform/tenants/{id}`; POST `/tenants`; POST `/{type:categories|products|packages|customers|enquiries}`; PATCH `/tenants/{id}/status`, `/packages/{id}`, `/leads/{id}`, `/categories/{id}`, `/products/{id}`, `/customers/{id}`; POST `/platform/tenants/{id}/lifecycle`; DELETE `/packages/{id}`, `/products/{id}`, `/categories/{id}`, `/customers/{id}`; POST `/enquiries/{id}/convert`, `/surveys`, `/quotations`, `/quotations/{id}/{action:approve|reject-approval|send|accept|reject}`, `/quotations/{id}/convert-to-order`, `/quotations/{id}/revisions`, `/packages/{id}/image`, `/public/{storeSlug}/enquiries`, `/catalog/products`, `/customer/product-orders`; GET `/leads/{id}/history`, `/quotations/{id}/revisions`, `/quotations/{id}/pdf`, `/public/{storeSlug}/packages`, `/public/{storeSlug}/products`, `/customer/product-orders`, `/warehouses`, `/inventory/balances`, `/inventory/reservations`, `/inventory/transactions`, `/inventory/low-stock`, `/orders/transition-options`, `/orders/transition-policy`, `/orders/{id}/history`, `/installation-teams`, `/installation-checklist-templates`, `/installations`, `/installations/{id}/checklist`, `/installations/{id}/schedule-history`, `/warranties`, `/customer/installations`, `/customer/warranties`; PUT `/orders/transition-policy`; POST `/warehouses`, `/inventory/transactions/{id}/reverse`, `/inventory/receipts`, `/inventory/returns`, `/inventory/issues`, `/inventory/damages`, `/inventory/adjustments`, `/inventory/transfers`, `/orders/{id}/reserve`, `/orders/{id}/release-reservation`, `/orders/{id}/transition`, `/installation-teams`, `/installations`, `/installations/{id}/reschedule`, `/installations/{id}/cancel`, `/installations/{id}/complete`, `/installations/{id}/commission`; PATCH `/installation-teams/{id}`, `/installations/{id}/checklist/{itemId}`; DELETE `/installation-checklist-templates/{id}` | Auth, users/RBAC, vendors/tenants, catalogue, CRM, quotation, orders, inventory, installation, customer portal |
| `EnterpriseSaasController.java` | `/api/v1` | GET/POST `/integrations`; PATCH `/integrations/{id}/status`; GET/POST `/integrations/{id}/sync-jobs`; POST `/integrations/sync-jobs/{jobId}/retry`; GET/POST `/approval-workflows`; GET `/approval-instances`, `/approval-instances/{id}`, `/approval-instances/{id}/decisions`; POST `/approval-instances/{id}/decisions`; GET/PATCH `/vendor/branding`; GET `/public/{slug}/branding`, `/public/store-context`; GET/POST `/vendor/domains`; POST `/vendor/domains/{id}/verify`; GET/POST `/branches`; PUT `/branches/{id}`; POST `/branches/{id}/activate`, `/branches/{id}/deactivate`, `/branches/{id}/users`; GET `/branches/{id}/users`; DELETE `/branches/{id}/users/{userId}`; GET `/subscription-plans`, `/platform/subscription-plans`, `/platform/subscription-plans/{planId}`, `/platform/tenants/search`, `/platform/tenants/{tenantId}/subscriptions`, `/platform/tenants/{tenantId}/subscriptions/{subscriptionId}`, `/platform/tenants/{tenantId}/subscription-history`, `/subscription`, `/platform/analytics/tenant-usage`, `/audit-events`; PUT `/platform/tenants/{tenantId}/subscriptions/{subscriptionId}`, `/branches/{id}/users`; POST `/platform/subscription-plans`, `/platform/subscription-plans/{planId}/activate`, `/platform/subscription-plans/{planId}/deactivate`, `/platform/tenants/{tenantId}/subscriptions/{subscriptionId}/cancel`, `/platform/tenants/{tenantId}/subscriptions`; PATCH `/warehouses/{id}/branch`; DELETE `/platform/subscription-plans/{planId}` | Integration, approval workflows, vendor, branches, subscriptions, platform analytics, audit |
| `CustomerAccountController.java` | `/api/v1/customer` | GET/PATCH `/profile`; GET/POST `/addresses`; PUT/DELETE `/addresses/{id}` | Customer identity/profile/address |
| `FinanceController.java` | `/api/v1` | GET `/finance/orders-awaiting-invoice`, `/invoices`, `/invoices/{id}/items`, `/payments`, `/payments/{id}/receipt.pdf`, `/refunds`, `/service-requests`, `/service-requests/{id}`, `/service-requests/{id}/visits`, `/service-requests/{id}/history`, `/service-customers`, `/service-engineers`, `/service-sla-policies`; POST `/invoices`, `/invoices/{id}/credit-notes`, `/invoices/{id}/void`, `/payments`, `/payments/{id}/refund`, `/payment-webhooks/{provider}`, `/service-requests`, `/service-requests/{id}/assign`, `/service-requests/{id}/visits`, `/service-requests/{id}/visits/{visitId}/start`, `/service-requests/{id}/visits/{visitId}/complete`, `/service-requests/{id}/sla/pause`, `/service-requests/{id}/sla/resume`, `/service-requests/{id}/confirm`, `/service-requests/{id}/close`, `/service-requests/{id}/reopen`, `/service-settings/reopen-policy`, `/service-sla-policies`; GET `/invoices/{id}/pdf`; GET `/invoices/{id}/credit-notes` | Invoices, payments, refunds, receipts, service and SLA |
| `CmsController.java` | `/api/v1` | POST `/cms/media`, `/cms/content`, `/cms/content/{id}/transition`, `/cms/content/{id}/rollback`; GET `/cms/content`, `/cms/content/{id}`, `/public/{storeSlug}/content/{type}`, `/public/{storeSlug}/content/{type}/{slug}`; PATCH `/cms/content/{id}` | CMS/blog/public storefront |
| `ExportController.java` | `/api/v1/exports` | POST base path; GET `/{exportId}`, `/{exportId}/download` | Asynchronous CSV/XLSX/PDF exports and private downloads |
| `BranchAnalyticsController.java` | `/api/v1/branches` | GET `/{branchId}/analytics` | Branch-scoped reporting |
| `VendorAnalyticsController.java` | `/api/v1/vendor/analytics` | GET `/breakdowns`, `/trends`, `/operations`, `/rankings` | Vendor analytics and operations reports |
| `PlatformBusinessAnalyticsController.java` | `/api/v1/platform/analytics` | GET `/overview` | Aggregate-only platform analytics |
| `PlatformSettingsController.java` | `/api/v1/platform/settings` | GET base path; PUT `/{key}` | Typed platform settings |
| `Phase2OperationsController.java` | `/api/v1` | GET/POST `/engineers`; GET/PATCH `/customer/messaging-consent`; GET `/warranty-claims`, `/customer/warranty-claims`; POST `/customer/warranty-claims`, `/warranty-claims/{id}/approve`, `/warranty-claims/{id}/reject`, `/warranty-claims/{id}/resolve`; GET `/reports/operations` | Engineers, warranty claims, messaging consent, reports |
| `ServiceAttachmentController.java` | `/api/v1/service-requests/{ticketId}/attachments` | POST base path; GET base path and `/{attachmentId}` | Private service attachments |
| `ServiceCommentController.java` | `/api/v1/service-requests/{ticketId}/comments` | GET/POST base path | Service-ticket collaboration/history |
| `SurveyLifecycleController.java` | `/api/v1/surveys` | GET `/{id}`, `/{id}/history`; PATCH `/{id}`; POST `/{id}/start`, `/{id}/submit`, `/{id}/approve`, `/{id}/reject`, `/{id}/cancel` | Site surveys and approval lifecycle |
| `SurveyAttachmentController.java` | `/api/v1/surveys/{surveyId}/attachments` | POST base path; GET base path and `/{attachmentId}` | Private survey attachments |
| `WarrantyClaimAttachmentController.java` | `/api/v1/warranty-claims/{claimId}/attachments` | POST base path; GET base path and `/{attachmentId}` | Private warranty evidence |
| `UserNotificationController.java` | `/api/v1/notifications` | GET base path, `/unread-count`; PATCH `/{id}/read`; POST `/read-all` | User-owned in-app inbox |
| `NotificationPreferenceController.java` | `/api/v1/notifications/preferences` | GET/PUT base path | Per-user/channel notification preference and consent |
| `NotificationOperationsController.java` | `/api/v1/notifications/operations` | GET `/deliveries`, `/outbox`, `/integration-jobs` | Tenant-scoped notification/integration monitoring |
| `NotificationWebhookController.java` | `/api/v1/notification-webhooks/mock` | POST base path; class is dev/test profile only | Mock delivery callback for tests/development |
| `OfflineSyncController.java` | `/api/v1/offline-sync` | POST/GET `/operations` | Offline operation synchronization |
| `PaymentGatewayController.java` | `/api/v1/payment-gateways` | GET `/availability`; POST `/invoices/{invoiceId}/checkout` | Provider-neutral checkout |
| `PaymentGatewayWebhookController.java` | `/api/v1/payment-gateway-webhooks` | POST `/{provider}` | Verified provider-neutral payment callback |
| `PaymentReconciliationController.java` | `/api/v1/finance/reconciliations` | POST base path; GET base path and `/{reportId}/lines` | Settlement reconciliation |
| `MockPaymentGatewaySimulatorController.java` | `/api/v1/payment-gateways/mock` | POST `/payments/{paymentId}/complete`, `/refunds/{refundId}/complete`; class is dev/test profile only | Deterministic mock payment lifecycle |
| `MockSettlementFixtureController.java` | `/api/v1/payment-gateways/mock` | POST `/settlement-fixtures`; class is dev/test profile only | Deterministic reconciliation fixtures |
| `SubscriptionUsageController.java` | `/api/v1/subscription` | GET `/usage`, `/storage` | Tenant subscription limits/usage |
| `IntegrationAdapterCapabilitiesController.java` | `/api/v1` | GET mapping path is not duplicated here; class path and exact mapping are retained in source | Integration adapter capability discovery |
| `ApiExceptionHandler.java` | Not an HTTP controller | Global exception-to-Problem-Details mapping | Common error contract |

## Screen and frontend component inventory

The frontend tree contains the following active route/screen components; the main route/menu composition and role guards are in `frontend/src/main.tsx` and `frontend/src/RoleNavigation.tsx`.

| Screen / component | Main responsibility | Backend API families |
|---|---|---|
| `PublicHome.tsx`, `PublicSiteHeader.tsx`, `BrandLogo.tsx` | Public home, storefront content and navigation | `/public/{storeSlug}/...`, public branding and CMS content |
| `EnterpriseSaasPanel.tsx` | Vendors, plans, subscriptions, integrations, branches | `EnterpriseSaasController`, tenant APIs |
| `CmsManagementPanel.tsx` | Content authoring, revision, publish and media | `CmsController` |
| `RoleManagementPanel.tsx`, `RoleNavigation.tsx` | Custom roles, permissions, role-based menus | `/roles`, `/permissions`, identity/session APIs |
| `OperationsPanel.tsx` | CRM, site survey, orders, inventory, installations, warranty/service operations | `Controller`, `Phase2OperationsController`, `SurveyLifecycleController`, attachment/comment APIs |
| `FinanceServicePanel.tsx` | Invoices, payments, refunds, credit notes, reconciliation and service | `FinanceController`, payment gateway and reconciliation APIs |
| `NotificationInboxPanel.tsx` | User inbox, read state, preferences and delivery status | `UserNotificationController`, preference and operations APIs |
| `OfflineSyncPanel.tsx` | Offline operation submission and status | `OfflineSyncController` |
| `VendorAnalyticsPanel.tsx` | Vendor/branch/operations analytics and exports | `VendorAnalyticsController`, `BranchAnalyticsController`, exports |
| `PlatformAnalyticsPanel.tsx` | Platform aggregate analytics | `PlatformBusinessAnalyticsController` |
| `PlatformSettingsPanel.tsx` | Typed platform configuration | `PlatformSettingsController` |
| `Phase2AdminPanel.tsx` | Platform/vendor administration and operational settings | `Controller`, `EnterpriseSaasController`, role/menu configuration |
| `RecordEditor.tsx`, `PackageImageUpload.tsx` | Shared CRUD form and catalogue/media upload components | Catalogue/package/media APIs |
| `authenticatedFetch.ts` | Credentialed/CSRF-aware API calls and session refresh | Authentication/session APIs and all protected API calls |

Frontend tests are in `frontend/tests/` and browser-level suites are in `frontend/e2e/`; the exact files are enumerated by the branch tree: component tests cover the screen components above and E2E covers auth/session security, two-tenant filter isolation, tenant workspace switching, CMS and customer document flows.

## Complete V1 table inventory and later schema objects

Before cleanup, the original V1 migration declared 74 tables. The table list below records that original V1 table set (now included in the consolidated `V1__baseline.sql`) and maps to the corresponding Java domain/repository pair under `backend/src/main/java/com/saumyasolar/domain/` and `repo/` (or a shared aggregate in `AppService`/service packages).

- **Identity/tenant/security:** `tenant`, `permission`, `role`, `role_permission`, `app_user`, `user_role`, `refresh_token`, `password_reset_token`, `mfa_challenge`, `auth_rate_limit_bucket`, `audit_event`.
- **Vendor/SaaS:** `subscription_plan`, `tenant_subscription`, `subscription_usage`, `subscription_assignment_history`, `tenant_domain`, `tenant_storage_ledger`, `branch`, `branch_user`, `warehouse`.
- **Catalogue/inventory:** `product_category`, `product`, `solar_package`, `package_item`, `inventory_balance`, `inventory_reservation`, `inventory_transaction`.
- **Customer/CRM/sales:** `customer_profile`, `customer_address`, `enquiry`, `sales_lead`, `lead_history`, `site_survey`, `site_survey_history`, `site_survey_attachment`, `quotation`, `quotation_item`, `customer_order`, `order_item`, `order_status_history`, `idempotency_record`.
- **Installation/service/warranty:** `installation_team`, `installation_checklist_template`, `installation_job`, `installation_checklist_item`, `installation_schedule_history`, `engineer_profile`, `service_sla_policy`, `service_request`, `service_visit`, `service_event`, `service_attachment`, `service_comment`, `warranty`, `warranty_claim`, `warranty_claim_attachment`.
- **Finance:** `invoice_sequence`, `invoice`, `invoice_item`, `payment`, `payment_webhook_event`, `payment_refund`, `credit_note`.
- **Integrations/communications:** `integration_connection`, `integration_secret_reference`, `integration_webhook_event`, `integration_sync_job`, `notification_delivery`, `offline_sync_operation`, `offline_sync_attachment`.
- **Reporting/approvals/exports:** `export_job`, `approval_workflow`, `approval_instance`, `approval_decision`.

Later migrations added ten required tables: `order_transition_policy` (V3), `payment_reconciliation_report` and `payment_reconciliation_line` (V5), `cms_content` and `cms_content_revision` (V6), `user_notification` (V7), `platform_setting` (V10), `notification_outbox`, `notification_preference` and `notification_provider_event` (V12). V2–V14 also add required columns, checks, indexes, triggers, permission catalogue rows/grants and tenant-bound composite foreign keys. All ten tables and every required schema/seed effect are now included in the consolidated baseline, which contains 84 tables in total.

## Repository inventory scope

The Git tree API returned the following checked-in inventory on this branch (generated/dependency build outputs are not counted as source files):

| Area | Files |
|---|---:|
| Backend (Java, resources, tests, Maven descriptor, Dockerfile) | 353 |
| Frontend (source, tests, E2E, package/build config, public assets, Dockerfile) | 48 |
| Docs (excluding root-level source-of-truth/analysis docs counted separately) | 22 |
| GitHub workflows | 2 |
| Scripts | 5 |

All backend files are organized under `api/`, `config/`, `domain/`, `repo/`, `security/`, `service/`, `resources/`, and `test/`; the recursive tree inventory was reviewed. The 48 frontend files are grouped as `src/`, `tests/`, `e2e/`, and `public/`. The five scripts are the two original audit shell/Python pairs plus the new repository-wide external transport assertion. Root files additionally include Docker Compose, the three source documents, existing analysis matrices, and the development start script.

## Phase 0 usage-search findings and removal candidate

- API mapping search found only one multi-path compatibility alias: `GET /settings` and `PATCH /settings/discount-threshold` are aliases of `/tenant/settings` and `/tenant/settings/discount-threshold` in `Controller.java`.
- Frontend source search found the two alias paths called only from `frontend/src/main.tsx` (vendor settings load and save). No other controller, frontend component, E2E/component test, or source mapping was found in the targeted caller search.
- The documented SRS/HLD API catalogue §13 does not list either `/settings` alias; tenant/vendor configuration belongs to the vendor/tenant administration API group. Since there is no production deployment and the frontend is the only discovered caller, Phase 1 will switch this caller to the explicit tenant-scoped path and remove the alias mappings. This is a path cleanup only; the underlying settings data and behavior remain unchanged.
- No old DTO version or explicitly deprecated API version was positively identified by the recursive path inventory. No undocumented route is to be deleted solely because it is not listed in the SRS; those remain pending caller/requirement evidence.
