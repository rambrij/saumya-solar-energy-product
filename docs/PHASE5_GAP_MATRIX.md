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
| CMS-01 | Manage pages, banners, FAQs, testimonials and blogs | Tenant-scoped CMS entities, immutable revision snapshots, controller/service, V6 migration, authoring panel and public storefront routes now exist. | Complete lifecycle, tenant scope, audit, public published-only reads, safe upload URL validation and UI are implemented. Browser E2E exercises upload, publish, page and blog rendering; CI confirmation pending. | PARTIAL — initial implementation committed; full acceptance pending |
| CMS-02 | DRAFT → REVIEW → PUBLISHED → ARCHIVED | CMS DRAFT → REVIEW → PUBLISHED / ARCHIVED transitions are enforced in the service; publishing requires CMS_PUBLISH and invalid transitions return 409. | Lifecycle service and transition audit are implemented. Optimistic version checks and separate reviewer-role policy remain unimplemented; concurrent revision behavior still needs acceptance tests. | PARTIAL — initial implementation committed; full acceptance pending |
| CMS-03 | Immutable content versions and rollback | cms_content_revision, append-only DB triggers, tenant-qualified revision lookup and rollback-as-new-revision exist. | Implemented with V6 schema and immutability triggers; MySQL CI validation and concurrent-write E2E pending. | PARTIAL — initial implementation committed; full acceptance pending |
| CMS-04 | SEO, slug and social image | Typed content model includes SEO fields and social image URL; upload endpoint reuses existing image validation/storage. | Tenant/type/slug uniqueness, bounded SEO, HTTPS or safe /uploads references and plain-text/Markdown-only body validation implemented; arbitrary HTML rejected. | PARTIAL — initial implementation committed; full acceptance pending |
| CMS-05 | Tenant-scoped public content | Public CMS APIs resolve active store slug and return the published revision only; PublicHome renders published PAGE, BLOG, BANNER and TESTIMONIAL content. | Server-side tenant resolution and published-only filtering implemented. Scheduled publication windows are not modeled and remain out of scope pending SRS/product decision. | PARTIAL — initial implementation committed; full acceptance pending |
| SET-01 | Platform-wide configuration | Allow-listed PlatformSetting entity/repository/service/controller, V10 migration and PlatformSettingsPanel exist. Supported keys: default currency, time zone, language, support email/phone and platform banner message. No secret values or arbitrary keys. | GET /api/v1/platform/settings and PUT /api/v1/platform/settings/{key}; PLATFORM_SUPER_ADMIN + PLATFORM_MANAGE guard; typed validation, optimistic version, audit and safe defaults. Backend/UI tests added; MySQL CI pending. | DEFERRED — safe setting-key catalogue requires product/security approval |
| SET-02 | Tenant workspace settings | Tenant branding/contact fields and discount-threshold endpoint exist. | Inventory current fields first; expose only missing settings through existing tenant/profile service; do not duplicate branding or discount policy. | PARTIAL |
| DOC-01 | Customer document centre | Dedicated invoice, quotation, payment and service evidence flows exist; no unified customer document listing found. | Customer UI composes existing invoice, quotation, receipt and service evidence sources. No duplicate binary storage or new aggregate backend API. | PARTIAL |
| DOC-02 | Authorized document download | Private service attachments and PDF flows exist. | Existing authenticated owner-checked download endpoints are reused. Browser E2E tests that another customer cannot list or download an invoice. | PARTIAL |
| NOT-01 | In-app notification inbox | `NotificationService` and `notification_delivery` persist provider delivery attempts; no inbox/read-state entity/controller found. | Add separate user/tenant-owned inbox records; derive recipient from principal; paginated list, unread count, mark-one/read-all idempotently. | PARTIAL — initial implementation committed; full acceptance pending |
| NOT-02 | Notification templates/preferences | Delivery retry and customer messaging consent exist; no complete role-aware inbox/preferences centre established. | Reuse consent and delivery code; add only confirmed missing preferences/templates; provider failure must not roll back business events. | PARTIAL |
| ROLE-01 | Tenant custom role editor | `RoleManagementPanel.tsx`, `/roles`, `/permissions` and `AuthLifecycleService` custom-role operations exist. | Do not duplicate UI. Verify allowlist excludes platform permissions, unknown codes rejected, system roles protected, assigned-role deletion denied, and all changes/assignments audited with tenant-negative tests. | PARTIAL / EXISTING — existing editor reused; inbox grant added |
| RPT-01 | Vendor operational analytics | `VendorAnalyticsController/Service` and `VendorAnalyticsPanel.tsx` provide sales/lead/category/product views with tenant/date/branch filters. | /vendor/analytics/operations adds installation and service status, low stock, outstanding invoices and employee performance with tenant, date, branch and optional employee scope. | PARTIAL |
| RPT-02 | Platform aggregate analytics | `PlatformAnalyticsPanel.tsx` calls `/platform/analytics/tenant-usage`; usage is not full platform sales/order/subscription performance. | Add aggregate-only platform KPIs; platform permission required; no tenant customer PII; prevent double counting. | PARTIAL |
| RPT-03 | Report/export centre | Existing asynchronous ExportService supports OPERATIONS, SALES, INSTALLATIONS, SERVICE, INVENTORY and FINANCE reports in CSV/XLSX/PDF; private requester/tenant-scoped downloads and expiry remain in place. | Reuse `ExportService` and tenant-scoped export jobs; allow-listed report types, REPORT_EXPORT, expiry, audit and CSV/XLSX/PDF tests. | PARTIAL |

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
- Platform settings: GET /api/v1/platform/settings and allow-listed PUT /api/v1/platform/settings/{key} with expectedVersion; no arbitrary key/value or secret-value API.
- Customer documents: frontend aggregates existing authorized APIs/downloads; no new aggregate API or storage-key exposure.
- Analytics: add only missing report families; reuse existing filters and export jobs. Platform aggregates must not return customer PII.

