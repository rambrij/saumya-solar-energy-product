# Phase 3 Enterprise SaaS — Audit Findings and Gap Analysis

- Repository: `rambrij/saumya-solar-energy-product`
- Target branch: `feature/phase-3-Enterprise-SaaS`
- Audit baseline: branch HEAD `c4516a311848bbac97c31f93e15e85eacfa6d05e`
- Audit type: source-code and repository evidence review
- Scope: Phase 3 Enterprise SaaS, plus relevant Phase 1/2 regression and security foundations
- Implementation status at time of audit: no application changes made

> This document records the gap assessment used to plan the approved implementation. A source file, entity, table, endpoint or UI control alone is not treated as proof of a complete business capability. Runtime verification is explicitly distinguished from source inspection.

## 1. Executive summary

**Overall assessment: NOT READY for Phase 3 acceptance.**

The branch was created from `main` and, at the time of audit, had no commits ahead of or behind `main`. The implementation contains substantial Phase 1/2 foundations: tenant-aware JWT authentication, tenant-scoped operations, inventory and warehouse movements, quotation discount approval, domain-specific history, operational reporting, responsive frontend styles, payment tracking, signed webhook handling and Twilio messaging.

None of the 11 named Phase 3 feature areas met the complete end-to-end acceptance bar. Major missing capabilities include subscription plans and entitlement enforcement, branch management and branch-level isolation, centralized advanced audit, configurable multi-step approvals, accounting synchronization, a concrete online payment provider, and a tenant-aware integration management framework.

The audit did not establish a confirmed cross-tenant exploit in the reviewed paths. It also did not perform a complete penetration test or live deployment test, so it cannot certify that all tenant boundaries are safe.

## 2. Repository and evidence baseline

| Item | Finding |
|---|---|
| Repository | `rambrij/saumya-solar-energy-product` |
| Branch | `feature/phase-3-Enterprise-SaaS` |
| HEAD reviewed | `c4516a311848bbac97c31f93e15e85eacfa6d05e` |
| Relationship to main | Same tree at audit time; 0 commits ahead / 0 behind |
| Stack | Spring Boot 3 / Java 21, Maven, React / TypeScript / Vite, MySQL, Flyway |
| Migrations | V1 through V36 present in the inspected tree |
| Runtime verification | Not performed |
| Current-HEAD CI verification | No successful current-HEAD workflow result was available during the audit |
| Application modifications during audit | None |

### Evidence classifications

- **Code Verified**: source implementation was inspected.
- **Test Evidence**: test source exists; this does not mean it was executed in this audit.
- **Runtime Verified**: exercised against a running application/provider/database. No Phase 3 capability received this classification in this audit.
- **Documentation Only**: described in a reference document or README but not established by implementation evidence.
- **Unable to Verify**: evidence or runtime environment was unavailable.

## 3. Reference-document interpretation

The audit used:

1. `saumya-solar-energy-solution-requirements.md`
2. `saumya-solar-production-ready-srs-hld.md`

The SRS includes vendor lifecycle and subscription requirements (VEN-001–VEN-006), inventory requirements (INV-001–INV-008), quotation discount approval (QUO-003), reporting requirements (RPT-001–RPT-006), audit requirements (AUD-001–AUD-005), external integration requirements and a Phase 3 roadmap covering subscription metering, white-label domains, workflow design, accounting, PWA, IoT and advanced analytics.

**Phase boundary ambiguity:** the two documents do not consistently assign branches, multiple warehouses and payment gateway to the same release. The user's explicit Phase 3 scope is treated as the controlling release scope for this gap assessment. Product-owner confirmation is still required for conflicting phase boundaries and optional IoT/service-decomposition scope.

## 4. Status and priority definitions

Statuses used in the traceability matrix:

