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
