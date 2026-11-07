# Release 4 and Platform Administration Requirement Traceability

Baseline: `feature/release-3-operations` at `9ad36cf1abc5fe092a1113488748f15f07259dba`.

Source documents were read from the baseline branch:
- `RELEASE_PLAN.md` (five-release strategy, cross-release controls, Release 4 scope/sequence/design/acceptance, Release 5 dashboards/reporting/audit, testing and release gates)
- `saumya-solar-energy-solution-requirements.md` (roles, customer/vendor/platform screens, order/payment journey, service lifecycle, vendor lifecycle, tenant isolation, reporting, production security and audit)
- `saumya-solar-production-ready-srs-hld.md` (RBAC catalogue, FIN-001..FIN-007, SVC-001..SVC-007, API contracts, database constraints, screen catalogue, workflows, security, NFRs and acceptance scenarios)

## Platform Super Admin dashboard and tenant reporting

| Requirement | Baseline status | Evidence / gap |
|---|---|---|
| Platform login and role-aware route | Partial | `frontend/src/main.tsx` dispatches on account type, but platform home renders a generic CRUD screen. |
| Dashboard metrics and recent tenants | Missing | No platform dashboard endpoint or dedicated dashboard UI. |
| Searchable/paginated tenant list | Partial | `GET /api/v1/tenants` returns all tenants; no search, page metadata or page controls. |
| Tenant administrator identity and audit dates | Missing | Tenant table/entity has name/slug/status only; no timestamps or administrator projection. |
| View/edit tenant | Missing | Status endpoint exists; no tenant details or safe edit endpoint. |
| Activate/deactivate/suspend | Partial | Existing lifecycle supports ACTIVE/SUSPENDED only; status controls are embedded in generic CRUD. |
| Create tenant flow returns to dashboard | Partial | Generic CRUD form remains visible; no dedicated create/edit modal/route lifecycle. |
| CSV/XLSX/PDF exports with applied filters | Missing | No tenant export endpoint or UI. |
| Logo navigation by authenticated role | Missing | Header logo is a non-interactive image. |

## Release 4: Finance and service

| ID / capability | Baseline status | Evidence / gap |
|---|---|---|
| FIN-001 Invoice generation from order milestones | Missing | No invoice entity, repository, service, API or screen. |
| FIN-002 Tenant/fiscal-sequence invoice numbering | Missing | No invoice table or numbering configuration. |
| FIN-003 Immutable issued invoices; void/reissue/credit-note | Missing | No financial document lifecycle. |
| FIN-004 Manual/offline payments and future gateway callbacks | Missing | No payment persistence/API. |
| FIN-005 Webhook deduplication | Missing | No provider event store or callback processing. |
| FIN-006 Customer invoice PDF and receipts | Missing | Existing PDF support is quotation-only. |
| FIN-007 Financial audit | Missing | No finance audit event model; existing order/inventory history is not sufficient. |
| Provider-neutral payment adapter | Missing | No adapter contract. Provider selection is explicitly an open business decision; no provider credentials or production gateway may be invented. |
| Webhook signature verification and reconciliation | Missing | No webhook endpoint or verification configuration. |
| Service request create/list/detail | Missing | No service ticket model or API. |
| Ticket category, priority, asset/serial, warranty, attachments | Missing | Warranty and installation exist, but no ticket snapshot/attachment relationship. |
| Assignment and service state machine | Missing | Existing service roles/permission only; no service workflow. |
| Service visits, scheduling, notes and evidence | Missing | No service visit entity or UI. |
| SLA policy, clocks, pauses, breaches and escalation | Missing | No SLA model/job. Business hours/holidays are unspecified and must remain configurable, not hard-coded as an asserted policy. |
| Parts consumed via immutable inventory ledger | Partial foundation | `OperationsService` has immutable inventory movements and stock issue primitives; no service-visit reference or atomic parts workflow. |
| Customer confirmation and controlled closure/reopen | Missing | No service ticket/visit workflow. |
| Refund/cancellation authorization, reason and amount controls | Missing | No refund model/API; order cancellation is separate and does not implement payment reversal. |
| Customer portal invoice/payment/service screens | Missing | Customer UI shows orders, installations and warranties only. |
| Vendor finance/service screens | Missing | Vendor UI has operations/settings, but no invoice/payment/service tabs. |

## Implementation checklist

- [ ] Tenant dashboard API: status totals, recent tenants, search and server-side pagination.
- [ ] Tenant detail/edit API with allow-listed mutable fields and platform-only authorization.
- [ ] CSV, XLSX and PDF export using the exact current search/status filter.
- [ ] Dedicated Platform dashboard UI; create/edit return to dashboard and refresh results.
- [ ] Central role-aware logo destination using current session and existing account-type routing.
- [ ] Versioned Flyway migration for tenant audit timestamps and finance/service schema.
- [ ] Invoice snapshots, controlled fiscal numbering, PDF, send/void and credit-note representation.
- [ ] Manual payments, immutable payment events, receipts and overpayment prevention.
- [ ] Provider-neutral adapter port and signed, idempotent webhook event handling; real gateway activation remains configuration-dependent.
- [ ] Service ticket lifecycle, tenant/customer ownership and assignment controls.
- [ ] SLA policy and deterministic elapsed-time calculations; configurable working-hours/holiday calendar.
- [x] Service visit scheduling, private resolution evidence and customer confirmation.
- [x] Service parts consumption through the existing inventory balance/ledger transaction in the same database transaction.
- [ ] Refund and cancellation workflow with reason, amount limits, permission and audit.
- [ ] Vendor and customer finance/service UI, validation, loading/empty/error states.
- [x] Regression tests for platform export, service state/tenant ownership and private attachment access; finance webhook/refund and full parts-consumption scenario tests remain to be expanded.
- [x] Backend and frontend build, Flyway MySQL 8.4 migration, application startup and regression CI (latest head run must be checked after final commit).
- [x] README, API/role matrix and environment configuration updated.

