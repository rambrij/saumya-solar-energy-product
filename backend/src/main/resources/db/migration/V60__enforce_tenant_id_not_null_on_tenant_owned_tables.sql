-- Phase 2 tenant ownership hardening: tenant-owned rows must always carry tenant_id.
-- V58 preflight (extended for V60) runs before this migration. These ALTERs intentionally
-- fail closed if any legacy NULL tenant_id remains; no tenant value is guessed or backfilled.
ALTER TABLE customer_order MODIFY tenant_id BINARY(16) NOT NULL;
ALTER TABLE order_item MODIFY tenant_id BINARY(16) NOT NULL;
ALTER TABLE quotation MODIFY tenant_id BINARY(16) NOT NULL;
ALTER TABLE quotation_item MODIFY tenant_id BINARY(16) NOT NULL;
ALTER TABLE site_survey MODIFY tenant_id BINARY(16) NOT NULL;
ALTER TABLE lead_history MODIFY tenant_id BINARY(16) NOT NULL;
ALTER TABLE notification_delivery MODIFY tenant_id BINARY(16) NOT NULL;
ALTER TABLE integration_webhook_event MODIFY tenant_id BINARY(16) NOT NULL;
ALTER TABLE idempotency_record MODIFY tenant_id BINARY(16) NOT NULL;
