ALTER TABLE customer_profile
    ADD COLUMN sms_consent BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN whatsapp_consent BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN messaging_consent_updated_at TIMESTAMP(6) NULL;
ALTER TABLE notification_delivery
    ADD COLUMN message_body TEXT NULL;