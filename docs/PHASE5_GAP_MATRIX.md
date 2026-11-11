# Phase 5 Gap Matrix — CMS, settings, customer workspace and reporting

**Date:** 2026-11-11  
**Branch:** `audit/phase-4-finance-provider-impl`  
**Baseline:** `d3a0f7656767d82a70f7f6dbedac5647d043aa31`  
**Phase 4 gate:** [GitHub Actions run 43025279559](https://github.com/rambrij/saumya-solar-energy-product/actions/runs/43025279559) concluded `success`; all four jobs completed successfully on this branch/head commit.

## 1. Audit method and guardrails

The audit read the business requirements, production SRS/HLD and current implementation gap analysis, then compared requirements with the branch-head repository tree and source. The SRS is authoritative. Reuse existing controllers, services, migrations, permission catalogue, audit facilities, notification delivery/retry, role editor, analytics, customer invoice/quotation/attachment and private-storage paths before adding abstractions.

- Preserve Java 21/Spring Boot 3, React/TypeScript, MySQL 8.4 and modular-monolith boundaries.
- Derive tenant scope only from the authenticated principal; never trust a client tenant ID.
- Backend authorization is mandatory; menu visibility is not authorization.
- No live payment/messaging provider, production data, backfill, compatibility shim or destructive migration.
- Additive migrations only, ordered after V5, tested on clean MySQL 8.4.
- Audit publishing, role/permission changes, platform setting changes and document access.
- No feature is complete until persistence, API, UI, tenant/RBAC tests, migrations and CI evidence pass.

## 2. Phase 5 traceability matrix

| ID | Requirement | Current evidence | Gap / acceptance | Status |
|---|---|---|---|---|
| CMS-01 | Manage pages, banners, FAQs, testimonials and blogs | No CMS domain/repository/service/controller/migration/authoring screen found; public home is partly static. | Add typed tenant-owned content, validated CRUD, editor UI and public read APIs. Public reads return PUBLISHED only. Cross-tenant guessed IDs return non-disclosing 404. | PARTIAL — initial implementation committed; full acceptance pending |
| CMS-02 | DRAFT → REVIEW → PUBLISHED → ARCHIVED | No CMS workflow found; existing order approval is not a CMS workflow. | Enforce explicit transitions and publish permission in service; optimistic version check; audit transitions; invalid transitions return 409. | PARTIAL — initial implementation committed; full acceptance pending |
| CMS-03 | Immutable content versions and rollback | No CMS revision/version table found. | Each save creates immutable revision; rollback creates a new revision; never mutate old published snapshots. | PARTIAL — initial implementation committed; full acceptance pending |
| CMS-04 | SEO, slug and social image | No CMS content model found. | Unique tenant/type/slug; normalized slug; bounded SEO fields; safe media references; sanitized rich content. | PARTIAL — initial implementation committed; full acceptance pending |
| CMS-05 | Tenant-scoped public content | Existing public product/package/enquiry APIs resolve store slug and active tenant; no public CMS API found. | Resolve slug server-side; filter tenant + PUBLISHED + publication window; never accept tenant ID. | PARTIAL — initial implementation committed; full acceptance pending |
| SET-01 | Platform-wide configuration | Tenant branding/contact fields and platform administration exist; no general platform settings domain/table/API/UI identified. | Add allow-listed typed settings, platform-super-admin-only writes, versioning and audit. Secrets remain references in secret-provider; never return raw values. | DEFERRED — safe setting-key catalogue requires product/security approval |
| SET-02 | Tenant workspace settings | Tenant branding/contact fields and discount-threshold endpoint exist. | Inventory current fields first; expose only missing settings through existing tenant/profile service; do not duplicate branding or discount policy. | PARTIAL |
| DOC-01 | Customer document centre | Dedicated invoice, quotation, payment and service evidence flows exist; no unified customer document listing found. | Build read-only aggregation over existing authorized sources; do not copy binaries or bypass owner checks. | PARTIAL |
| DOC-02 | Authorized document download | Private service attachments and PDF flows exist. | Reuse existing authorized download services; no storage keys in API; test guessed IDs, cross-customer and cross-tenant denial. | PARTIAL |
| NOT-01 | In-app notification inbox | `NotificationService` and `notification_delivery` persist provider delivery attempts; no inbox/read-state entity/controller found. | Add separate user/tenant-owned inbox records; derive recipient from principal; paginated list, unread count, mark-one/read-all idempotently. | PARTIAL — initial implementation committed; full acceptance pending |
| NOT-02 | Notification templates/preferences | Delivery retry and customer messaging consent exist; no complete role-aware inbox/preferences centre established. | Reuse consent and delivery code; add only confirmed missing preferences/templates; provider failure must not roll back business events. | PARTIAL |
| ROLE-01 | Tenant custom role editor | `RoleManagementPanel.tsx`, `/roles`, `/permissions` and `AuthLifecycleService` custom-role operations exist. | Do not duplicate UI. Verify allowlist excludes platform permissions, unknown codes rejected, system roles protected, assigned-role deletion denied, and all changes/assignments audited with tenant-negative tests. | PARTIAL / EXISTING — existing editor reused; inbox grant added |
| RPT-01 | Vendor operational analytics | `VendorAnalyticsController/Service` and `VendorAnalyticsPanel.tsx` provide sales/lead/category/product views with tenant/date/branch filters. | Missing SRS coverage includes installation, service, stock/low stock, outstanding payments and employee performance. Add only absent slices with branch/assignment scopes and existing export service. | PARTIAL |
| RPT-02 | Platform aggregate analytics | `PlatformAnalyticsPanel.tsx` calls `/platform/analytics/tenant-usage`; usage is not full platform sales/order/subscription performance. | Add aggregate-only platform KPIs; platform permission required; no tenant customer PII; prevent double counting. | PARTIAL |
| RPT-03 | Report/export centre | Platform exports and operational report endpoints exist; unified vendor report centre/comprehensive vendor exports not evidenced. | Reuse `ExportService` and tenant-scoped export jobs; allow-listed report types, REPORT_EXPORT, expiry, audit and CSV/XLSX/PDF tests. | PARTIAL |

## 3. API and data contracts (implemented slices plus remaining target)

### CMS
- Vendor authoring: `GET/POST /api/v1/cms/content`, `GET/PATCH /api/v1/cms/content/{id}`, `POST /api/v1/cms/content/{id}/transition`, `GET /api/v1/cms/content/{id}/versions`, `POST /api/v1/cms/content/{id}/rollback`.
- Public read: `GET /api/v1/public/{storeSlug}/content/{type}` and `GET /api/v1/public/{storeSlug}/content/{type}/{slug}`.
- Additive schema: `cms_content` (tenant, type, slug, status, current revision, publication timestamps, actor/timestamps, version) and `cms_content_revision` (tenant, content ID, revision number, immutable content/SEO/media snapshot, creator/time). Require composite tenant FKs and unique tenant/type/slug and tenant/content/revision constraints.

### In-app notifications
- `GET /api/v1/notifications` (bounded page/size and unread filter), `GET /api/v1/notifications/unread-count`, `PATCH /api/v1/notifications/{id}/read`, `POST /api/v1/notifications/read-all`.
- Add `user_notification` distinct from `notification_delivery`: tenant, recipient user, event type, title/body, safe target type/ID, created/read timestamps and dedupe key. Tenant may be null only for explicitly platform-scoped notifications.
- Delivery attempts remain in `notification_delivery`; an inbox is a user-facing projection. Never expose provider recipient data or credentials.

### Settings, documents and analytics
- Platform settings: `GET /api/v1/platform/settings` and allow-listed `PATCH /api/v1/platform/settings/{key}`; no arbitrary key/value or secret-value API.
- Customer documents: `GET /api/v1/customer/documents` aggregates existing authorized sources; do not bypass source authorization or return storage keys.
- Analytics: add only missing report families; reuse existing filters and export jobs. Platform aggregates must not return customer PII.

## 4. Permission and audit reconciliation

The SRS already defines `CMS_VIEW`, `CMS_CREATE`, `CMS_UPDATE`, `CMS_PUBLISH`, `REPORT_VIEW`, `REPORT_EXPORT`, `ROLE_VIEW`, `ROLE_CREATE`, `ROLE_UPDATE` and `ROLE_ASSIGN`. Reconcile actual `DataSeeder` and migration state before adding codes. Candidate least-privilege codes, only if absent and justified by endpoint use: `CMS_REVIEW`, `PLATFORM_SETTINGS_VIEW`, `PLATFORM_SETTINGS_UPDATE`. A user's own inbox is self-scoped and must not require tenant-wide notification-management permission. Reuse existing customer/document permissions wherever possible.

Every privileged mutation records actor, tenant, action, resource, outcome and timestamp. Do not log message bodies, document contents, credentials or unnecessary PII.

## 5. Required tests and release evidence

- Clean MySQL 8.4 migration from V1 through the new Phase 5 migration; no backfill and no destructive compatibility path.
- CMS validation, unique slug, immutable revisions, transitions, rollback, published-only public visibility, tenant isolation and permission denial.
- Inbox recipient derivation, user/tenant isolation, unread count, mark-one/read-all idempotency, pagination, duplicate event handling and separation from provider delivery state.
- Settings platform-only access, allow-listed keys, invalid values, secret-reference masking, optimistic conflict and audit.
- Customer documents ownership, tenant isolation, private download authorization and no storage-key leakage.
- Role privilege escalation, unknown permissions, system-role mutation, assigned-role deletion, tenant isolation and complete audit trail.
- Analytics date bounds, branch/assignment scopes, aggregate-only platform output, export permission and cross-tenant denial.
- Frontend authoring lifecycle, preview/public visibility, inbox read state, customer documents, settings permissions, role-editor regression and analytics filters.
- Run complete release-validation workflow on this same branch; a successful build alone does not complete acceptance.

## 6. Implementation sequence

1. Reconcile permission seeding, role-service invariants, audit patterns and existing settings/document/export APIs.
2. Add additive schema and backend CMS + in-app inbox vertical slices with tests.
3. Add allow-listed platform settings using the existing secret abstraction and audit repository.
4. Add customer document aggregation by composing existing authorized services.
5. Expand vendor/platform analytics only for uncovered SRS report families; reuse export infrastructure.
6. Add/extend React screens and role navigation without duplicating menu logic.
7. Update OpenAPI/feature docs, gap analysis and cleanup decisions; run migration, tests, lint, typecheck and production build in CI.

**Audit conclusion:** Phase 5 is not implemented. CMS/content versioning and the in-app inbox/read state are the clearest absent slices. Role management, delivery, customer documents, settings and analytics have reusable foundations and must be extended rather than replaced.

## 7. Initial Phase 5 implementation progress — 2026-11-11

The first implementation vertical slices are committed on this branch. This is an in-progress engineering checkpoint; it is not a Phase 5 completion declaration. Validation is pending the latest full CI run.

| Slice | Files / behavior committed | Current limitation / remaining acceptance |
|---|---|---|
| CMS persistence and lifecycle | `CmsContent`, immutable `CmsContentRevision`, tenant-qualified repositories, `CmsService`, `CmsController`; V6 migration. Supports PAGE/BANNER/FAQ/TESTIMONIAL/BLOG, DRAFT/REVIEW/PUBLISHED/ARCHIVED, draft edits, immutable revisions and rollback-as-new-revision, publish pointer preserving the previously published revision during edits, public published-only routes, slug uniqueness, safe relative/HTTPS social image references, HTML-tag rejection and audit events. | Public storefront screens still need to consume CMS APIs; no media upload workflow/preview or review separation policy yet. Add integration tests for Flyway/JPA, lifecycle, concurrency and cross-tenant public reads; verify migration on MySQL CI. |
| In-app inbox | `UserNotification`, repository/service/controller; V7 inbox table; V8 `NOTIFICATION_VIEW` catalogue grant; notification event path creates a self-scoped inbox entry while retaining existing delivery attempts. List, unread count, mark one and mark all read are available. | Delivery and inbox persistence failure isolation, duplicate-event behavior, account-type tests and browser E2E need CI confirmation. The current implementation adds an inbox record for business notifications with an identified tenant user; authentication recovery/MFA messages remain outside the inbox. |
| CMS and inbox frontend | `CmsManagementPanel.tsx`, `NotificationInboxPanel.tsx`, vendor/customer navigation integration and component tests. | Verify build/tests; improve public-site CMS rendering and inbox polling/badge if product requires. |
| Customer documents | Customer workspace now composes existing invoice PDFs, quotation PDFs, payment receipts and service attachments into one view; uses existing authenticated owner-checked download endpoints. | This is a frontend aggregation, not a new backend document aggregation API. Warranty/installation documents should be added only where an existing authorized source is present. E2E ownership tests remain. |
| Platform analytics | New aggregate-only `/api/v1/platform/analytics/overview` endpoint and service for tenant lifecycle counts, order count/value by date range and subscription counts; platform super-admin + SUBSCRIPTION_MANAGE guard. Existing tenant-usage API remains. Platform analytics UI now combines both. | CI must validate SQL against MySQL 8.4; add export centre/report families (service, inventory, installations, outstanding payments) and negative platform role tests. Metrics are counts/aggregate order value, not audited revenue recognition. |
| Roles | Existing editor and backend CRUD retained. `NOTIFICATION_VIEW` added to vendor custom-role allowlist and default system role grants. | Verify migration grants only intended system vendor roles and endpoint matrix; do not duplicate editor. |
| Platform settings | Existing platform settings placeholder remains; no new arbitrary key/value or secret API added. | Safe setting keys, typed schema and product ownership decisions are still open; implementation intentionally deferred pending approval. |

### Initial implementation commits

CMS and inbox backend/UI, migrations V6–V9, aggregate platform analytics, tests, and CI migration updates are committed on this same branch. Use the branch commit history and the latest GitHub Actions run as the authoritative implementation and validation record.

### Known validation issues observed and corrective work

Earlier intermediate CI runs identified (a) the new inbox endpoints being accessible to a zero-permission vendor, (b) an ambiguous CMS test label selector, and (c) the release-validation migration-count assertion being hard-coded to five migrations. Corrective commits added `NOTIFICATION_VIEW` checks and role grants, narrowed the test selector, and extended clean-install migration validation to V6–V9. These intermediate failed runs are not final acceptance; the latest full workflow must pass.
