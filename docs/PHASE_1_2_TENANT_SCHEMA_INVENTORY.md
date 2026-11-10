# Phase 1/2 Tenant Schema and Relationship Inventory

**Branch:** `audit/phase-2-implementation-gap-analysis`  
**Review date:** 2026-11-10  
**Evidence:** Flyway V57/V58 source, domain/repository tree, SRS §8.1–8.4 and §14.2–14.3.

## 1. Classification rules

SRS §8.1 requires every tenant-owned entity to carry a non-null `tenant_id`, while platform-owned global reference data may omit it and must be explicitly classified. `app_user` and `role` may contain platform/global rows; they cannot be blindly constrained to NOT NULL without separating platform identities/roles from vendor-owned rows.

This is a source inventory, not a statement that production data has been scanned. The read-only metadata/data checks are in `docs/PHASE_1_2_TENANT_SCHEMA_AUDIT.sql`.

## 2. Existing composite-FK coverage

- V57: `product_category(tenant_id,id)`, `product(tenant_id,id)`, `solar_package(tenant_id,id)`, `package_item(tenant_id,package_id)`, `package_item(tenant_id,product_id)`, and product-to-category tenant relation.
- V58: sales/CRM (`sales_lead`, `lead_history`, `site_survey`, `site_survey_history`, `site_survey_attachment`); quotation/order (`quotation`, `quotation_item`, `customer_order`, `order_item`, `order_status_history`); stock/operations (`warehouse`, `inventory_balance`, `inventory_transaction`, `inventory_reservation`, `installation_team`, `installation_job`, `installation_checklist_item`, `installation_schedule_history`); warranty/finance (`warranty`, `warranty_claim`, `warranty_claim_attachment`, `invoice`, `invoice_item`, `payment`, `payment_refund`, `credit_note`); service/evidence (`service_request`, `service_visit`, `service_event`, `service_attachment`, `service_comment`); identity/tenant support (`customer_profile`, `branch_user`, `engineer_profile`, `approval_instance`, `approval_decision`, `audit_event`, `notification_delivery`, `offline_sync_operation`, `offline_sync_attachment`, `integration_secret_reference`).
- V58 includes fail-closed checks for NULL tenant IDs on selected sales tables and orphan/cross-tenant references before adding constraints. It does not constitute a full table-wide tenant_id NOT NULL inventory.

## 3. Entity/table inventory requiring database confirmation

The backend domain model includes the following tenant/business areas. The entity tree is not a substitute for `information_schema`: explicit `@Table` names, join tables, views, legacy tables and tables without a current entity class must be reconciled against the actual database.

| Area | Entity families to inventory | Current evidence |
|---|---|---|
| Identity and access | AppUser, Role, Permission, MFA challenge, refresh token, password reset token | Mixed platform/global and tenant-owned; classify by SRS account type before NOT NULL |
| Tenant and subscription | Tenant, TenantDomain, TenantSubscription, SubscriptionPlan, SubscriptionUsage, SubscriptionAssignmentHistory, TenantStorageLedgerEntry | Platform-owned and tenant-associated rows; inspect all composite references |
| Catalogue | ProductCategory, Product, SolarPackage, PackageItem | V57 covers core catalogue tenant relationships |
| CRM and sales | CustomerProfile, CustomerAddress, Enquiry, SalesLead, LeadHistory, SiteSurvey, SiteSurveyHistory, Quotation, QuotationItem | V58 covers many core links; customer address and all enquiry links need schema inventory |
| Orders and inventory | CustomerOrder, OrderItem, OrderStatusHistory, Warehouse, InventoryBalance, InventoryReservation, InventoryTransaction | V58 covers core operational relationships; verify every nullable FK and tenant NOT NULL |
| Installation and service | InstallationTeam, InstallationJob, InstallationChecklistTemplate, InstallationChecklistItem, InstallationScheduleHistory, EngineerProfile, ServiceRequest, ServiceVisit, ServiceEvent, ServiceSlaPolicy, ServiceAttachment, ServiceComment | V58 covers selected operational links; checklist template/SLA and all auxiliary links require explicit review |
| Finance and warranty | Invoice, InvoiceItem, InvoiceSequence, Payment, PaymentWebhookEvent, Refund, CreditNote, Warranty, WarrantyClaim, WarrantyClaimAttachment | V58 covers major finance/service relationships; inspect sequence/webhook/provider/idempotency tables |
| Integrations and offline | IntegrationConnection, IntegrationSyncJob, IntegrationWebhookEvent, OfflineSyncOperation, OfflineSyncAttachment, IdempotencyRecord, IntegrationSecretReference | V58 covers secret reference and offline links; integration connection/job/webhook FKs and tenant nullability need review |
| Audit and notifications | AuditEvent, NotificationDelivery | V58 covers selected actor/tenant relationships; verify event/consent ownership and retention tables |

## 4. CI schema-audit findings and V60 remediation

The read-only schema audit artifact from CI run 42842355967 (fresh disposable MySQL 8.4 schema, before V60) enumerated 74 base tables: 64 have tenant_id and 10 do not. The 10 without tenant_id comprise five platform-global candidates, three user-scoped security tables linked through app_user, and two join tables whose scope is inherited from their parent rows.

The exact 12 tenant_id columns declared nullable in that CI schema were:
- Mixed-scope: app_user, role, audit_event, notification_delivery. These four nullable columns are intentional mixed platform/tenant data; audit_event supports platform events and notification_delivery supports platform authentication messages.
- Tenant-owned: customer_order, idempotency_record, integration_webhook_event, lead_history, order_item, quotation, quotation_item, site_survey.