- **COMPLETE** — end-to-end business behavior, persistence, authorization, tenant isolation and relevant tests are evidenced.
- **PARTIALLY IMPLEMENTED** — meaningful implementation exists, but one or more required parts are missing.
- **NOT IMPLEMENTED** — no implementation evidence was found.
- **IMPLEMENTED BUT INCORRECT** — code exists but observed logic does not satisfy the requirement.
- **BLOCKED BY EXISTING DESIGN** — existing architecture prevents safe completion without a design change.
- **REQUIRES CLARIFICATION** — acceptance behavior or scope needs a product decision.

Priorities:

- **CRITICAL** — tenant/security boundary, commercial entitlement enforcement, audit integrity or correctness blocker.
- **HIGH** — required enterprise business capability or financial/integration control.
- **MEDIUM** — important operational capability.
- **LOW** — architectural decision or optional scope item.

## 5. Requirements traceability matrix

| ID | Feature | Acceptance criterion | Status | Code evidence | Gap | Required change | Priority |
|---|---|---|---|---|---|---|---|
| SUB-01 | Subscription | Plan catalogue and plan lifecycle | NOT IMPLEMENTED | `domain/Tenant.java`; no plan entity/migration found | No plan model | Add plan catalogue, versioning and lifecycle | CRITICAL |
| SUB-02 | Subscription | Vendor assignment, activation, renewal, upgrade, downgrade, cancellation and expiry | NOT IMPLEMENTED | No subscription lifecycle service/API found | No subscription state machine or billing history | Add subscription lifecycle and state transition rules | CRITICAL |
| SUB-03 | Entitlements | Feature and quantitative limits with server-side enforcement | NOT IMPLEMENTED | No entitlement/metering service found | No enforcement for users, storage, branches or warehouses | Add centralized entitlement evaluator and usage ledger | CRITICAL |
| BRAND-01 | Branding | Tenant logo, display name, colors, contact/business details | PARTIALLY IMPLEMENTED | `domain/Tenant.java`, `frontend/src/main.tsx` | Tenant identity exists; configurable tenant theme/logo not established | Add branding profile, upload/storage rules and tenant-aware theme resolution | HIGH |
| BRAND-02 | White label | Public storefront branding and custom-domain mapping/verification | NOT IMPLEMENTED | `frontend/src/PublicHome.tsx`; storefront slug | No custom-domain ownership or tenant resolution | Add verified domain registry and host-to-tenant resolver | HIGH |
| BR-01 | Branches | Branch CRUD and tenant relationship | NOT IMPLEMENTED | No Branch model/migration/API found | Branch aggregate absent | Add branch entity, migration, service and CRUD API/UI | HIGH |
| BR-02 | Branch security | User-to-branch assignment, branch context and branch-level authorization | NOT IMPLEMENTED | `AppUser.java`, `Principal.java`, `JwtFilter.java` | No branch scope in identity or authorization | Add branch membership and enforce scope in every relevant query | CRITICAL |
| WH-01 | Warehouses | Multiple tenant warehouses and inventory movement | PARTIALLY IMPLEMENTED | `Warehouse.java`, `OperationsService.java`, V11 migration | Tenant-level warehouse and stock movement foundations exist | Complete lifecycle and acceptance coverage | MEDIUM |
| WH-02 | Warehouse security | Branch mapping, warehouse access permissions and isolation | NOT IMPLEMENTED | `Warehouse.java`, V11 migration | No branch association or warehouse grants found | Add branch_id and warehouse access policy | HIGH |
| APP-01 | Approval | Configurable quotation discount threshold and decisions | PARTIALLY IMPLEMENTED | `AppService.java`, `Controller.java`, V9 migration | Single quotation threshold flow only | Preserve and extend existing behavior | HIGH |
| APP-02 | Workflow | Multi-step configurable approval definitions, routing, comments and history | NOT IMPLEMENTED | No workflow definition/step model found | No workflow engine/designer | Add workflow definitions, instances, steps and transition service | HIGH |
| AUD-01 | Audit | Business event history for operational entities | PARTIALLY IMPLEMENTED | Lead/order/survey/installation histories, ServiceEvent, InventoryTransaction | Domain histories are fragmented | Emit normalized audit events from business services | HIGH |
| AUD-02 | Audit | Actor, tenant, timestamp, before/after, security events, append-only search | NOT IMPLEMENTED | No global audit log/API/UI found | No complete central audit capability | Add append-only audit store, capture hooks and secured search/export | CRITICAL |
| ANA-01 | Analytics | Vendor dashboard for sales, orders, leads, inventory, service and operations | PARTIALLY IMPLEMENTED | `Phase2OperationsController.java`, `OperationsPanel.tsx` | Basic operational summaries/reports; incomplete dimensions | Expand filters, dimensions, accuracy and performance | HIGH |
| ANA-02 | Analytics | Platform, branch and subscription usage analytics | PARTIALLY IMPLEMENTED | `TenantAdminService.java`, `Phase2AdminPanel.tsx` | Tenant directory/counts exist; subscription and branch usage absent | Add platform and branch analytics | HIGH |
| ANA-03 | Analytics | Correct payment/revenue aggregation | IMPLEMENTED BUT INCORRECT | `Phase2OperationsController.java`, `FinanceService.java` | Report counts SUCCEEDED/PAID while application records CONFIRMED | Align state semantics and test totals against ledger | HIGH |
| PWA-01 | Mobile | Responsive UI and usable mobile navigation | PARTIALLY IMPLEMENTED | `frontend/src/style.css`, `frontend/src/main.tsx` | Responsive styling exists; device-level usability not runtime verified | Add viewport/accessibility tests and mobile workflow validation | MEDIUM |
| PWA-02 | PWA | Manifest, service worker, installability, cache and offline behavior | NOT IMPLEMENTED | No manifest/service-worker/registration found | Not an installable PWA | Add PWA configuration and safe offline strategy | MEDIUM |
| ACC-01 | Accounting | Provider-neutral accounting adapter and mappings | NOT IMPLEMENTED | No accounting adapter found | No accounting connector | Select provider and implement adapter boundary | HIGH |
| ACC-02 | Accounting | Invoice/customer/payment sync, retry, idempotency and reconciliation | NOT IMPLEMENTED | No accounting sync state/migrations found | No synchronization pipeline | Add durable outbox/sync records, retries and reconciliation | HIGH |
| PAY-01 | Gateway | Create provider payment/checkout and verify provider result | PARTIALLY IMPLEMENTED | `PaymentGatewayAdapter.java`, `FinanceService.java` | Interface exists; no concrete provider adapter or checkout flow found | Implement selected provider adapter and payment intent lifecycle | HIGH |
| PAY-02 | Gateway | Signed webhook, idempotency, failure, cancellation and refund | PARTIALLY IMPLEMENTED | `FinanceService.java`, `PaymentWebhookEvent.java`, V18 | HMAC and dedupe foundations; no complete provider lifecycle/refund adapter | Complete provider-specific webhook and refund/reconciliation flows | HIGH |
| API-01 | Integrations | Reusable external integration architecture | PARTIALLY IMPLEMENTED | `NotificationService.java`, `PaymentGatewayAdapter.java` | Direct Twilio and gateway contracts only | Add provider registry and adapter contracts | HIGH |
| API-02 | Integrations | Tenant config/secrets, timeout, retry, rate limits, logs and correlation | PARTIALLY IMPLEMENTED | `application.yml`, `NotificationService.java` | Some global config, timeout and retry; no tenant-scoped integration administration | Add encrypted credential references and operational controls | HIGH |
| IOT-01 | IoT | Device onboarding and telemetry/monitoring (SRS roadmap) | NOT IMPLEMENTED | No IoT module found | No device/telemetry flow | Confirm whether included in this release; design only if approved | MEDIUM |
| ARC-01 | Architecture | Selective service decomposition justified by scale evidence | REQUIRES CLARIFICATION | Modular Spring Boot application | No measured bottleneck or scale threshold supplied | Keep modular monolith until measurements justify extraction | LOW |