## Explicit open business/provider decisions

The source SRS marks the following as business decisions: legally approved GST invoice fields and fiscal-year numbering, payment gateway and settlement model, refund thresholds, and service SLA business hours/holiday calendar. The implementation must preserve these as tenant/platform configuration or adapter configuration. It must not claim statutory GST compliance, choose a real gateway, or assume business hours without approval. Gateway adapter contracts and webhook security can be implemented independently of provider selection.


## Current implementation result (branch audit)

| Requirement | Backend | Frontend | Database | Validation / remaining |
|---|---|---|---|---|
| Platform dashboard totals, recent tenants, search/pagination | Implemented | Implemented | V17 timestamps | Integration tests added; current status values are ACTIVE/SUSPENDED only. |
| Tenant view/edit and activate/suspend | Implemented | Implemented | Existing lifecycle + V17 | Edit deliberately permits name only; slug/admin identity are protected. |
| Tenant CSV/XLSX/PDF exports | Implemented | Implemented | Real tenant/admin records | Integration tests cover formats, filter and role denial. |
| Role-aware logo home | Implemented by account-type root dispatch | Implemented centrally in header | No change | Refresh uses existing stored session and 401 handling. |
| FIN-001 invoice issue from eligible order | Implemented | Implemented | V18 invoice/sequence/items | Transactional order lock, fiscal sequence, one invoice per order. |
| FIN-002 tenant/fiscal invoice number | Implemented | Implemented | V18 invoice_sequence unique tenant/year | India FY boundary is currently Asia/Kolkata; prefix defaults to INV. |
| FIN-003 immutable issued invoice correction | Partial | Partial | V20 credit_note | No edit API; void and credit-note workflows exist. Replacement invoice after void is not yet supported because current design keeps one invoice per order. |
| FIN-004 manual/offline payment | Implemented | Implemented | V18 payment | Overpayment rejected and invoice balance updated. |
| FIN-005 duplicate signed payment webhook | Partial | Not applicable | V18 payment_webhook_event unique provider/event | HMAC and deduplication exist. Real provider checkout/adapter implementation is pending provider selection; unmatched events are retained for reconciliation. |
| FIN-006 customer invoice PDF and receipts | Implemented | Implemented | V18 invoice/payment snapshots | Customer ownership enforced. |
| FIN-007 finance audit | Partial | Partial | V18 payment/refund/credit records | Financial records retain actor and timestamps; a unified platform audit-log search/export UI is not implemented. |
| SVC-001 customer/vendor ticket creation | Implemented | Implemented | V18 service_request | Customer self-service and tenant-scoped vendor create. |
| SVC-002 category, priority, installation/product/serial/warranty and evidence attachments | Implemented | Implemented | V18 + V22 | Private JPEG/PNG/WebP/PDF evidence upload/download is authorized by tenant, customer ownership or engineer assignment. |
| SVC-003 manager assignment and visit schedule | Implemented | Implemented | V18 service_visit | Same-tenant enabled service-engineer validation. |
| SVC-004 ticket state machine | Implemented | Implemented | V18 service_event | OPEN→ASSIGNED→VISIT_SCHEDULED→IN_PROGRESS→RESOLVED→CUSTOMER_CONFIRMED→CLOSED. |
| SVC-005 customer reopen by tenant policy | Implemented | Implemented | V18 service_reopen_days | 0 disables; Vendor Admin configures 0–90 days. |
| SVC-006 configurable SLA by priority, pause and breach | Partial | Partial | V18 + V21 calendar fields | Configurable calendar, pause/resume and one-time breach events exist; outbound escalation notifications and escalation policies remain open. |
| SVC-007 resolution, parts, evidence, confirmation | Implemented | Implemented | V18 + V22 | Parts consume stock and immutable ledger atomically; private service evidence is stored outside public uploads. |
| Refund authorization and amount controls | Implemented for manual payments | Implemented | V18 payment_refund | Requires PAYMENT_REFUND and reason; gateway refunds await provider adapter. |
| Tenant isolation and customer ownership | Implemented | Existing role navigation | Tenant foreign keys/indexes | Regression tests cover cross-customer ticket detail denial and platform/vendor boundary. |

## Latest CI validation

The branch workflow is configured to run:
- Maven compile and full backend tests
- React TypeScript/production build
- V1–V22 Flyway migration sequence on MySQL 8.4
- Spring Boot startup with Hibernate schema validation against MySQL 8.4

Do not treat this release as production-complete until the latest PR run reports all jobs green and the remaining decisions/limitations above are resolved.
