ALTER TABLE tenant
    ADD COLUMN legal_name VARCHAR(180) NULL,
    ADD COLUMN contact_phone VARCHAR(20) NULL,
    ADD COLUMN registered_address VARCHAR(500) NULL,
    ADD COLUMN contact_email VARCHAR(190) NULL,
    ADD COLUMN website VARCHAR(255) NULL;