## 6. Detailed findings

### 6.1 Subscription plans and entitlements — NOT IMPLEMENTED

Evidence inspected:
- `backend/src/main/java/com/saumyasolar/domain/Tenant.java`
- `backend/src/main/java/com/saumyasolar/service/TenantAdminService.java`
- `backend/src/main/java/com/saumyasolar/api/Controller.java`
- Flyway migration tree

Tenant management is not subscription management. No plan catalogue, subscription lifecycle, invoice/renewal records, metering ledger, feature entitlement model or centralized enforcement was found.

Required implementation:
1. Plan and plan-version model.
2. Tenant subscription and lifecycle state machine.
3. Entitlement catalogue and limits.
4. Usage metering with auditable counters.
5. Server-side enforcement at service boundaries.
6. Grace-period, expiry and disabled-vendor behavior.
7. Admin/vendor screens and lifecycle tests.

### 6.2 Branding and white-label — PARTIAL / NOT IMPLEMENTED

Evidence inspected:
- `Tenant.java`
- `frontend/src/main.tsx`
- `frontend/src/PublicHome.tsx`

Vendor identity and storefront slug exist, but a complete tenant-specific logo/theme/contact configuration and custom-domain verification were not found. The application header uses a platform-level logo.

Required implementation:
1. Tenant branding profile and validation.
2. Safe logo upload/storage and content-type/size checks.
3. CSS theme variables resolved from trusted tenant context.
4. Public storefront tenant resolution.
5. Custom domain verification, uniqueness and TLS/deployment prerequisites.
6. Tests proving one tenant's theme/domain cannot bleed into another.

