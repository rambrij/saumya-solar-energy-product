-- Complete the durable delivery schema required by NotificationDelivery.
-- Existing email rows remain EMAIL and retain their existing delivery status.
ALTER TABLE notification_delivery
    ADD COLUMN channel VARCHAR(20) NOT NULL DEFAULT 'EMAIL',
    ADD COLUMN idempotency_key VARCHAR(120) NULL,
    ADD COLUMN provider_message_id VARCHAR(120) NULL,
    ADD COLUMN next_attempt_at TIMESTAMP(6) NULL;

CREATE UNIQUE INDEX uq_notification_delivery_tenant_idempotency
    ON notification_delivery (tenant_id, idempotency_key);

CREATE INDEX ix_notification_delivery_retry
    ON notification_delivery (status, next_attempt_at, created_at);

CREATE INDEX ix_notification_delivery_provider_message
    ON notification_delivery (provider_message_id);
