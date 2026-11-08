ALTER TABLE notification_delivery
    ADD COLUMN body LONGTEXT NULL,
    ADD COLUMN next_attempt_at TIMESTAMP(6) NULL;

UPDATE notification_delivery
SET next_attempt_at = created_at
WHERE status IN ('PENDING', 'FAILED') AND next_attempt_at IS NULL;

ALTER TABLE notification_delivery
    MODIFY COLUMN status VARCHAR(20) NOT NULL;

ALTER TABLE notification_delivery
    DROP CHECK chk_notification_delivery_status;

ALTER TABLE notification_delivery
    ADD CONSTRAINT chk_notification_delivery_status
    CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED', 'DEAD'));

CREATE INDEX ix_notification_delivery_due
    ON notification_delivery(status, next_attempt_at, created_at);

-- Legacy synchronous deliveries did not persist their payload and cannot be retried safely.
UPDATE notification_delivery
SET status = 'DEAD', last_error = 'Legacy delivery has no persisted message payload'
WHERE body IS NULL AND status IN ('PENDING', 'FAILED');
