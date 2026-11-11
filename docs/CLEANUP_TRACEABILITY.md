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

The configured migration system is Flyway at `backend/src/main/resources/db/migration`. The branch tree contains exactly the following 14 versioned SQL migrations at this audit checkpoint:

| Migration | Current role recorded by migration name / prior decisions |
|---|---|
| V1__baseline.sql | Initial application schema and reference data |
| V2__phase3_reservation_expiry.sql | Reservation expiry |
| V3__phase3_order_policy_and_tenant_lifecycle.sql | Order transition policy and tenant lifecycle |
| V4__phase3_order_policy_permission_grant.sql | Order-policy permission grant |
| V5__phase4_finance_provider_reconciliation.sql | Payment provider-neutral state, refund and reconciliation schema |
| V6__phase5_cms_content_revisions.sql | CMS content and immutable revisions |
| V7__phase5_user_notification_inbox.sql | User notification inbox |
| V8__phase5_notification_view_permission.sql | Notification-view permission catalogue/grants |
| V9__phase5_platform_analytics_indexes.sql | Platform analytics indexes |
| V10__phase5_typed_platform_settings.sql | Typed platform settings |
| V11__phase5_operational_analytics_indexes.sql | Operational analytics indexes |
| V12__phase6_notification_outbox_preferences_provider_events.sql | Notification outbox, preferences and provider events |
| V13__phase6_provider_accepted_delivery_status.sql | Provider-accepted delivery status |
| V14__phase6_enforce_tenant_bound_notification_references.sql | Tenant-bound notification foreign keys |

This is a source-tree inventory, not a schema equivalence result. V1 already exists as a large baseline, but V2–V14 carry required later functionality. Consolidation is not authorized until the old migration chain and the proposed single baseline are both applied to clean MySQL 8.4 databases and their normalized schema dumps are diffed. Flyway history-table handling and clean-database-only scope must be documented. No existing migration is deleted in Phase 0.

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