## 4. Permission and audit reconciliation

The SRS already defines `CMS_VIEW`, `CMS_CREATE`, `CMS_UPDATE`, `CMS_PUBLISH`, `REPORT_VIEW`, `REPORT_EXPORT`, `ROLE_VIEW`, `ROLE_CREATE`, `ROLE_UPDATE` and `ROLE_ASSIGN`. Reconcile actual `DataSeeder` and migration state before adding codes. Candidate least-privilege codes, only if absent and justified by endpoint use: `CMS_REVIEW`, `PLATFORM_SETTINGS_VIEW`, `PLATFORM_SETTINGS_UPDATE`. A user's own inbox is self-scoped and must not require tenant-wide notification-management permission. Reuse existing customer/document permissions wherever possible.

Every privileged mutation records actor, tenant, action, resource, outcome and timestamp. Do not log message bodies, document contents, credentials or unnecessary PII.

## 5. Required tests and release evidence

- Clean MySQL 8.4 migration from V1 through V11; no backfill and no destructive compatibility path.
- CMS validation, unique slug, immutable revisions, transitions, rollback, published-only public visibility, tenant isolation and permission denial.
- Inbox recipient derivation, user/tenant isolation, unread count, mark-one/read-all idempotency, pagination, duplicate event handling and separation from provider delivery state.
- Settings platform-only access, allow-listed keys, invalid values, secret-reference masking, optimistic conflict and audit.
- Customer documents ownership, tenant isolation, private download authorization and no storage-key leakage.
- Role privilege escalation, unknown permissions, system-role mutation, assigned-role deletion, tenant isolation and complete audit trail.
- Analytics date bounds, branch/assignment scopes, aggregate-only platform output, export permission and cross-tenant denial.
- Frontend authoring lifecycle, preview/public visibility, inbox read state, customer documents, settings permissions, role-editor regression and analytics filters.
- Release workflow includes backend/frontend checks, V1–V11 MySQL migration validation, Spring Boot startup and Playwright browser tests; latest run must pass.

## 6. Implementation sequence