All eight tenant-owned tables had zero NULL tenant_id rows in that disposable CI dataset, but the schema still allowed NULL. V60 changes these eight columns to NOT NULL and the corresponding JPA entities declare @Column(nullable=false). The migration fails closed if any NULL remains; it performs no backfill or guessed tenant assignment.

Mixed-scope tables remain nullable by design:
- notification_delivery: platform password-reset/MFA messages for global platform users may have NULL tenant_id; business notifications must carry a tenant_id and the recipient user must belong to that tenant.
- app_user: platform users have no tenant; vendor/customer users must be tenant-bound.
- role: PLATFORM_SUPER_ADMIN, PLATFORM_SUPPORT and the shared CUSTOMER role are global; vendor roles must be tenant-bound.
- audit_event: platform audit events may have no tenant; tenant audit events are tenant-bound.

Tables without tenant_id are classified as:
- PLATFORM_GLOBAL candidates: permission, subscription_plan, auth_rate_limit_bucket, payment_webhook_event, and tenant registry.
- USER_SCOPED via app_user: mfa_challenge, password_reset_token, refresh_token.
- JOIN_TABLE scope inherited from parents: role_permission and user_role.

These classifications are grounded in current schema/entity usage but still require data-owner approval. The production preflight now rejects NULL tenant_id in the eight tenant-owned tables and rejects app_user/role/notification_delivery rows whose tenant_id does not match their platform-vs-tenant identity classification (including the global CUSTOMER role). It must be executed against production before V60 rollout.

## 5. Remaining schema proof and release gates

1. Run the protected workflow .github/workflows/phase-2-production-preflight.yml manually from audit/phase-2-implementation-gap-analysis after the production environment approval; execute the read-only SQL against the exact production schema and archive its complete output.
2. Obtain data-owner sign-off for every PLATFORM_GLOBAL, USER_SCOPED, JOIN_TABLE, MIXED and TENANT_OWNED classification above.
3. Confirm every TENANT_OWNED table has tenant_id IS_NULLABLE=NO and exact null_tenant_rows=0 in production.
4. Reconcile every FK and unique index in the audit output against the SRS and migration graph, including join tables and user-scoped security tables.
5. Review production preflight output and backup/change-window approval before applying V57/V58/V60. Never backfill a NULL tenant without a proven parent mapping and approved repair.
6. Run fresh-schema and upgrade-schema migration tests on MySQL 8.4 after V60, then retain production preflight evidence and DBA approval.

## 6. Status

**PARTIAL — Critical.** V57/V58 implement broad composite-FK protection. V60 now enforces NOT NULL for the eight tenant-owned columns that the CI schema inventory found nullable. The disposable MySQL schema audit is complete and exact counts are archived, but production classification, production NULL counts, complete FK/unique-index review and production data preflight remain outstanding. Do not mark P2-TEN-001/P2-TEN-004 MET until production evidence and data-owner/DBA sign-off exist.


## 7. Executable metadata and row-count audit update — 2026-11-10

docs/PHASE_1_2_TENANT_SCHEMA_AUDIT.sql now executes an exact COUNT(*) / SUM(tenant_id IS NULL) query for every base table exposing tenant_id, reports the declared nullability, inventories every FK and unique index, and flags tables with no tenant column. It explicitly labels app_user, role, audit_event and notification_delivery as mixed-scope candidates and does not silently classify all other tables as tenant-owned or global.

This is now executable evidence collection, not yet the requested proof: it must run against the actual production schema and its complete result set must be reviewed. Every table still needs an owner-approved TENANT_OWNED / PLATFORM_GLOBAL / MIXED decision. A tenant-owned table passes only when the column is declared NOT NULL and the exact NULL count is zero. Production data remains unexamined in this session.

- CI run 42844217653 passed the V60 migration and tenant integrity preflight on disposable MySQL 8.4; the schema inventory artifact was emitted. This confirms migration compatibility and clean test data, not production data cleanliness.


### Post-V60 CI schema proof — run 42912143460

The downloaded MySQL 8.4 schema audit artifact `phase-1-2-tenant-schema-inventory` (artifact ID `13808500261`) contains the complete current disposable-schema inventory and exact row counts:

- 74 base tables were enumerated; 64 have a `tenant_id` column and 10 do not.
- Of the 64 `tenant_id` columns, 60 are declared `NOT NULL` and four remain nullable by design: `app_user`, `role`, `audit_event`, and `notification_delivery`.
- The exact count query reports **three NULL `tenant_id` rows in `role` and zero in every other tenant_id-bearing table**. These three rows are the expected platform/global roles (`PLATFORM_SUPER_ADMIN`, `PLATFORM_SUPPORT`, and shared `CUSTOMER`) in this disposable CI dataset. The other three mixed-scope tables have zero NULL rows in this CI dataset.
- Every tenant-scoped table with `tenant_id NOT NULL` has an exact `null_tenant_rows=0` result in this artifact. The artifact also contains the full FK and unique-index inventories.
- The V60 workflow gate separately asserts that the eight migration-target tenant-owned columns are `NOT NULL` and the four mixed-scope columns remain nullable.

This is complete schema and NULL-row evidence for the **disposable CI MySQL 8.4 database only**. It is not production evidence and does not constitute owner approval for the three global role rows or any other classification. Production must be audited with the same read-only SQL and reviewed by the data owner/DBA before release.

The workflow run above has successful MySQL migration/schema jobs, but the overall run's backend test job failed on the then-present public CSRF probe test; do not describe that whole run as green. A later current-head run must pass the full suite.
