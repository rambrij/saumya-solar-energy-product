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
