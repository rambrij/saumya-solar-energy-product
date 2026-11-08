# Phase 3 Enterprise SaaS — Implementation Report

- Repository: `rambrij/saumya-solar-energy-product`
- Branch: `feature/phase-3-Enterprise-SaaS`
- Implementation baseline: `f415e78010723f78b4ed419c860d0b6e62c218ee`
- Related audit: `PHASE-3-ENTERPRISE-SAAS-GAP-ANALYSIS.md`
- Stack retained: Spring Boot 3 / Java 21, React / TypeScript, MySQL, Flyway
- Scope: implement approved Phase 3 gaps while preserving existing Phase 1/2 behavior

## 1. Delivery summary

The Phase 3 branch now contains the additional SaaS, tenant administration, workflow, integration, usage and PWA capabilities listed below. The existing audit report remains unchanged as the historical pre-implementation baseline.

**Validation at implementation baseline:** all four GitHub Actions jobs passed:
- Backend Maven compile and tests
- Frontend TypeScript check and production build
- Flyway migration validation against MySQL 8.4
- Spring Boot startup/smoke test against MySQL 8.4 and Mailpit

Workflow: https://github.com/rambrij/saumya-solar-energy-product/actions/runs/42504271629

A passing CI pipeline verifies the automated checks above; it is not a production penetration test, live payment-provider certification, or proof of every business workflow in a deployed environment.

## 2. Implemented capabilities

### 2.1 Subscription and entitlement foundation
- Subscription plan catalogue creation and deactivation.
- Tenant subscription assignment and cancellation.
- Scheduled subscription transitions preserve the currently effective subscription until the scheduled start and do not extend an earlier existing end date.
- Current subscription resolution prefers the effective subscription over a future scheduled subscription.
- Scheduled expiry transitions for expired trial/active/past-due subscriptions, with audit events.
- Server-side feature gates for branches, multiple warehouses, branding, custom domains, approval workflows and configured external integration categories.
- Quantitative limits for vendor users, active branches and active warehouses.
- Monthly usage snapshots and vendor usage endpoint/UI for active users, branches and warehouses.
- Tenant row locking is used around key count/limit and usage-snapshot paths to reduce concurrent over-allocation and duplicate meter writes.

### 2.2 Branch and warehouse scope
- Tenant-scoped branch create, update, activate/deactivate and list operations.
- Branch membership assignment, listing and removal.
- Membership assignment validates that the user is enabled, belongs to the same tenant and is a vendor account.
- Branch-bound warehouse access is checked against the caller's branch memberships.
- Inventory warehouse lists, balances, ledger and low-stock reporting filter branch-bound warehouses for non-admin users.
- Warehouse-to-branch assignment validates tenant ownership and required permission.
- Existing unassigned legacy warehouses remain tenant-wide for backward compatibility.

### 2.3 Branding and custom domains
- Tenant branding updates validate logo URL schemes and brand colors.
- Custom-domain registration and DNS TXT verification.
- Verified host/origin resolution to the correct active tenant storefront.
- Public storefront bootstrap resolves a verified custom domain before falling back to the configured store slug.
- Custom-domain and branding capabilities are gated by subscription entitlements.

### 2.4 Approval workflows
- Tenant-scoped configurable multi-step approval workflows.
- JSON step validation, role/permission checks and optional amount bounds.
- Approval request listing and decision-history endpoints.
- Tenant-scoped, pessimistically locked decisions to serialize concurrent approvals.
- Idempotent pending-request creation for the same entity.
- Existing quotation approval flow is connected to configured workflows.
- Vendor UI supports workflow configuration, pending-request review and approve/reject actions.

### 2.5 Audit
- Tenant/platform audit search supports date range, actor, action, entity type and outcome filters.
- Search range is validated and capped at 90 days; results are bounded.
- Enterprise subscription, branch, branding, domain, approval and integration operations write audit events.
- Vendor/platform UI exposes the audit search filters and recent results.

### 2.6 External integrations and accounting event delivery
- Tenant-scoped integration connection registry with active/disabled lifecycle.
- Configuration JSON rejects embedded credentials; secret references are limited to supported `env://VARIABLE` references.
- Durable sync-job persistence, tenant-scoped idempotency keys, retry and dead-letter/waiting-for-adapter states.
- Atomic job claims prevent multiple workers from processing the same due job concurrently.
- Abandoned `PROCESSING` jobs can be reclaimed after a stale-processing interval.
- Generic HTTPS adapter enforces HTTPS/standard port, disables redirects, rejects private/local DNS targets and uses timeouts.
- Integration UI supports connection management, job queueing, status review and retry.
- Accounting sync events are queued for order, invoice, payment and refund changes where an active accounting integration is configured.

