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
| Finance and warranty | Invoice, InvoiceItem, InvoiceSequence, Payment, PaymentWebhookEvent, PaymentGatewayWebhookEvent, Refund, CreditNote, Warranty, WarrantyClaim, WarrantyClaimAttachment | V58 covers major finance/service relationships; inspect sequence/webhook/provider/idempotency tables |
| Integrations and offline | IntegrationConnection, IntegrationSyncJob, IntegrationWebhookEvent, OfflineSyncOperation, OfflineSyncAttachment, IdempotencyRecord, IntegrationSecretReference | V58 covers secret reference and offline links; integration connection/job/webhook FKs and tenant nullability need review |
| Audit and notifications | AuditEvent, NotificationDelivery, CustomerMessagingConsent | V58 covers selected actor/tenant relationships; verify event/consent ownership and retention tables |

## 4. Known gaps to close

1. Execute the read-only SQL against the exact production-like MySQL schema and archive its output with the release evidence.
2. Compare every actual base table with the domain/JPA mapping and SRS entity list; include join tables, webhook tables, idempotency records, and legacy tables.
3. For each table, classify `TENANT_OWNED`, `PLATFORM_GLOBAL`, or `MIXED`; document the reason and owner.
4. For every tenant-owned table, prove `tenant_id IS NOT NULL` and zero NULL rows. Do not make `app_user` or `role` globally NOT NULL without separating platform/global rows.
5. Inventory every FK between tenant-owned entities and require a composite FK on `(tenant_id, referenced_id)` where applicable. Document exceptions such as globally shared reference data.
6. Inventory tenant-scoped unique constraints and verify each business natural key includes `tenant_id`.
7. Add new Flyway migrations only after the inventory and NULL/orphan preflight have been reviewed. Backfill only when the parent relationship unambiguously identifies the tenant; otherwise fail closed and require operator remediation.
8. Run fresh-schema and upgrade-schema migration tests on MySQL 8.4, then run the same read-only preflight against an approved production snapshot.

## 5. Status

**PARTIAL — Critical.** V57/V58 implement broad composite-FK protection and their CI preflight has passed on a disposable MySQL database. The complete live schema inventory, tenant-owned table NOT NULL proof, all-FK graph, unique-index inventory and production-data preflight are not yet executed. Do not mark P2-TEN-001/P2-TEN-004 MET until those artifacts exist and pass.