### 6.3 Branches — NOT IMPLEMENTED

No branch entity, migration, API or frontend administration was found.

Required implementation:
1. Branch aggregate scoped to tenant.
2. User-to-branch memberships and branch role scope.
3. Branch context resolved server-side, never trusted from arbitrary request body.
4. Branch filters enforced in repository/service methods.
5. Tenant + branch security tests for read, write, exports and background jobs.
6. Explicit policy for users assigned to multiple branches and platform support access.

### 6.4 Warehouses — PARTIAL

Evidence inspected:
- `Warehouse.java`
- `OperationsService.java`
- `V11__release3_operations.sql`
- `OperationsPanel.tsx`

Tenant-level warehouses, balances and movement operations exist. Branch association and warehouse-level permissions are not established.

Required implementation:
1. Branch association with safe migration/backfill.
2. Warehouse membership/permission model.
3. Scope checks on all inventory reads, movements, reservations and transfers.
4. Branch/warehouse-aware reports.
5. Concurrency and negative-stock regression tests.

### 6.5 Approval workflows — PARTIAL

Evidence inspected:
- `Quotation.java`
- `AppService.java`
- `Controller.java`
- `V9__tenant_discount_approval_policy.sql`

A tenant discount threshold can put a quotation into pending approval and authorized users can approve/reject. This is not a configurable multi-step workflow platform.

Required implementation:
1. Versioned workflow definitions.
2. Workflow steps, conditions and approver resolution.
3. Workflow instances and decisions.
4. Comments/reasons, delegation, escalation and cancellation policy.
5. Permission checks and immutable decision history.
6. Backward compatibility with the current quotation threshold behavior.

### 6.6 Advanced audit — PARTIAL

Evidence inspected:
- `LeadHistory`
- `OrderStatusHistory`
- `SiteSurveyHistory`
- `InstallationScheduleHistory`
- `ServiceEvent`
- `InventoryTransaction`

Domain histories exist but are fragmented. A complete central append-only audit facility, before/after snapshots and search/filter UI were not found.

Required implementation:
1. Common audit event schema with tenant, actor, action, resource, timestamp, outcome and correlation ID.
2. Before/after capture for sensitive business changes with redaction of secrets and personal data where appropriate.
3. Append-only database permissions/controls.
4. Audit capture for authentication/security/admin/configuration and business actions.
5. Search/export endpoints with platform/vendor authorization.
6. Tamper-resistance and tenant-isolation tests.