### 2.7 PWA
- Existing manifest/service-worker app-shell support retained.
- Install prompt is surfaced when the browser supports PWA installation.
- Offline status is shown to users; account, enquiry and business actions continue to require a network connection.
- Private business API data is not added to the public service-worker cache.

## 3. Remaining gaps and acceptance caveats

The implementation is materially improved but **Phase 3 should not yet be represented as 100% complete**. The following capabilities still require work or a product/provider decision:

| Area | Remaining limitation |
|---|---|
| Storage metering | **Partially implemented in follow-up commit:** private service-ticket, survey and warranty evidence bytes are measured per tenant; upload quota is checked against the active plan under a tenant row lock; vendor usage endpoint/UI shows used/remaining MB. Still filesystem-scan based (not a durable usage ledger), excludes non-evidence assets such as package images, and needs concurrent-upload/MySQL runtime validation. |
| Branch isolation | Branch authorization is enforced for branch-bound warehouses/inventory. Orders, quotations, customers, invoices, installations and service records are still primarily tenant-scoped and do not all carry/enforce a branch context. |
| Branch analytics | Complete branch-level sales, finance, warehouse and operational dashboards are not implemented. Existing operational reporting and new subscription usage meters are partial coverage. |
| Central audit | Audit coverage is improved for Phase 3 operations, but it is not yet a universal before/after audit layer for every business mutation, failed login, denied access and security event. Export/correlation-ID coverage is also incomplete. |
| Accounting provider support | **Provider-neutral port/registry added** for invoice, payment and refund synchronization, on top of the existing durable integration queue and generic HTTPS adapter. No concrete accounting adapter is installed; provider-specific mappings, reconciliation, external ID lifecycle and certification remain outstanding. |
| Payment provider | **Provider-neutral flow added:** payment gateway registry, idempotent hosted-checkout orchestration endpoint, customer invoice Pay Online action, signed provider webhook verification/parsing contract and payment/invoice state update. No concrete provider adapter is installed; checkout correctly returns unavailable until a tenant has an active gateway connection and a matching server-side adapter. Provider-specific refunds/reconciliation remain open. |
| Secret management | The generic adapter supports server-side environment references. A vault/KMS-backed tenant secret resolver, rotation and per-tenant secret lifecycle are not implemented. |
| Integration resilience | Generic HTTP delivery is durable and retried, but provider-specific rate limits, circuit breakers, health dashboards and contract tests remain future work. |
| PWA offline behavior | **Limited offline business-data queue added:** IndexedDB queue scoped by tenant/user, online replay and server-side idempotency for service comments and selected site-survey actions. It does not yet support all business modules, multi-device conflict resolution, attachments, or offline inventory/payment/order mutations. |
| IoT | IoT scope and release acceptance are ambiguous between the reference documents; implementation requires product-owner clarification. |

## 4. Security and compatibility notes

- Existing tenant-scoped repository access and JWT/RBAC foundations were retained.
- Branch membership does not replace tenant authorization; both tenant and branch checks are applied where branch-scoped warehouse operations are used.
- Generic integration endpoints are treated as untrusted tenant input and are restricted to HTTPS on the standard port, with redirects disabled and non-public address resolution rejected.
- Secrets must be configured in the server environment; secret values are never accepted in integration configuration JSON.
- Existing Phase 1/2 tenants without assigned subscriptions are grandfathered in selected limit/feature paths to avoid unexpectedly blocking established workflows. New/assigned subscriptions are subject to their configured entitlements.
- No production payment credentials, accounting credentials or DNS control-plane access were added to the repository.

## 5. Validation and next acceptance steps

Automated validation passed at the implementation baseline listed above. Before production release, complete:
1. Branch-context propagation and isolation tests for sales, finance, installation and service records.
2. Tenant storage metering and storage-limit enforcement.
3. Central audit coverage and security-event capture.
4. Provider selection plus provider-specific accounting and payment adapter contract tests.
5. Full browser end-to-end tests for subscription, branch, branding/domain, approval and integration workflows.
6. Production DNS verification, deployment smoke tests and tenant cross-boundary penetration testing.
7. Product-owner decision on IoT scope and any phase-boundary conflicts.

