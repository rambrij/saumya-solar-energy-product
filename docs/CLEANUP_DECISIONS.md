# Cleanup and implementation decisions

## Phase 4 — Finance and payment-provider decisions

**Date:** 2026-11-11  
**Branch:** `audit/phase-4-finance-provider-impl`

### Decisions fixed by the Phase 4 implementation brief

1. No live payment provider may be contacted. Do not add Razorpay, Stripe, PayU, or another provider SDK, API integration, or credential.
2. Implement provider-neutral contracts and a deterministic mock adapter only. The mock must be selected through configuration and registered through the payment gateway registry.
3. Mock checkout, webhook simulators, refund simulators, and settlement fixtures must be clearly identified and available only in development/test profiles. They must not be enabled in production profiles.
4. Provider-specific behavior must remain behind the adapter boundary. Core finance logic must own tenant-scoped invoice/payment state, audit history, idempotency, and reconciliation outcomes.
5. Browser redirect/checkout-result parameters are not proof of payment. Only a verified, idempotently processed webhook/provider status may confirm payment.
6. Preserve issued invoice snapshots. Do not edit an issued invoice in place; use the existing void/reissue or credit-note workflow.
7. Do not claim statutory GST compliance or production payment readiness until the accounting and provider decisions below are approved.

### Open Questions

The following are intentionally unresolved. Keep the existing behavior and make the relevant rules configurable where the architecture safely permits; do not invent statutory or commercial policy.

| ID | Open question | Required decision owner | Implementation consequence |
|---|---|---|---|
| FIN-OQ-01 | Which GST tax classifications, rates, exemptions and effective-date rules are approved for each product/service category? | Business owner and qualified tax/accounting advisor | Do not hard-code unapproved tax treatment; retain explicit tax snapshots and configurable rule inputs. |
| FIN-OQ-02 | What is the approved place-of-supply determination and when should CGST/SGST versus IGST apply? | Qualified tax/accounting advisor | Do not infer place of supply from incomplete address data. |
| FIN-OQ-03 | Are reverse-charge cases in scope, and what invoice fields/accounting treatment are required? | Qualified tax/accounting advisor | Leave reverse-charge behavior disabled until approved. |
| FIN-OQ-04 | Is e-invoicing/IRN required for this business and which turnover/entity thresholds and workflows apply? | Business owner and qualified tax/accounting advisor | No IRN generation or statutory submission is included in this mock-only phase. |
| FIN-OQ-05 | What is the legally approved invoice series format, fiscal-year boundary/time zone, reset policy, branch scope and concurrency behavior? | Business owner and accounting | Keep numbering configurable; do not represent a default format as legally approved. |
| FIN-OQ-06 | Which payment provider, merchant account/entity and supported payment methods will be selected? | Business/product owner | Real-provider integration remains pending provider selection; mock adapter only. |
| FIN-OQ-07 | What settlement model, settlement schedule, fees, taxes, chargebacks, reserves and bank-reference fields must be reconciled? | Finance/accounting and selected provider | Implement generic reconciliation evidence and mismatch classification only; do not invent provider settlement semantics. |
| FIN-OQ-08 | What is the approved refund policy, including partial refunds, approval thresholds, time limits and cancellation interaction? | Business owner and accounting | Retain existing authorization/reason controls; provider refund initiation is mock-only until policy is approved. |
| FIN-OQ-09 | Which roles may initiate, approve and execute refunds, and is maker-checker approval required above a threshold? | Business/security owner | Do not broaden existing finance permissions or invent approval thresholds. |
| FIN-OQ-10 | What retention, privacy, and evidence requirements apply to invoice snapshots, provider events, reconciliation files and refund events? | Legal/privacy and accounting | Preserve auditable references; final retention/deletion policy requires approval. |


## Verification limitation

This record is a decision log, not evidence that Phase 4 code has been implemented or tested. The connected GitHub account has repository push/admin permissions and the GitHub API exposed the branch tree and source files. A local `git clone` attempt still failed because this execution environment could not resolve `github.com`; no local Maven/npm command execution is available through the GitHub connector. Runtime code and tests were changed through individual GitHub commits and verified through the repository's GitHub Actions workflow. Runtime implementation commit `24c1e88` and subsequent documentation-only commit `e6de31a` passed all four jobs: 238 backend tests, 24 frontend tests, MySQL 8.4 V5 migration, Spring Boot startup/schema validation, endpoint authorization evidence and browser-level tenant filter tampering. The final follow-up commit changes documentation only; no runtime code changed after the verified implementation.


## Phase 4 implementation decisions

- The mock provider is selected only when `app.payment.mock.enabled=true` and the active profile is `dev` or `test`. Mock simulator and settlement-fixture controllers are both profile- and property-gated.
- The browser return URL identifies a payment only; authenticated payment API state is authoritative. Only HMAC-verified webhook events can move a pending payment or refund into a terminal state.
- Tenant identity is part of the signed provider event payload and part of the webhook idempotency key. An unmatched or mismatched event is retryable and does not create a processed idempotency record.
- Provider refunds reserve the outstanding amount while PENDING. A verified refund webhook records PROCESSED or FAILED and recalculates the payment/invoice balance.
- Settlement report reference plus payload hash makes an identical replay idempotent and a changed payload under the same reference a conflict. Every line outcome is retained; mismatches are not auto-corrected.
- V1 remains the existing Phase 3 baseline. Phase 4 schema changes are in V5; no data backfill or compatibility shim is added.
- Existing invoice tax snapshots and tenant/fiscal-year sequence behavior are retained until the business/accounting decisions in FIN-OQ-01 through FIN-OQ-05 are approved.
- Real-provider integration remains **pending provider selection; adapter and mock complete**. No live provider SDK, credentials, or network integration is present.


## Phase 5 — CMS, customer workspace and reporting decisions

**Date:** 2026-11-11  
**Branch:** `audit/phase-4-finance-provider-impl`

### Scope and architecture decisions

1. Keep the existing Java/Spring modular monolith, React/TypeScript frontend and MySQL schema. Do not introduce a separate CMS service or new provider SDK.
2. CMS content is tenant-owned. Public routes resolve the tenant from a validated store slug and return published content only. Draft/review content is never public.
3. CMS revisions are immutable snapshots. Rollback creates a new revision; it never edits history. Content status changes require explicit policy, authorization, optimistic concurrency and audit.
4. In-app notifications are a user-facing inbox projection, separate from `notification_delivery` provider attempts. The recipient is derived from the authenticated principal. Read state is per user and tenant-scoped.
5. Customer documents aggregate existing authorized invoice, quotation, receipt and private attachment records. Do not copy binary objects, expose storage keys or weaken source download authorization.
6. Extend the existing custom-role editor and service. Do not create another role editor. Vendor custom roles cannot receive platform permissions; system roles remain immutable; assigned roles cannot be deleted.
7. Platform settings are allow-listed and typed. Secret values remain in the existing secret-provider mechanism; settings store references only and never return raw secrets.
8. Extend existing vendor/platform analytics and `ExportService` only for report families not already covered. Platform metrics must be aggregate-only and must not disclose customer PII.
9. Additive migrations only after V5. No production data, backfill, destructive change, or compatibility shim is authorized.
10. No feature is complete without migration validation, tenant/RBAC negative tests, API/UI tests, audit coverage and full CI evidence.

