-- Phase 4 finance/provider lifecycle: clean-install schema extension.
-- The application is not deployed; existing event rows do not require a data backfill.
ALTER TABLE payment
    MODIFY COLUMN paid_at TIMESTAMP(6) NULL;

ALTER TABLE payment_webhook_event
    ADD COLUMN tenant_id BINARY(16) NOT NULL AFTER id,
    ADD COLUMN provider_reference VARCHAR(150) NOT NULL AFTER provider,
    ADD COLUMN event_type VARCHAR(32) NOT NULL AFTER processing_status,
    ADD COLUMN provider_refund_reference VARCHAR(150) NULL AFTER event_type,
    DROP INDEX uq_payment_webhook_provider_event,
    ADD CONSTRAINT fk_payment_webhook_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
    ADD UNIQUE KEY uq_payment_webhook_tenant_provider_event(tenant_id, provider, event_id),
    ADD INDEX ix_payment_webhook_tenant_received(tenant_id, received_at);

ALTER TABLE payment_refund
    ADD COLUMN provider VARCHAR(60) NULL AFTER recorded_by,
    ADD COLUMN provider_refund_reference VARCHAR(150) NULL AFTER provider,
    ADD COLUMN idempotency_key VARCHAR(160) NULL AFTER provider_refund_reference,
    ADD COLUMN processed_at TIMESTAMP(6) NULL AFTER idempotency_key,
    ADD UNIQUE KEY uq_payment_refund_tenant_idempotency(tenant_id, idempotency_key),
    ADD UNIQUE KEY uq_payment_refund_provider_reference(tenant_id, provider, provider_refund_reference);

CREATE TABLE payment_reconciliation_report(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  provider VARCHAR(60) NOT NULL,
  report_reference VARCHAR(160) NOT NULL,
  currency CHAR(3) NOT NULL,
  reported_total DECIMAL(19,4) NOT NULL,
  matched_total DECIMAL(19,4) NOT NULL DEFAULT 0,
  mismatch_count INT NOT NULL DEFAULT 0,
  status VARCHAR(24) NOT NULL,
  payload_hash VARCHAR(64) NOT NULL,
  created_by BINARY(16) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  processed_at TIMESTAMP(6) NULL,
  CONSTRAINT fk_payment_reconciliation_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_payment_reconciliation_created_by FOREIGN KEY(tenant_id,created_by) REFERENCES app_user(tenant_id,id),
  CONSTRAINT uq_payment_reconciliation_report UNIQUE(tenant_id,provider,report_reference),
  CONSTRAINT uq_payment_reconciliation_tenant_id UNIQUE(tenant_id,id),
  INDEX ix_payment_reconciliation_tenant_created(tenant_id,created_at)
);

CREATE TABLE payment_reconciliation_line(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  report_id BINARY(16) NOT NULL,
  provider_reference VARCHAR(150) NOT NULL,
  amount DECIMAL(19,4) NOT NULL,
  currency CHAR(3) NOT NULL,
  outcome VARCHAR(32) NOT NULL,
  payment_id BINARY(16) NULL,
  details VARCHAR(500) NULL,
  CONSTRAINT fk_payment_reconciliation_line_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_payment_reconciliation_line_report FOREIGN KEY(tenant_id,report_id) REFERENCES payment_reconciliation_report(tenant_id,id),
  CONSTRAINT fk_payment_reconciliation_line_payment FOREIGN KEY(tenant_id,payment_id) REFERENCES payment(tenant_id,id),
  INDEX ix_payment_reconciliation_line_tenant(tenant_id,provider_reference),
  INDEX ix_payment_reconciliation_line_report(tenant_id,report_id)
);