## 6. Change safety

- Work was committed only to `feature/phase-3-Enterprise-SaaS`.
- No changes were intentionally made to `main`.
- Existing Phase 1/2 architecture and workflows were preserved; the automated backend tests, frontend build, MySQL migration validation and Spring Boot/Mailpit smoke test passed at the implementation baseline.


## 7. Follow-up implementation — tenant evidence storage metering

**Commits:** `271885b162fdd6d1d706f63e46ffe291fff12fdf`, `01568de113e1447d0b870403a927d6184cdc67f3`, `10441c85fb0470fe2f73eeacc27fb00538608254`, `9b5fc84efea3bf262d5cefd426a0e7640a63339f`, `824d35e296a84773f523c66f5406c1546fb5fca0`, `10e8d71f094e25d8a8dc2e7177d9747f0f735f40`, `a87f83d64e1c2ad3ee74d963207b4b897b0d4cc7`, `584900e34ced85e6e499ed9f51023da36b3519b0`.

- Added tenant storage entitlement resolution from the effective subscription plan; existing tenants without an assigned subscription remain grandfathered.
- Added filesystem metering across private service, survey and warranty evidence roots.
- Enforced storage quota before a private evidence upload. The tenant row is locked in the upload transaction to serialize quota checks for concurrent uploads by the same tenant.
- Added `GET /api/v1/subscription/storage` for tenant-scoped storage usage and quota summary.
- Added a vendor UI panel showing used MB, plan limit and remaining MB.
- Added unit tests for multi-root byte measurement and quota rejection.

**Validation caveat:** These follow-up commits have not yet been verified by a completed CI run in this report. The implementation uses a filesystem scan rather than a transactional storage ledger, so it is suitable as an incremental guard for the current single-instance persistent-volume deployment, not a final multi-replica metering architecture. Package images and any other non-evidence file stores are not yet included. A durable ledger, reconciliation job, storage deletion accounting, multi-instance shared storage strategy and concurrency test against MySQL remain required for full acceptance.

## 8. Remaining Phase 3 acceptance work

The following items remain open and must not be marked accepted solely because a branch, warehouse or audit foundation exists:

1. Propagate and enforce branch context for orders, quotations, customers, invoices, payments, installations, service tickets, reporting and background jobs. Existing branch authorization is limited primarily to branch-bound warehouses/inventory.
2. Add branch-aware sales/finance/operations analytics after branch assignment rules and historical-data backfill are defined.
3. Extend audit capture to all business mutations, before/after snapshots, authentication failures, denied access, security events and correlation IDs; redact secrets and sensitive personal data.
4. Add selected accounting and payment provider implementations, provider-specific contracts, webhook verification, refund flows and reconciliation. Provider selection and sandbox credentials are external prerequisites.
5. Replace environment-only secret references with a selected Vault/KMS/Secrets Manager integration, rotation and audit lifecycle.
6. Add integration rate limiting, provider health, circuit breaking and provider contract tests.
7. Decide whether offline business-data queueing/conflict resolution is in Phase 3; the current PWA is intentionally app-shell/offline-indicator only.
8. Add complete browser E2E, cross-tenant/cross-branch negative tests, clean-install and upgrade migration checks, and deployed smoke validation.
9. Confirm IoT acceptance scope and any accounting/payment providers with the product owner.

Do not claim 100% Phase 3 acceptance until these items and the follow-up validation gates are completed.


## 9. Follow-up implementation — provider-neutral payment/accounting and offline business sync