1. Reconcile permission seeding, role-service invariants, audit patterns and existing settings/document/export APIs.
2. Add additive schema and backend CMS + in-app inbox vertical slices with tests.
3. Add allow-listed platform settings using the existing secret abstraction and audit repository.
4. Add customer document aggregation by composing existing authorized services.
5. Expand vendor/platform analytics only for uncovered SRS report families; reuse export infrastructure.
6. Add/extend React screens and role navigation without duplicating menu logic.
7. Update OpenAPI/feature docs, gap analysis and cleanup decisions; run migration, tests, lint, typecheck and production build in CI.

**Audit conclusion:** Phase 5 has substantial committed slices, but is not complete until the latest full CI workflow passes and remaining role/review, CMS concurrency, notification isolation and document authorization assertions are accepted.

## 7. Initial Phase 5 implementation progress — 2026-11-11

The first implementation vertical slices are committed on this branch. This is an in-progress engineering checkpoint; it is not a Phase 5 completion declaration. Validation is pending the latest full CI run.

| Slice | Files / behavior committed | Current limitation / remaining acceptance |
|---|---|---|
| CMS persistence and lifecycle | `CmsContent`, immutable `CmsContentRevision`, tenant-qualified repositories, `CmsService`, `CmsController`; V6 migration. Supports PAGE/BANNER/FAQ/TESTIMONIAL/BLOG, DRAFT/REVIEW/PUBLISHED/ARCHIVED, draft edits, immutable revisions and rollback-as-new-revision, publish pointer preserving the previously published revision during edits, public published-only routes, slug uniqueness, safe relative/HTTPS social image references, HTML-tag rejection and audit events. | Public storefront now consumes published PAGE/BLOG/BANNER/TESTIMONIAL APIs; media upload and preview are implemented. Separate reviewer policy and full concurrent revision tests remain. Add integration tests for Flyway/JPA, lifecycle, concurrency and cross-tenant public reads; verify migration on MySQL CI. |
| In-app inbox | `UserNotification`, repository/service/controller; V7 inbox table; V8 `NOTIFICATION_VIEW` catalogue grant; notification event path creates a self-scoped inbox entry while retaining existing delivery attempts. List, unread count, mark one and mark all read are available. | Delivery and inbox persistence failure isolation, duplicate-event behavior, account-type tests and browser E2E need CI confirmation. The current implementation adds an inbox record for business notifications with an identified tenant user; authentication recovery/MFA messages remain outside the inbox. |
| CMS and inbox frontend | `CmsManagementPanel.tsx`, `NotificationInboxPanel.tsx`, vendor/customer navigation integration and component tests. | Public CMS routes and media preview now exist; browser E2E covers upload/publish/render. Inbox polling/badge remains optional UX work. |
| Customer documents | Customer workspace now composes existing invoice PDFs, quotation PDFs, payment receipts and service attachments into one view; uses existing authenticated owner-checked download endpoints. | This is a frontend aggregation, not a new backend document aggregation API. Warranty/installation documents should be added only where an existing authorized source is present. E2E ownership tests remain. |
| Platform analytics | New aggregate-only `/api/v1/platform/analytics/overview` endpoint and service for tenant lifecycle counts, order count/value by date range and subscription counts; platform super-admin + SUBSCRIPTION_MANAGE guard. Existing tenant-usage API remains. Platform analytics UI now combines both. | Aggregate API/UI exists; MySQL 8.4 and negative platform-role validation remain in CI. Order value is not recognized revenue. Metrics are counts/aggregate order value, not audited revenue recognition. |
| Roles | Existing editor and backend CRUD retained. `NOTIFICATION_VIEW` added to vendor custom-role allowlist and default system role grants. | Verify migration grants only intended system vendor roles and endpoint matrix; do not duplicate editor. |
| Platform settings | Typed allow-listed platform settings API, migration, audit, optimistic version and PlatformSettingsPanel are committed. Keys are deliberately finite and non-secret. | The current approved technical allowlist is documented above; adding further keys still requires product/security review. |

### Initial implementation commits

CMS, inbox, customer workspace, platform analytics, typed settings, operational analytics and multi-report exports are committed on this same branch. Migrations V6–V11 are additive. Use the branch commit history and the latest GitHub Actions run as the authoritative implementation and validation record.

