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

### Verification limitation

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