**Implementation commits:** `a5e39001fe47f8fed9bc05b0fc38e686789d9c12`, `6503d254dc2888a19ee04b8ab94f1887741e963d`, `828ff2b7312dd3eecd38169efe92540f6f4ec8af`, `5135ebda73641c0767242ec04e24fa80db6d297d`, `cbc13ed0cb7c9ebad806963a963e11c3aafa7bef`, `36b7bd9a9df1d9ce37221c6c8877b11220edb7ca`, `a1f330e4737416dcddd8f7409b4b3f942d52a09d`, `9ff4d75af2391715892ef0152baa0a68366c1d78`, `a69e7e175c6edfe6da032e5c2980c27dc393b3d5`, `8c607c4c3caf0d2d28fd3c5d2fad40b7114f08cf`, `a09fbd4d98dd992af6f74c93ffaabd96df066664`, `772d61c85416ee788b977c0b3e64a45fbfa07f0d`, `a14746a0bd5e15f17d05f533397b5ed4139b20f4`, `3f4b3f5ca42b1b83588bfb6544be8ecfea7f2023`, `ff3df62b8891a2347c9af9c9388eefe4039bc376`, `fe2c9e93a715f830882aa02bd653eae0c9b19a6a`, `ac482cd264c04cf07b5b1c1d3a24d2972287322d`, `b1aed3429e3d8200ff922f3a4b4db71514d9663b`, `654cf51c5564f86e8eb039a5bf0b41bf6ffb0309`, `d6069be7a75e3cef888c7f165f817aa0e84e4db7`, `88d7053b3083fa38dadbebdce7f3faeed30c282c`, `a9df92bc9e9f9aa6e1862e1db7146799b2f5bcb9`, `b97862e82907f6cbec6a9bbe27b3af5dd96335b8`, `dce7c4c881744fb784617cbe9b03cbb4912108d2`, `fb7e3a55ef6d281eca7aca0e29acb7576be3757b`, `96817d728f3202d75c1f770ace351379e96b8716`, `d66d36483dfe1db4b2b0b38ff02a391dcd1db5a2`, `8d5ba703973adcde75a1781826048fc7804607c2`.

### Offline business-data synchronization
- Added additive Flyway V41 inbox table with tenant, actor, client operation ID, resource ID, payload hash and applied result.
- Added authenticated batch/status APIs. Batches are limited to 50 commands and payloads/timestamps are validated.
- Commands are allowlisted: service comment create, site-survey update, start and submit.
- The client uses IndexedDB; queued items are partitioned by tenant and user and replayed with an operation ID.
- The server persists an applied result and returns the stored result on a duplicate replay; reusing an operation ID with a different payload is rejected.
- Existing survey lifecycle and service-ticket authorization are reused; customer-visible vendor comments continue to use the durable notification pipeline.
- Sensitive API responses and uploads remain excluded from the service-worker cache.

**Offline limitations:** no general-purpose conflict resolution, cross-device queue replication, attachment upload queue, order/payment/inventory commands or server-side stale-record merge policy are included. Users should review failed commands and retry only after resolving the reported business validation conflict.

### Provider-neutral payment
- Extended the payment gateway port with normalized webhook parsing and added a registry for installed adapters.
- Added invoice checkout orchestration with tenant subscription entitlement, invoice ownership checks, outstanding-balance calculation, HTTPS checkout URL validation and deterministic payment ID/idempotency handling.
- Added customer portal Pay Online action.
- Added signed provider webhook route; the installed adapter is responsible for signature verification and normalization. The server matches provider reference, amount and currency before confirming payment and updating invoice status.
- Added additive Flyway V42 columns for checkout URL and tenant-scoped idempotency key.

**Payment limitations:** no provider adapter is bundled or configured by this request. A configured integration alone is insufficient; the corresponding server-side `PaymentGatewayAdapter` implementation must be deployed. Until then checkout returns a controlled service-unavailable response. Gateway refund execution, refund webhook lifecycle, settlement reconciliation and provider sandbox certification remain open.

### Provider-neutral accounting
- Added `AccountingAdapter` contract and registry for canonical invoice, payment and refund synchronization.
- Existing durable tenant integration jobs, idempotency, retry and dead-letter behavior remain the transport.
- No Zoho/Tally/SAP or other provider implementation was added, as requested.

**Accounting limitations:** provider mappings, tax-ledger mapping, reconciliation, external-reference lifecycle and provider-specific contract tests require a concrete adapter implementation.

### Build and validation follow-up
- Added explicit Lombok annotation processor configuration for deterministic Java 21 builds.
- Corrected the offline batch DTO type annotation and test import based on GitHub Actions compiler findings.
- Added V41 and V42 MySQL 8.4 migration steps to the release validation workflow.
- Frontend typecheck/build passed on an intermediate commit. Backend test compilation initially exposed a missing test import, which was corrected in the subsequent commit. The latest workflow must finish before any passing result is claimed.