### Open decisions requiring product/security approval

- Whether CMS review must be a two-person approval or whether a single authorized publisher may move REVIEW to PUBLISHED.
- Which content blocks/rich-text format and image storage constraints are approved; sanitize all rendered content.
- Which notification categories are user-configurable and which events are mandatory/security-critical.
- Retention and deletion periods for inbox notifications and immutable CMS revisions.
- Exact platform-wide settings that are safe to expose as typed values; secret ownership/rotation remains outside settings UI.
- Which additional vendor report families and KPIs are required, and which roles may export them.

### Phase 4 gate evidence

GitHub Actions run [43025279559](https://github.com/rambrij/saumya-solar-energy-product/actions/runs/43025279559) ran on `audit/phase-4-finance-provider-impl` at `d3a0f7656767d82a70f7f6dbedac5647d043aa31` and concluded `success`. All four jobs completed successfully, including backend tests, frontend tests/typecheck/build, MySQL 8.4 migration validation including V5, Spring Boot startup and browser tenant-filter tampering checks. This is branch CI verification, not production deployment or merge-to-main sign-off.


### Phase 5 implementation decisions — initial vertical slice

1. CMS body is stored as plain text/Markdown, not trusted HTML. Raw HTML tags are rejected by the service; the public API returns text fields only. A future rich-text editor requires an approved sanitizer/allowlist before HTML rendering.
2. CMS revision rows are append-only. Updating a published item creates a draft revision while the published revision pointer continues serving the last published snapshot. Type/slug changes are rejected while a published snapshot is live; archive then create a new item for a new public identity.
3. CMS public routes resolve the tenant from an ACTIVE store slug and read only a published revision. Authoring routes derive tenant from the authenticated vendor principal. Every mutation writes an audit event.
4. Inbox records are separate from email/SMS/WhatsApp delivery attempts. The business notification path attempts an independent inbox write; provider delivery and existing retry behavior remain intact. Users can read only their own tenant inbox; vendor inbox routes require `NOTIFICATION_VIEW`, while customer/platform self-inbox scope does not expose other recipients.
5. `NOTIFICATION_VIEW` is a least-privilege self-inbox grant, not a tenant-wide notification administration permission. V8 installs the catalogue code and grants it only to known built-in vendor roles; custom roles may opt in through the existing allowlisted role editor.
6. Platform analytics returns aggregate counts/value only; it never returns tenant/customer rows. Order value is labelled as order value and must not be interpreted as recognized revenue.
7. Customer document centre composes existing authenticated download routes; no duplicate document metadata or binary storage is created in this slice.
8. Platform settings are not made arbitrary key/value storage. Implementation waits for approval of the exact typed non-secret setting keys; secret values remain in the secret provider.

### Phase 5 migrations

- V6: tenant-scoped CMS content and immutable revisions with composite tenant foreign keys.
- V7: per-user in-app notification inbox table, separate from notification delivery.
- V8: least-privilege `NOTIFICATION_VIEW` permission catalogue and built-in vendor role grants.
- V9: indexes for date-filtered cross-tenant order aggregates and subscription status counts.

All are additive; no production data backfill or destructive migration is intended. The release-validation workflow was extended to assert V1–V8 on a clean MySQL 8.4 database.


### Phase 5 remaining-scope implementation decisions

9. Platform settings use a finite allowlist rather than generic configuration storage. V10 stores typed non-secret values only: platform default currency/time zone/language, support email/phone and a public banner message. Writes require PLATFORM_SUPER_ADMIN plus PLATFORM_MANAGE, expectedVersion and an audit event. Tenant branding, tax, invoice numbering and discount thresholds remain in existing tenant settings. Provider secrets remain in the secret provider.
10. CMS media reuses PackageImageStorageService's JPEG/PNG/WebP validation and private file-write mechanics for intentionally public website media. The CMS API returns only a normalized app-relative /uploads path; CMS body remains plain text/Markdown and rejects HTML. The public site renders CMS text as text, never as injected HTML.
11. CMS public routes resolve the active tenant from storeSlug and expose only the published revision. Public PAGE and BLOG routes, blog listing, banners and testimonials are now connected to the React storefront. No scheduled publication window was added because the current CMS schema has no scheduling model.
12. Vendor operational analytics is a new tenant-scoped report projection, not a replacement for the existing sales analytics. It uses the authenticated tenant, validates branch ownership, filters assigned engineer IDs within tenant-owned rows, and returns only aggregates/report fields. V11 adds indexes for the date/assignee predicates.
13. Asynchronous exports reuse ExportJob, ExportService, private object storage, REPORT_EXPORT, owner checks and expiry. Supported report types are OPERATIONS, SALES, INSTALLATIONS, SERVICE, INVENTORY and FINANCE. CSV renders detail sections; XLSX renders summary and detail sheets; PDF renders summary and detail tables. The source report projections exclude customer contact/address fields.
14. Customer documents remain a frontend aggregation over existing invoice, quotation, payment receipt and service evidence routes. Do not introduce a second file registry or duplicate binary store. Browser E2E explicitly checks cross-customer invoice list/download denial; remaining document families need fixtures and evidence.
15. The release workflow validates V1–V11 on clean MySQL 8.4, starts Spring Boot against MySQL and runs the Playwright browser suite. A queued or in-progress run is not acceptance evidence; only the latest branch-head workflow with all jobs successful can close Phase 5.

### Phase 5 schema additions

- V10: typed allow-listed platform_setting table with optimistic version, actor and timestamp.
- V11: tenant/assignee/date indexes used by installation, service, invoice and order reporting.

No production data backfill, destructive migration or live provider integration was introduced.


## Phase 6 — Notifications and integrations audit

**Date:** 2026-11-11  
**Branch:** `audit/phase-6-notifications-integrations`  
**Base:** `audit/phase-4-finance-provider-impl`

### Requirements traced to the source documents

- Business requirements §15 require centralized events for quotation creation/acceptance, order confirmation, payment receipt, installation scheduling/completion, service-ticket creation/resolution and invoice generation. Supported channels are email, SMS, WhatsApp and in-app; provider implementations must be replaceable behind interfaces.
- SRS/HLD §10.12 (NOT-001–NOT-006) requires event-driven notifications, in-app/email plus pluggable SMS/WhatsApp, tenant-configurable templates with platform defaults, asynchronous processing, persisted attempts/provider references/failures/retry counts, and isolation of provider failures from completed business transactions.
- SRS/HLD §11.3–11.4 defines vendor notification-template and platform provider-configuration/job-monitoring screens.
- SRS/HLD §16.5 and §17 require observable background jobs, provider-failure/backlog alerting, ports/adapters, bounded retries, circuit breaking and dead-letter/manual-retry handling.
- SRS/HLD §18 requires notification delivery/retry and SLA breach evaluation as tenant-aware, idempotent background jobs.
- SRS/HLD §25 requires authorization, tenant tests, unit/integration/E2E tests, audit/logging/metrics and migration validation before completion.

### Branch-head audit — existing implementation

| Requirement | Status | Evidence observed on base branch |
|---|---|---|
| Persisted notification delivery attempts | PARTIAL | `backend/src/main/java/com/saumyasolar/domain/NotificationDelivery.java`; `NotificationDeliveryRepository.java`; `NotificationService.java` |
| Separate user inbox and read state | EXISTS | `domain/UserNotification.java`; `repo/UserNotificationRepository.java`; `service/UserNotificationService.java`; Phase 5 V7/V8 decisions |
| Transactional outbox and atomic enqueue with business transaction | MISSING / NOT PROVEN | Existing delivery persistence exists, but the inspected business notification path writes the inbox in REQUIRES_NEW and queues channel delivery separately; no dedicated outbox record or transactional outbox boundary was found |
| Integration job persistence and scheduler | PARTIAL | `domain/IntegrationSyncJob.java`; `service/IntegrationSyncScheduler.java`; tenant-aware claim/reload and retry/dead-letter states exist |
| Notification provider ports/adapters | MISSING | `NotificationService.java` contains provider-specific SMTP/Twilio delivery logic directly rather than using channel adapter interfaces |
| Mock-only notification providers and profile/config selection | MISSING / BLOCKING | The inspected notification service contains direct SMTP and Twilio HTTP calls; this does not satisfy the Phase 6 no-real-provider requirement |
| Bounded notification retry and terminal state | PARTIAL | Existing retry uses attempt count and exponential delay, with a five-attempt check; no explicit DEAD_LETTER status is set for exhausted notification rows |
| Integration retry and dead-letter state | PARTIAL | Integration scheduler has RETRY/DEAD_LETTER and circuit-breaker handling; max attempts and operational monitoring need verification |
| Notification preferences and consent | PARTIAL | Customer profile has SMS/WhatsApp consent flags; no complete per-user/per-channel preference model/API/UI was established |
| Provider delivery webhooks and provider event ledger | MISSING / NOT PROVEN | Payment webhook persistence is not evidence of notification delivery callbacks; no notification provider-event/inbox webhook path was found in inspected files |
| Retry and integration monitoring APIs/admin UI | MISSING / NOT PROVEN | SRS defines platform provider configuration and job/integration monitoring screens; corresponding Phase 6 monitoring surface was not established by this audit |
| SLA escalation notifications | PARTIAL / UNDEFINED | Existing service SLA breach evaluation/scheduler is present from Phase 2; notification escalation rules, thresholds and targets are not specified in the cited SRS requirements |
| Frontend notification preferences and delivery status | MISSING / PARTIAL | Phase 5 inbox is present; preference management and channel-delivery status surfaces were not established by this audit |
| Tenant isolation and authorization evidence | PARTIAL | Existing notification repositories/services carry tenant/user predicates; Phase 6-specific negative tests for preferences, delivery status, retries and webhook tenant binding are not present in the inspected evidence |

### Decisions for this branch

1. Reuse `NotificationDelivery`, `UserNotification`, `IntegrationSyncJob`, `IntegrationSyncScheduler` and the existing Phase 5 inbox. Do not introduce parallel inbox, integration-job or notification-delivery aggregates.
2. Keep platform password-reset/MFA delivery distinct from tenant business notifications. Never expose recovery/MFA content through vendor monitoring or customer notification preferences.
3. The business notification write must ultimately be transactionally coupled to an outbox insert. Dispatch must occur after commit; provider failure must not roll back the business transaction.
4. Provider-specific network behavior must be moved behind channel ports. This Phase 6 implementation must select deterministic mock adapters only, with explicit dev/test configuration and production-profile exclusion. Do not add provider credentials or real SDK/network calls.
5. Provider callbacks must be signature-verified and idempotently recorded as provider events before updating delivery status. Raw secrets, message bodies, phone numbers, email addresses and callback payload PII must not be logged.
6. Reuse existing tenant execution context and identity-derived tenant scope for every scheduler, API, query and webhook-to-delivery association.
7. Do not infer escalation policy, mandatory notification categories, consent defaults, retention periods or provider callback contracts. Preserve existing behavior until product/security decisions are recorded below.
8. No legacy shim or data backfill is authorized. Correct the schema directly in new forward migrations; do not edit applied migrations.

### Open Questions

| ID | Open question | Decision owner | Why implementation cannot safely guess |
|---|---|---|---|
| NOT-OQ-01 | What are the notification SLA thresholds by event/priority, when does the clock start/pause, and what constitutes breach? | Product/operations owner | SRS defines configurable service SLA targets by priority but does not define notification escalation thresholds or timing semantics |
| NOT-OQ-02 | Who receives each escalation (role, named user, branch manager, tenant admin, platform operations), and what is the escalation sequence/cadence? | Product/operations owner | No escalation target matrix or escalation ladder is specified |
| NOT-OQ-03 | Which event categories are mandatory/security-critical and cannot be disabled, and which can users opt out of? | Product/security/privacy owner | Phase 5 notes already identify configurable-vs-mandatory categories as unresolved |
| NOT-OQ-04 | Is consent required separately for email, SMS and WhatsApp; what evidence/source/timestamp/version is retained; how are withdrawals and re-consent handled? | Legal/privacy owner | Existing SMS/WhatsApp profile flags do not define complete consent lifecycle or email consent |
| NOT-OQ-05 | Which provider-specific callback event types, signature headers, canonical payload rules, timestamp tolerance and key-rotation process should the mock contract model? | Integration/security owner | No real provider has been selected; do not imitate an undocumented provider contract |
| NOT-OQ-06 | What notification retention/deletion period applies to outbox, inbox, provider events, delivery metadata and failed payloads? | Legal/privacy/security owner | SRS does not specify retention periods; message content can contain personal data |
| NOT-OQ-07 | Should platform administrators see tenant-level delivery metadata only, or may they inspect message content? | Product/security/privacy owner | Access scope for cross-tenant operations monitoring is not defined |
| NOT-OQ-08 | What are the approved retry max-attempt count, base delay, multiplier, cap, jitter and manual-redrive permissions for each channel/job class? | Operations owner | Current notification retry and integration retry use different limits; do not silently impose a unified policy |
| NOT-OQ-09 | Which user identities (customer, vendor employee, platform user) can configure preferences, and may vendor admins set defaults for employees/customers? | Product owner | SRS defines notification screens but not preference ownership/delegation |
| NOT-OQ-10 | What mock-provider behavior should represent accepted, delivered, bounced, undeliverable and unknown outcomes? | Product/test owner | The deterministic mock lifecycle must model agreed status semantics without pretending to be a selected provider |

### Verification status

This commit records the Phase 6 source audit and decisions only. It does not claim Phase 6 runtime implementation or test completion. No migration, adapter, API, UI or provider webhook implementation is included in this audit checkpoint. Full build/test/CI verification remains outstanding.
### Phase 6 implementation progress (2026-11-11)

Implemented in this branch (runtime verification pending):
- V12 adds tenant-owned `notification_outbox`, `notification_preference` and `notification_provider_event`; it extends the notification delivery status check for `DEAD_LETTER` and `SUPPRESSED`.
- Existing `NotificationDelivery` and Phase 5 `UserNotification` remain the delivery attempt and inbox aggregates; the new outbox references the delivery row rather than duplicating its payload.
- Business notification enqueueing now writes delivery and outbox rows in the same Spring transaction. Existing after-commit event dispatch remains as a low-latency path; a tenant-aware scheduled outbox dispatcher recovers pending/retry work after restart.
- Email adapter is pinned to local Mailpit (SMTP port 1025; Docker web UI port 8025). The development profile uses localhost:1025. SMS and WhatsApp use deterministic mock adapters only; no Twilio HTTP call or provider SDK is retained in NotificationService.
- Channel preferences are stored per tenant/user/channel, require explicit SMS/WhatsApp consent to enable, and are checked before dispatch. In-app preference is checked before inbox projection creation.
- A mock delivery webhook verifies HMAC-SHA256 over the raw request bytes, persists a provider event, rejects event-ID reuse with a changed payload hash, and updates only a delivery belonging to the supplied tenant.
- Tenant-scoped delivery, outbox and integration-job monitoring APIs were added. The existing notification inbox UI now includes preference controls and delivery status.
- Release validation now includes the Phase 6 branch and applies V12 in its clean MySQL 8.4 migration path.

### Remaining verification and policy gates
- This is not a production-provider implementation. Mock SMS/WhatsApp simulate acceptance and transient failure; they do not send messages.
- Runtime build, all backend/frontend tests, MySQL 8.4 Flyway execution and browser tests have not yet been observed passing on this branch. Do not close Phase 6 until branch-head CI is green.
- Integration job monitoring is tenant-scoped and read-only; manual retry/redrive API, provider configuration administration and platform-wide cross-tenant dashboard remain deferred pending role/scope approval.
- The mock webhook endpoint is a test contract only. Do not expose it as a production provider callback endpoint.
- NOT-OQ-01 through NOT-OQ-10 remain open. SLA escalation policy is not implemented because thresholds, targets and cadence are unspecified.

### Additional Phase 6 deferred decisions

| ID | Open question | Decision owner | Implementation consequence |
|---|---|---|---|
| NOT-OQ-11 | What notification template catalogue, locale fallback, approved variables, versioning and vendor/platform editing permissions are required? | Product and security owners | The current delivery flow uses existing event subject/body; template administration and platform defaults are deferred rather than inventing a template language or exposing unsafe variables. |
| NOT-OQ-12 | Which roles may manually redrive DEAD_LETTER notification and integration jobs, and should redrive reset attempts or create a new linked execution record? | Operations/security owner | Monitoring is read-only. No retry/redrive mutation API is exposed until least-privilege permission and audit semantics are approved. |
| NOT-OQ-13 | Should platform operations have cross-tenant provider health/backlog visibility, and which fields may be visible? | Product/security/privacy owner | Current monitoring endpoints are tenant-scoped; tenant-wide outbox/integration monitoring is restricted to the vendor administrator. Platform cross-tenant dashboard is deferred. |
| NOT-OQ-14 | Which existing integration providers/adapters are approved for each integration type, and should their mock adapters be profile-selected independently? | Product/integration owner | Existing IntegrationSyncScheduler and adapters are retained. No new real integration provider is selected or contacted by this phase. |

These are explicit deferrals, not silently completed features. Phase 6 remains PARTIAL until the above decisions are made and the corresponding UI/workflows are implemented or explicitly excluded by product approval.
## Phase 6 implementation update — 2026-11-11

This section supersedes the initial audit's statement that Phase 6 runtime work had not yet been applied. The original audit remains as the branch-head baseline and explains why the changes below were required.

### Implemented in this branch

- **Transactional outbox:** business notification creation now persists the `notification_delivery` row and its `notification_outbox` row within the same `@Transactional` service boundary. The business request path no longer publishes an asynchronous event that can bypass the outbox. Dispatch is performed by the tenant-scoped outbox scheduler after the transaction has committed.
- **Provider ports and selection:** `NotificationChannelAdapter` and `NotificationChannelAdapterRegistry` are the provider seam. Email uses the local Mailpit SMTP adapter. SMS/WhatsApp use the deterministic `MOCK-MESSAGING` adapter. `app.messaging.provider` / `NOTIFICATION_MESSAGING_PROVIDER` selects `mock` (default) or `twilio`. The Twilio adapter is deliberately a network-disabled placeholder that fails closed; no Twilio SDK, credentials, or network calls are included.
- **Retry/dead-letter:** business delivery is processed only through the outbox. Provider errors are stored as generic non-PII messages; retry attempts use bounded exponential backoff and a five-attempt dead-letter limit. Provider-accepted SMS/WhatsApp messages are recorded as `SENT` until a signed provider callback confirms `DELIVERED` or reports `FAILED`/`BOUNCED`.
- **Webhook handling:** the mock webhook is only registered in dev/test profiles, validates an HMAC-SHA256 signature before parsing/looking up delivery data, stores provider events under a unique provider/event key, checks duplicate payload hashes, and updates tenant-scoped delivery/outbox state. A late failure callback cannot downgrade a delivered message.
- **Preferences and consent:** per-user/per-channel preferences are persisted and exposed through the existing preference API. SMS and WhatsApp require an explicit enabled preference and consent record; absent consent suppresses delivery. Withdrawing consent also disables that channel from the frontend.
- **Monitoring and frontend:** existing tenant-scoped delivery, outbox and integration-job monitoring APIs are retained. The existing Notifications workspace displays inbox, per-channel preferences, delivery attempts and outbox/integration status; its consent action was corrected to disable a channel on withdrawal.
- **Inbox transaction boundary:** the in-app notification projection now joins the business transaction rather than committing independently in a `REQUIRES_NEW` transaction.
- **Migrations:** V12 adds outbox, preference and provider-event tables and expands delivery states; V13 adds the provider-accepted `SENT` state; V14 enforces tenant-matched composite foreign keys from outbox/provider events to delivery rows. The release validation workflow now applies V13 in its MySQL 8.4 migration job.

### Verification artifacts added/updated

- Backend tests: transactional outbox enqueue/idempotency, preference enforcement, retry/backoff and dead-letter, provider adapter selection, Twilio fail-closed behavior, signed webhook idempotency and outbox status update.
- Frontend test: explicit opt-in consent request.
- CI workflow: updated migration-count and MySQL 8.4 V13 validation steps.

**Execution status:** source changes and test definitions are committed, but Maven, npm, MySQL migration, Playwright and GitHub Actions results have not been executed/observed in this session. Do not treat build/test acceptance as passed until the branch workflow is green.

### Remaining policy/open-scope items

- The NOT-OQ-01/02 escalation thresholds, clock semantics, recipient matrix and escalation cadence remain unimplemented because the requirements do not define them.
- NOT-OQ-03/04 mandatory-event rules and full consent evidence/legal lifecycle remain product/privacy decisions. The implemented SMS/WhatsApp opt-in gate is the conservative default; email and in-app retain existing default behavior unless a preference row disables them.
- NOT-OQ-05 real-provider webhook headers, payloads, timestamp tolerance and key rotation remain undefined. Only the documented mock HMAC contract is implemented.
- NOT-OQ-06 retention/deletion periods remain undefined; no cleanup job was invented.
- NOT-OQ-07 cross-tenant platform visibility remains restricted; monitoring APIs are tenant-scoped and vendor-admin-only for outbox/integration job records.
- NOT-OQ-08 retry policy currently uses five attempts and bounded exponential backoff as a technical default. Operations must approve channel-specific limits, jitter and manual redrive permissions.
- NOT-OQ-09 preference management is self-service for authenticated tenant users; vendor-admin delegation/defaults remain undefined.
- NOT-OQ-10 mock delivery simulation currently supports deterministic acceptance and the explicit `[[MOCK_FAIL]]` failure trigger. Delivery callbacks are posted separately to the dev/test mock webhook.

**Out of scope by explicit instruction:** live Twilio requests, Twilio SDK/credentials, real SMS/WhatsApp sending, any paid/hosted message provider, and undocumented SLA escalation policy. Selecting `app.messaging.provider=twilio` currently fails closed by design until live integration is separately authorized and implemented.


---

# Repository cleanup — Phase 0 analysis

**Date:** 2026-11-12  
**Branch:** `cleanup/final-code-cleanup`  
**Base:** `audit/implementation-gap-analysis`  
**Phase 0 scope:** documentation and evidence inventory only. No application code, configuration, SQL migration, test, CI workflow, or script has been removed or modified.

## Conflicts

| Document | Section | Conflict | Resolution |
|---|---|---|---|
| Business requirements | §15 Communication System | Requires centralized notifications over email, SMS, WhatsApp and in-app, with replaceable provider interfaces. | Preserve the notification domain, channels, adapter ports, persisted delivery state, inbox and retry behavior. Removing an external transport integration is not permission to remove notification functionality. |
| SRS/HLD | §§10.12, 17, 18, 16.5 | Explicitly requires asynchronous notification processing, persisted attempts, retries, background jobs, event handling and dead-letter/manual-retry capability. | Preserve asynchronous behavior. The documented implementation uses a database-backed outbox/job model and in-process schedulers; do not replace it with another queue product. If a third-party queue integration is found, remove only that adapter after verifying the durable DB-backed behavior remains intact. |
| SRS/HLD | §§17–18 | Requires integrations, background jobs and events, including tenant-aware processing and reliability. | Retain IntegrationSyncJob, IntegrationSyncScheduler, offline synchronization and integration adapters unless an individual implementation is proven outside scope. The removal of external queue products does not mean the removal of asynchronous work. |
| SRS/HLD | §§14, 20, 21, 23 | Requires Flyway/Liquibase migrations, tenant isolation, clean-install validation and reference/seed data. | Consolidate V1–V14 only after clean MySQL 8.4 old-chain vs single-baseline schema comparison. Preserve all required columns, indexes, constraints, composite tenant FKs, triggers, seed/catalogue data and permission grants. |
| Business requirements and SRS/HLD | §§1, 11, 12; SRS §§8–9, 15 | Require multi-tenant access, lifecycle and tenant-scoped security; no legacy production deployment exists. | Remove backward-compatibility code only when route/API and source-reference searches prove it is not a documented contract. Tenant authorization and security controls are not compatibility code. |

## Removals

**No removals are approved in Phase 0.** This is deliberate: the branch inventory alone does not establish that a file, dependency, property, environment variable, script or route is unused. Each candidate below requires a positive repository-wide reference search and a negative requirements/SRS trace before Phase 1 deletion.

| Path / candidate | Why it may be unused (hypothesis only) | Evidence required before removal | Decision |
|---|---|---|---|
| Any deprecated endpoint or old DTO/version | Could be a compatibility surface. | Enumerate every mapping and DTO reference; compare method/path/request/response behavior with SRS/HLD §13 and frontend callers/tests. | KEEP pending evidence |
| Any feature flag or fallback | Could preserve a superseded implementation path. | Find all property reads, environment bindings, profile overrides, tests and runtime references; prove no required behavior depends on it. | KEEP pending evidence |
| Any dependency in backend/pom.xml or frontend/package.json | Could be unused. | Build dependency tree/import graph, source/test references and plugin usage; verify build and tests without candidate. | KEEP pending evidence |
| Any application property or environment variable | Could be stale. | Trace property binding, `@Value`, `@ConfigurationProperties`, compose/workflow/script references and deployment docs. | KEEP pending evidence |
| Any SQL file outside the active Flyway chain | Could be a one-off, rollback or duplicate seed script. | Search all scripts, workflows, test resources and documentation for invocation; compare schema/data effects. | KEEP pending evidence |
| Any script or test fixture | Could be orphaned. | Search workflow/package/compose references and test imports; prove no required behavior/test evidence is lost. | KEEP pending evidence |
| Any third-party message queue dependency or transport | The user explicitly requests zero third-party message-queue usage. | Search all source, build manifests, lockfiles, properties, env files, compose, CI, scripts, docs and tests for third-party queue client libraries and endpoint/configuration references; distinguish third-party queue transport from required DB outbox, async executor, scheduler and integration jobs. | KEEP until complete evidence; remove transport only |
| Any migration V2–V14 | Candidate for consolidation, not unused functionality. | Reproduce all 14 active migrations on clean MySQL 8.4, compare against the proposed consolidated baseline and document exact schema/seed equivalence. | KEEP until schema diff passes |

## Contract Changes

| Old | New | Reason | Document reference |
|---|---|---|---|
| None approved | None | Phase 0 is documentation-only. No REST API, DTO, event, persistence, or user-visible contract is changed. | SRS/HLD §§10–14 |

## Open Questions

1. **Async implementation boundary:** The SRS requires asynchronous processing, and the existing implementation/gap inventory describes DB-backed outbox and integration jobs plus in-process schedulers/listeners. Confirm that the cleanup intent is to eliminate external queue transports only, while retaining these required async mechanisms. Until confirmed, preserve them.
2. **Provider adapter placeholder:** Existing Phase 6 decisions describe a fail-closed Twilio adapter seam and mock SMS/WhatsApp. The business requirement calls for replaceable providers, but no production provider is selected. Preserve the interface and fail-closed behavior; do not introduce a live provider or remove the required adapter boundary.
3. **External worker infrastructure:** Repository documentation says the checked-in scan cannot prove absence of cloud-managed queues, cron jobs or worker overrides. Infrastructure owner confirmation is required for external resources; no external infrastructure configuration is changed by this repository cleanup.
4. **Notification policy:** SLA escalation, mandatory event preferences, consent evidence, retention, provider callback contracts, retry/redrive policy and cross-tenant monitoring remain unresolved in the existing NOT-OQ records. Cleanup must not invent these policies.
5. **Schema baseline semantics:** Confirm that the final single baseline is intended for fresh installations only and that there are no deployed production databases requiring a Flyway history migration. User context states no production deployment, so the plan assumes fresh installs but still documents Flyway history handling.
6. **CI branch policy:** User instruction requires release-validation triggers to include only `main` and `cleanup/final-code-cleanup`. Confirm whether the phrase "restrict CI to the single valid branch" applies only to release-validation as separately specified, or all workflows. Phase 3 will apply the explicit workflow-specific rule and audit other workflows for stale audit-branch triggers.

## Verification

| Check | Command / evidence | Result |
|---|---|---|
| Base branch | GitHub branch creation from `audit/implementation-gap-analysis` | PASS — target branch created |
| Required source documents | Full GitHub file fetch for all three named documents; heading inventory recorded in `docs/CLEANUP_TRACEABILITY.md` | PASS — source files fetched in full; README excluded |
| Repository clone | `git clone --branch audit/implementation-gap-analysis --single-branch https://github.com/rambrij/saumya-solar-energy-product.git` | BLOCKED — execution environment could not resolve github.com |
| Local build/tests/MySQL schema dump | Maven, npm, Docker Compose and MySQL commands | NOT RUN — no local repository clone/network resolution in this execution environment |
| Repository-wide external transport scan | Pending complete branch-ref source/config/docs search | NOT YET VERIFIED |
| Schema equivalence | Pending old migration chain vs single baseline MySQL 8.4 dump diff | NOT RUN |
| Fresh database application startup | Pending baseline consolidation | NOT RUN |
| CI branch policy | Pending Phase 3 workflow audit/change | NOT RUN |

No command is marked successful unless its output was observed. GitHub Actions must provide build, test, MySQL and browser verification evidence after implementation commits.


### Phase 0 repository evidence addendum

The following branch-head evidence was inspected after the initial inventory:

- `backend/pom.xml` declares Spring Web, Security, Data JPA, Validation, Mail, Actuator, Flyway, OpenAPI, JWT, PDF/XLSX, MySQL and test dependencies. No third-party queue client dependency is declared in this manifest. This does not prove the absence of transitive or frontend/other build dependencies.
- Root `docker-compose.yml` defines MySQL, Mailpit, backend and frontend services; no dedicated queue service is present in the inspected file.
- `docs/PHASE_1_2_BACKGROUND_JOB_INVENTORY.md` records database-backed `IntegrationSyncJob` and `notification_outbox` processing, seven scheduled jobs, two in-process async listeners and a bounded executor. It also records that the static worker scan found no checked-in third-party producer/consumer queue integrations on the documented audit checkpoint. This is supporting historical evidence, not a substitute for a fresh scan on this target branch.
- The notification outbox, provider adapters, preferences and tenant-bound references are required functionality under business requirements §15 and SRS/HLD §§10.12, 17–18. These must remain. Only a proven third-party queue transport/configuration may be removed.
- `.github/workflows/release-validation.yml` currently has audit-only push and pull-request branch filters (Phase 2, 3, 4 and 6 audit branches). Its migration job also contains historical migration application/equivalence steps. Both are planned Phase 2/3 work, not Phase 0 changes.
- `.github/workflows/phase-2-production-preflight.yml` currently contains an `if` condition and checkout ref for `audit/phase-2-implementation-gap-analysis`. This is an additional stale branch-specific workflow reference to assess in Phase 3.
- The release-validation workflow contains references to async-worker and legacy-authorization audit scripts. These are verification steps, not evidence of application runtime compatibility code; do not remove them solely because their names contain "legacy".

These findings do not authorize a code/config/CI deletion in Phase 0. Full repository-wide reference search and fresh CI evidence remain mandatory before marking the external transport scan complete.


---

# Cleanup implementation record — Phases 1–4

**Date:** 2026-11-12  
**Branch:** `cleanup/final-code-cleanup`  
**Base:** `audit/implementation-gap-analysis`

This section supersedes the Phase 0 pending statements above. The Phase 0 findings remain as the initial snapshot and are not the final implementation status.

## Full reference inventory completed

| Inventory | Evidence |
|---|---|
| Required specifications | Full text and heading maps for the business requirements, SRS/HLD and implementation gap analysis are recorded in `docs/CLEANUP_TRACEABILITY.md`. |
| Backend source | Recursive Git tree review: 353 files across API, config, domain, repository, security, service, resource and test areas. |
| Frontend source | Recursive Git tree review: 48 files across source, component tests, E2E suites, public assets and build configuration. |
| Documentation | Recursive docs tree review: 20 files, plus root-level requirements, SRS/HLD and implementation analysis documents. |
| CI/workflows | Both workflow files were inspected. Release validation now filters only `main` and `cleanup/final-code-cleanup`; production preflight runs only for `main`. |
| Scripts | All four original audit scripts were inspected. The worker scan was generalized and a repository-wide external transport assertion was added. |
| REST API mappings | Spring mapping annotations were inventoried across all API controller classes and recorded in `CLEANUP_TRACEABILITY.md`. |
| Frontend screens/callers | Screen component and API-family mapping is in `CLEANUP_TRACEABILITY.md`; the settings-alias caller search identified the only consumer of the undocumented aliases. |
| Database | Original V1 contained 74 tables; V2–V14 added ten tables plus required columns, constraints, indexes, triggers and seed/grant operations. The consolidated baseline now defines 84 tables. |

The source inventory is evidence of what exists in the repository. It is not a blanket assertion that every line of application code has been proven dead-code free. No domain feature, DTO, dependency, environment property, test fixture, or application service has been deleted without positive source/reference evidence.

## Conflicts and resolutions

| Documents / sections | Conflict | Resolution |
|---|---|---|
| Business requirements §15; SRS/HLD §§10.12, 17–18 | Notification processing must be asynchronous and include persisted attempts, retry/dead-letter behavior and pluggable channels. | Preserved the transactional database outbox, notification delivery/inbox, Mailpit email adapter, deterministic SMS/WhatsApp mock adapter, provider interface, background dispatcher and tenant-scoped retry/monitoring. No replacement transport was introduced. |
| SRS/HLD §§17–18 | Integration jobs and other background tasks must remain observable and tenant scoped. | Preserved database-backed `IntegrationSyncJob`, `IntegrationSyncScheduler`, offline sync, scheduled jobs and bounded in-process application event execution. |
| SRS/HLD §13 vs controller aliases | `GET /settings` and `PATCH /settings/discount-threshold` were undocumented aliases; SRS describes the vendor/tenant settings contract but not these short aliases. | Kept the documented tenant settings functionality and removed only the undocumented aliases. Frontend calls now use `/tenant/settings` and `/tenant/settings/discount-threshold`. |
| SRS/HLD §§14, 20–23 vs old migration chain | One clean-install schema is required; the old tree had 14 active Flyway files. | Folded every original migration SQL operation into one V1 baseline. Schema equivalence is enforced by the CI MySQL 8.4 diff job; no deployed Flyway history conversion is provided. |
| User CI branch rule vs workflow triggers | Release validation previously triggered on audit-only branch names. | Release-validation push and PR branch filters now contain exactly `main` and `cleanup/final-code-cleanup`. The separate production preflight is manually dispatched and constrained to `main`; no audit branch binding remains. |

## Removals and consolidation inventory

The listed SQL files were removed as separate active Flyway migration files only after their SQL was incorporated into `V1__baseline.sql`. These are migration-file consolidations, not feature removals.

| Path | Why the standalone path is no longer needed | Evidence / disposition |
|---|---|---|
| `backend/src/main/java/com/saumyasolar/api/Controller.java` (two alias mappings) | The short settings paths were undocumented compatibility aliases. | Targeted mapping/caller search found the only consumer in `frontend/src/main.tsx`; updated it to the canonical paths. SRS/HLD §13 does not list the aliases. Underlying tenant settings behavior is retained. |
| `backend/src/main/resources/db/migration/V2__phase3_reservation_expiry.sql` | Standalone migration no longer required after consolidation. | Reservation expiry columns, release metadata, index, status-history actor column and append-only triggers are included in V1. |
| `backend/src/main/resources/db/migration/V3__phase3_order_policy_and_tenant_lifecycle.sql` | Standalone migration no longer required after consolidation. | Tenant lifecycle metadata, data initialization, persisted order-transition policy, default transitions and permission catalogue row are included in V1. |
| `backend/src/main/resources/db/migration/V4__phase3_order_policy_permission_grant.sql` | Standalone grant migration no longer required after consolidation. | Vendor administrator order-policy permission grant is included in V1 seed/reference DML. |
| `backend/src/main/resources/db/migration/V5__phase4_finance_provider_reconciliation.sql` | Standalone finance extension no longer required after consolidation. | Nullable payment timestamp, tenant-scoped webhook provider fields/keys, refund provider/idempotency fields, reconciliation tables and tenant FKs are included in V1. |
| `backend/src/main/resources/db/migration/V6__phase5_cms_content_revisions.sql` | Standalone CMS extension no longer required after consolidation. | CMS content/revision tables, revision pointer FKs, supporting indexes and immutable revision triggers are included in V1. |
| `backend/src/main/resources/db/migration/V7__phase5_user_notification_inbox.sql` | Standalone inbox migration no longer required after consolidation. | Tenant/user-scoped `user_notification` table and inbox indexes are included in V1. |
| `backend/src/main/resources/db/migration/V8__phase5_notification_view_permission.sql` | Standalone permission migration no longer required after consolidation. | `NOTIFICATION_VIEW` catalogue row and supported built-in vendor-role grants are included in V1 seed DML. |
| `backend/src/main/resources/db/migration/V9__phase5_platform_analytics_indexes.sql` | Standalone analytics index migration no longer required after consolidation. | Customer-order created-at and tenant-subscription status indexes are included in V1. |
| `backend/src/main/resources/db/migration/V10__phase5_typed_platform_settings.sql` | Standalone platform settings migration no longer required after consolidation. | Typed `platform_setting` table, actor FK and type/version checks are included in V1. |
| `backend/src/main/resources/db/migration/V11__phase5_operational_analytics_indexes.sql` | Standalone operations index migration no longer required after consolidation. | Installation, service, invoice and order tenant/assignee/date indexes are included in V1. |
| `backend/src/main/resources/db/migration/V12__phase6_notification_outbox_preferences_provider_events.sql` | Standalone notification outbox extension no longer required after consolidation. | Outbox, preference and provider-event tables, status check update and indexes are included in V1. |
| `backend/src/main/resources/db/migration/V13__phase6_provider_accepted_delivery_status.sql` | Standalone delivery-status extension no longer required after consolidation. | `SENT` status in the notification delivery status check is included in V1. |
| `backend/src/main/resources/db/migration/V14__phase6_enforce_tenant_bound_notification_references.sql` | Standalone tenant-FK hardening migration no longer required after consolidation. | Delivery tenant/id unique key and composite tenant-matched foreign keys for outbox and provider-event references are included in V1. |
| `.github/workflows/release-validation.yml` (old audit-only trigger entries and historical 63-file migration-history procedure) | Old trigger names and migration-history procedure did not match the requested branch policy or current 14-file active chain. | Replaced triggers with the two requested branches and replaced the historical 63-file procedure with a 14-migration old-chain vs one-baseline MySQL comparison. Verification steps for build, tests, tenant authorization, startup and browser security remain. |
| `.github/workflows/phase-2-production-preflight.yml` (hard-coded audit branch condition/checkout ref) | Branch-bound preflight pointed only to an old audit branch. | Removed hard-coded checkout ref and constrained the manual production preflight to `main`; protected secrets, data-integrity checks and evidence manifest remain. |
| `scripts/audit/scan-async-workers.sh` (retired product-specific transport signatures) | The worker inventory should detect current in-process async/scheduled execution, not retain old transport product signatures. | Generalized the scan for event publishers, schedulers, executors and generic queue-client methods. Added the separate whole-repository transport assertion below. |

### Items intentionally retained

No external queue product dependency, SDK, service, environment variable or producer/consumer was present in the inspected Maven manifest or Compose file, so none was deleted from those locations. No unused Maven/npm dependency or application property was positively proven by the performed reference inventory. All required notification, integration, offline sync, exports, event listeners, schedulers, security controls, tenant restrictions, domain services and their tests remain.

The `TwilioMessagingNotificationAdapter` is a network-disabled fail-closed placeholder, not a live provider transport. It remains as a replaceable adapter seam; mock SMS/WhatsApp remains deterministic and active only in dev/test configuration. Real provider selection is still open.

## Contract Changes

| Old | New | Reason | Document reference |
|---|---|---|---|
| `GET /api/v1/settings` | `GET /api/v1/tenant/settings` | Removed undocumented alias; frontend uses canonical path. | SRS/HLD §13.3; tenant/vendor settings |
| `PATCH /api/v1/settings/discount-threshold` | `PATCH /api/v1/tenant/settings/discount-threshold` | Removed undocumented alias; method, request body, authorization and business behavior remain unchanged. | SRS/HLD §13.3 and vendor settings module |
| Flyway V1–V14 for fresh installs | Single `V1__baseline.sql` for fresh installs | The product has no production deployment; preserve all schema/seed semantics while reducing clean-install migration chain to one file. | SRS/HLD §§14, 22–23 |
| Release validation branch filters (audit-only branches) | Only `main` and `cleanup/final-code-cleanup` | Follow explicit requested branch policy. | User cleanup instruction, Phase 3 |

No documented business workflow, documented versioned REST API, authorization rule, tenant ownership rule, message event, or notification channel was intentionally removed.

## Open Questions

1. **External infrastructure inventory:** Repository scanning cannot establish that separately configured cloud queues, cron triggers, managed workers, or environment overrides do not exist outside Git. Infrastructure owner should provide current exports/sign-off for the actual target deployment account. The source documents do not identify a production deployment for this repository.
2. **Provider selection:** SMS/WhatsApp currently uses a deterministic dev/test mock and the reserved real-provider adapter fails closed. Select and approve a live provider separately before any real outbound integration is implemented.
3. **Notification policy:** Existing NOT-OQ decisions remain open for SLA escalation thresholds/targets/cadence, mandatory notification categories, consent evidence/retention, provider callback contract, retry/redrive policy, notification retention and platform-wide monitoring. Cleanup did not invent those policies.
4. **Manual local build availability:** The local clone was attempted with the user's permission, but this execution host still returned a DNS resolution failure for `github.com`. Maven/npm/MySQL commands were therefore not run locally. GitHub Actions is the available build/test/DB verification path.
5. **Baseline schema result:** PASS on final branch-head CI run 43089380154. The old V1–V14 chain and consolidated V1 produce identical MySQL 8.4 schema dumps; artifact 13888520692 contains a 63-byte PASS result and no diff. No schema deviation required justification.
6. **Branch-policy interpretation:** The explicit release-validation rule has been applied exactly as written. The production preflight remains manual and is restricted to `main` so it cannot run against an audit/feature branch with production secrets.
7. **Unused symbols/dependencies attestation:** The full file/tree, endpoint, caller and configuration inventories were reviewed, but no exhaustive compiler-assisted dead-code/symbol reachability or Maven/npm dependency-usage report was produced because local clone DNS was unavailable. No unproven dependency, class, config property or fixture was removed. A strict zero-unused-code attestation would require a separate full dependency/symbol audit; uncertain candidates were preserved.

## Verification

| Check | Command / evidence | Current result |
|---|---|---|
| Base branch ancestry | GitHub compare `audit/implementation-gap-analysis...cleanup/final-code-cleanup` | PASS — branch is based directly on the requested base and is ahead; no base divergence was reported at the ancestry check. |
| Active migrations | GitHub tree listing of `backend/src/main/resources/db/migration` | PASS — exactly one active file, `V1__baseline.sql`. |
| Consolidated SQL inventory | Source parser over all original 14 migration files; original statement order retained within DML | PASS — 84 tables, 14 table alters, 6 standalone indexes, 8 triggers, 67 DML statements, zero unclassified statements. |
| Runtime API route caller cleanup | Mapping and reference searches for both settings aliases | PASS — canonical frontend paths and canonical backend mappings are aligned. |
| Workflow branch references | Inspected both workflow files | PASS — release-validation branches are exactly `main` and `cleanup/final-code-cleanup`; production preflight condition is `main`. |
| External transport source/dependency/compose scan | Manifest, Compose, worker inventory and repository-wide assertion in CI | PASS — whole-repository assertion completed successfully on final branch HEAD. |
| Old-chain vs single-baseline schema diff | `mysql-migrations` CI job: old 14-migration chain vs consolidated V1 on MySQL 8.4 | PASS — run 43089380154; artifact 13888520692 contains `PASS: old V1-V14 schema and the single baseline are identical.` |
| Backend build/tests | `mvn --batch-mode --no-transfer-progress clean verify` in workflow | PASS — 274 tests run, 0 failures, 0 errors, 0 skipped; `BUILD SUCCESS` on final branch HEAD. |
| Frontend tests/build | `npm test` and `npm run build` in workflow | PASS — component tests and production typecheck/build steps succeeded on final branch HEAD. |
| Fresh database application startup | Spring Boot health check against clean MySQL 8.4 schema in workflow | PASS — application packaged, started against fresh MySQL 8.4 and health endpoint verified. |
| Browser security regression | Playwright E2E in workflow against fresh MySQL-backed app | PASS — browser-level tenant filter tampering suite succeeded. |
| Local clone | `git clone --branch cleanup/final-code-cleanup --single-branch https://github.com/rambrij/saumya-solar-energy-product.git` | BLOCKED — DNS lookup for `github.com` failed in this execution environment despite user authorization. |

### Final branch-head verification — 2026-11-12

**Workflow:** [run 43089380154](https://github.com/rambrij/saumya-solar-energy-product/actions/runs/43089380154)  
**Commit:** `deb61269572b19799bcc72685475c92c2e4f9a5e`  
**Conclusion:** SUCCESS — all four jobs passed.

| Job | Result and evidence |
|---|---|
| Backend compile and tests | PASS — targeted security/tenant/messaging tests, `mvn clean verify`, 274 tests, 0 failures/errors/skips, and runtime endpoint authorization evidence all passed. |
| Frontend typecheck and production build | PASS — dependency install, component tests and production build completed. |
| Validate Flyway migrations on MySQL 8.4 | PASS — exactly one baseline, old V1–V14 and new V1 clean-database schema dumps identical, tenant schema/FK/index/NULL audit passed. Final artifact [cleanup-schema-equivalence, 13888520692](https://github.com/rambrij/saumya-solar-energy-product/actions/runs/43089380154) contains the exact PASS result. |
| Start Spring Boot against MySQL 8.4 and Mailpit | PASS — package, concurrent SLA/warranty/integration scheduler tests, application health, tenant-integrity preflight and browser tenant-filter tampering tests all succeeded. |

Additional runtime authorization evidence from the backend job: 260 runtime mappings reconciled (239 protected and 21 public), 239 positive applicable-account probes, 106 zero-permission GET probes and 133 zero-permission mutation probes. Every runtime mapping received an explicit authentication/public classification; every protected mapping received positive applicable-account and least-privilege evidence. The checker reports 38 runtime-only mappings not listed in the narrower historical `PHASE_2_ENDPOINT_MATRIX.md`; these are identified in the expanded API traceability inventory and do not fail the runtime authorization gate.

The repository-wide prohibited-transport scan passed on this final commit. The active Flyway directory contains exactly one file, `V1__baseline.sql`. The baseline schema comparison reported zero differences. No production database or external cloud account was accessed or changed.

### Remaining evidence limitation

The connected GitHub checkout/Actions runner successfully checked out this branch and executed all CI commands. A separate clone from this assistant execution host continued to fail DNS resolution for `github.com`, so local Maven/npm commands were not run from this session. The source/reference inventory was completed through repository tree, code, controller mapping and caller searches; however, no exhaustive static Java symbol reachability/dead-code proof or complete Maven/npm unused-dependency report was available here. In accordance with the keep-on-ambiguity rule, no dependency, application property, domain class, service or fixture with uncertain use was deleted. The only runtime contract removal was the proven undocumented settings alias, and V2–V14 were consolidated rather than their schema effects removed.