### 6.7 Analytics — PARTIAL, WITH A CORRECTNESS DEFECT

Evidence inspected:
- `Phase2OperationsController.java`
- `TenantAdminService.java`
- `OperationsPanel.tsx`
- `Phase2AdminPanel.tsx`
- `FinanceService.java`

Operational dashboards and exports exist. A payment status mismatch was identified: report aggregation includes `SUCCEEDED` and `PAID`, while payment recording/webhook paths set `CONFIRMED`. Confirmed payments can therefore be omitted.

Required implementation:
1. Define canonical financial status semantics.
2. Correct aggregation from authoritative payment ledger entries.
3. Add date, branch, warehouse, product, engineer and customer filters as authorized.
4. Add subscription usage and platform analytics.
5. Test dashboard totals against source records and refunds/adjustments.
6. Validate export authorization and large-report behavior.

### 6.8 Mobile/PWA — PARTIAL

Evidence inspected:
- `frontend/src/style.css`
- `frontend/src/main.tsx`
- `frontend/index.html`
- `frontend/package.json`

Responsive CSS exists. No PWA manifest, service worker or registration was found.

Required implementation:
1. Web app manifest and icons.
2. Service worker and controlled asset caching.
3. Explicit offline data policy; do not cache sensitive tenant API responses indiscriminately.
4. Installability and update lifecycle.
5. Offline/expired-session behavior.
6. Mobile viewport and accessibility tests.

### 6.9 Accounting — NOT IMPLEMENTED

No accounting connector or durable sync pipeline was found.

Required implementation:
1. Select accounting provider and supported country/tax behavior.
2. Customer, tax, invoice and payment mapping.
3. Durable outbox/sync job with idempotency keys.
4. Retry/backoff and dead-letter/manual retry.
5. Reconciliation and error reporting.
6. Tenant-specific credential references and audit.

### 6.10 Payment gateway — PARTIAL

Evidence inspected:
- `PaymentGatewayAdapter.java`
- `FinanceService.java`
- `FinanceController.java`
- `PaymentWebhookEvent.java`
- `V18__release4_finance_and_service.sql`

A provider-neutral adapter interface and signed/deduplicated webhook handling exist. A concrete provider implementation and complete checkout/payment creation journey were not found. Gateway refund execution is not available without an adapter.

Required implementation:
1. Choose provider and obtain sandbox credentials outside source control.
2. Implement provider checkout/payment-intent creation.
3. Verify provider payment status server-to-server.
4. Verify signed webhooks and persist raw event metadata safely.
5. Enforce idempotency across retries and duplicate callbacks.
6. Implement cancellation/refund adapter methods.
7. Reconcile pending/unknown payment outcomes.
8. Test failure, replay, signature mismatch and cross-tenant references.

### 6.11 API integrations — PARTIAL

Evidence inspected:
- `NotificationService.java`
- `PaymentGatewayAdapter.java`
- `application.yml`

Twilio integration and payment webhook foundations exist. Notification delivery includes persisted delivery attempts and retry behavior. A general tenant-aware integration management system was not found.

Required implementation:
1. Provider-neutral adapter registry.
2. Tenant-scoped integration configuration.
3. Secrets stored in a secure secret manager or encrypted references, never plaintext API responses.
4. Timeouts, retries, circuit breaking where appropriate and rate limits.
5. Correlation IDs, health/status and delivery/sync history.
6. Secure webhook registration and signature validation.
7. Duplicate request and idempotency protection.

## 7. Security, RBAC and tenant-isolation findings

Positive foundations observed:
- JWT authentication and database-backed user/tenant revalidation.
- Token-version validation.
- Tenant active-state checks.
- Role/permission resolution.
- Spring method security.
- Tenant-scoped operations and warehouse lookup patterns.
- Signed payment webhook verification.

Gaps:
- No branch scope in principal/authorization.
- No warehouse membership enforcement.
- No subscription entitlement enforcement.
- No centralized audit of all privileged and security actions.
- No Phase 3 cross-tenant/cross-branch test suite.

