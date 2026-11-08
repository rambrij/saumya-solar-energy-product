ALTER TABLE notification_delivery
    ADD COLUMN read_at TIMESTAMP(6) NULL;

CREATE INDEX ix_notification_delivery_user_created
    ON notification_delivery(user_id, created_at);