### Known validation issues observed and corrective work

Earlier intermediate CI runs identified (a) the new inbox endpoints being accessible to a zero-permission vendor, (b) an ambiguous CMS test label selector, and (c) the release-validation migration-count assertion being hard-coded to five migrations. Corrective commits added `NOTIFICATION_VIEW` checks and role grants, narrowed the test selector, and extended clean-install migration validation to V6–V11 and added platform settings and operational analytics index assertions. These intermediate failed runs are not final acceptance; the latest full workflow must pass.


## 8. Remaining Phase 5 implementation and acceptance — 2026-11-11

### Implemented in the latest wave

- Platform settings: PlatformSetting entity/repository/service/controller, V10 migration and PlatformSettingsPanel. The finite allowlist is PLATFORM_DEFAULT_CURRENCY, PLATFORM_DEFAULT_TIME_ZONE, PLATFORM_DEFAULT_LANGUAGE, PLATFORM_SUPPORT_EMAIL, PLATFORM_SUPPORT_PHONE and PLATFORM_BANNER_MESSAGE. Only platform super-admins with PLATFORM_MANAGE can read/write. Values are typed and bounded; optimistic version is required; updates are audited. No secrets, provider credentials or arbitrary key/value storage are exposed.
- CMS storefront: public PublicHome renders published pages and blog detail routes, blog listing, banners and testimonials. CMS image upload reuses the existing JPEG/PNG/WebP validator/storage service; API returns safe app-relative /uploads/... paths and records an audit event. Editor includes local preview and upload preview.
- CMS E2E: Playwright test creates PAGE and BLOG content, uploads media, moves DRAFT → REVIEW → PUBLISHED, checks public APIs and renders both public routes. It verifies the uploaded image is served.
- Customer documents: Playwright test verifies a second customer cannot list or download another customer's invoice. UI continues to reuse invoice, quotation, receipt and service-attachment owner-checked endpoints; no duplicate document store is introduced.
- Vendor operational analytics: /api/v1/vendor/analytics/operations returns tenant-scoped installation/service status, low-stock products, outstanding invoices and employee performance. Filters include date range, branch and optional employee; foreign branches are rejected before SQL.
- Report/export centre: existing asynchronous export jobs now accept OPERATIONS, SALES, INSTALLATIONS, SERVICE, INVENTORY and FINANCE, with CSV section output, XLSX detail sheets and PDF tables. REPORT_EXPORT, requesting-user/tenant ownership, private storage and expiry remain enforced. Source projections contain only report fields needed for these report families.
- Migrations: V10 adds typed platform settings; V11 adds indexes for tenant/assignee/date operational reporting. Release validation applies V1–V11 on clean MySQL 8.4 and asserts the new schema/indexes.

### Remaining acceptance / not to be represented as complete yet

1. Latest full release-validation workflow must pass: backend Maven tests, frontend Vitest/typecheck/build, MySQL V1–V11 migration checks, Spring Boot/MySQL startup, V57/V58 preflight and Playwright E2E.
2. Validate operational report SQL and status/payment semantics against seeded MySQL fixtures. Confirm branch and employee filters and all export formats in browser tests.
3. Add/confirm explicit negative platform-settings API tests for PLATFORM_SUPPORT, vendor admins, stale version conflicts and audit persistence against MySQL (unit coverage exists).
4. Add full customer document E2E for quotation, payment receipt, service/warranty/installation attachments where seeded fixtures exist; invoice cross-customer denial is covered by the new test.
5. Add CMS concurrent revision/publication integration tests and decide whether CMS review must be a distinct permission/role; current REVIEW submission and CMS_PUBLISH gate are enforced.
6. Confirm no unsupported notification template/preferences requirements remain; inbox is distinct from delivery attempts and self-scoped.
7. Complete the final role × endpoint evidence for CMS, exports and settings; navigation visibility is not a substitute for backend authorization.

The most recent branch-head Actions run is the release gate; intermediate runs created by the per-file commits are not acceptance evidence.