Required security gates:
1. Resolve tenant and branch from authenticated server-side context.
2. Reject caller-supplied tenant/branch identifiers that do not match authorized context.
3. Scope all entity lookups, list queries, counts, reports, exports and asynchronous jobs.
4. Verify platform support/Super Admin elevation is explicit and audited.
5. Test guessed IDs, changed URL IDs, request-body tenant IDs, filters, pagination, exports and webhook references.
6. Verify disabled vendors and expired subscriptions cannot bypass entitlements.
7. Keep provider secrets out of frontend bundles, logs and response DTOs.

No confirmed cross-tenant exploit was established in the inspected paths. This is not a complete security certification.

## 8. Database and migration findings

Existing migration history demonstrates a substantial Phase 1/2 schema, including tenant, inventory, operations, finance, notifications, surveys, engineers and warranty claims.

Missing Phase 3 schema groups:
- Plan, plan version, subscription, subscription event and usage meter.
- Entitlement and limit definitions.
- Branch and user-branch membership.
- Warehouse-branch mapping and warehouse membership.
- Approval workflow definition, version, step, instance and decision.
- Central audit event.
- Accounting connection, mapping, sync item and reconciliation.
- External integration configuration, webhook registration and delivery state.

Migration requirements:
- Backward-compatible nullable additions and deterministic backfill.
- Foreign keys and unique constraints scoped by tenant.
- Composite indexes beginning with tenant and relevant branch/warehouse keys.
- No destructive data migration without explicit migration strategy.
- Clean-database Flyway validation and upgrade-from-existing-database tests.

## 9. Build, test and runtime validation

Repository configuration contains Maven verification, frontend Vite build and MySQL/Flyway validation workflow definitions. Existing backend integration/security test source files were found.

However, no successful current-HEAD CI execution or runtime test was available during the audit. This report does not claim that the current branch builds, that migrations have been applied to a clean database, or that external providers are connected.

Frontend automated test coverage is not demonstrated by the package scripts inspected. No complete browser E2E coverage was established for Phase 3.

## 10. Phase 1/2 regression risks

High-risk shared foundations that Phase 3 must not break:
- JWT claims and user/tenant revalidation.
- Vendor/customer/internal role permissions.
- Tenant isolation and platform Super Admin support access.
- Customer, product, package, lead, quotation and order workflows.
- Site survey and installation lifecycle.
- Engineer assignment, service and warranty workflows.
- Warehouse stock movement, reservation and compensation.
- Invoice, manual payment, refunds and webhook idempotency.
- Email/SMS notification delivery and retry.

All Phase 3 changes require regression tests against these existing workflows.

## 11. Prioritized implementation sequence

| Order | Workstream | Deliverables | Priority |
|---|---|---|---|
| 1 | Design/security foundation | Confirm tenant/branch/warehouse hierarchy, role model, API contracts and migration/backfill plan | CRITICAL |
| 2 | Subscription/entitlements | Plans, subscription lifecycle, usage metering and enforcement | CRITICAL |
| 3 | Branch management | Branch CRUD, membership and branch-aware authorization/data filtering | CRITICAL |
| 4 | Warehouse enterprise completion | Branch mapping, warehouse permissions, CRUD and reporting | HIGH |
| 5 | Advanced audit | Central append-only audit, before/after, security events and search | CRITICAL |
| 6 | Approval workflow engine | Versioned multi-step workflows, approver routing and decision history | HIGH |
| 7 | Analytics | Correct financial aggregation, platform/vendor/branch analytics and filters | HIGH |
| 8 | Branding/white label | Tenant theme/logo, storefront isolation and verified custom domains | HIGH |
| 9 | Payment gateway | Concrete provider adapter, checkout, verification, webhook, refunds and reconciliation | HIGH |
| 10 | Accounting | Provider adapter, mapping, sync, retries and reconciliation | HIGH |
| 11 | API integration framework | Tenant integration configuration, secure credentials, retries, limits and observability | HIGH |
| 12 | PWA | Manifest, service worker, installability and safe offline behavior | MEDIUM |
| 13 | Additional SRS roadmap | Confirm IoT scope and scale evidence before service decomposition | MEDIUM |
| 14 | Final verification | Clean migrations, backend/frontend build, regression, security and E2E tests | CRITICAL |

## 12. Final acceptance checklist

- [ ] Plan catalogue and versioned plan lifecycle.
- [ ] Vendor subscription activation, renewal, upgrade, downgrade and expiry.
- [ ] Server-side feature entitlement and quantitative limit enforcement.
- [ ] Tenant-specific branding and public storefront isolation.
- [ ] Verified custom-domain mapping.
- [ ] Branch CRUD and user-branch membership.
- [ ] Branch-level authorization and tenant+branch query isolation.
- [ ] Warehouse-to-branch relationship and warehouse-level access.
- [ ] Inventory movements preserve negative-stock and concurrency safeguards.
- [ ] Configurable multi-step approval workflow and decision history.
- [ ] Central append-only audit with before/after values and security events.
- [ ] Authorized audit search/filter/export.
- [ ] Vendor, platform, branch and warehouse analytics.
- [ ] Financial metrics reconcile to payment ledger, refunds and adjustments.
- [ ] Installable PWA and validated offline/session behavior.
- [ ] Accounting customer/invoice/payment synchronization and reconciliation.
- [ ] Working gateway checkout, server verification, webhook idempotency and refunds.
- [ ] Tenant-scoped external integration configuration and secure secret handling.
- [ ] Integration timeout, retry, rate-limit, correlation and error monitoring.
- [ ] Cross-tenant and cross-branch negative security tests.
- [ ] Existing Phase 1/2 regression suite passes.
- [ ] Flyway clean-install and upgrade validation pass.
- [ ] Backend and frontend builds pass on the final commit.
- [ ] End-to-end acceptance flows pass in a deployed/test environment.
- [ ] Product-owner decisions for phase boundary, provider choices and IoT scope recorded.

## 13. Approval and implementation note

This document is an audit/gap baseline, not evidence that the listed gaps have already been implemented. Implementation must proceed in the prioritized sequence, preserve existing Phase 1/2 behavior, and update this document with commit/test evidence as each workstream is completed.

External provider credentials, production domains, accounting provider selection, gateway provider selection and any required commercial plan/pricing decisions must be supplied through secure configuration or confirmed before live integration testing. No secrets belong in Git.


## 14. Post-audit implementation status (follow-up)

This audit remains the historical pre-implementation baseline. Follow-up commits on `feature/phase-3-Enterprise-SaaS` have since added:
- Tenant private evidence storage measurement and quota checks, with a vendor usage endpoint/UI (filesystem-scan based; not yet a durable cross-replica ledger).
- Provider-neutral payment gateway registry, invoice checkout orchestration, customer Pay Online action and adapter-verified webhook normalization. No concrete gateway adapter is installed; checkout remains unavailable until one is deployed and configured. Gateway refund orchestration and settlement reconciliation remain open.
- Provider-neutral accounting adapter contract/registry on top of the existing durable integration queue. No provider-specific adapter or reconciliation is included.
- Tenant/user-partitioned IndexedDB offline queue and server-side idempotent replay for service comments and selected site-survey lifecycle commands. Full conflict resolution, attachments and offline order/payment/inventory actions remain open.
- V41/V42 additive migrations and MySQL workflow validation steps.
- Explicit Lombok annotation processor configuration and corrections identified by CI.

These changes are documented with commit references and validation caveats in `PHASE-3-ENTERPRISE-SAAS-IMPLEMENTATION-REPORT.md`. They do not close the remaining branch propagation/isolation, branch analytics, universal audit coverage, secret-manager lifecycle, full provider refunds/reconciliation, complete offline conflict resolution, IoT scope or final end-to-end acceptance gaps. Do not treat this historical audit as a statement that Phase 3 is fully accepted.
