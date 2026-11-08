ALTER TABLE tenant
    ADD COLUMN verification_status VARCHAR(30) NOT NULL DEFAULT 'VERIFIED',
    ADD COLUMN verified_at TIMESTAMP(6) NULL,
    ADD COLUMN verified_by BINARY(16) NULL,
    ADD COLUMN rejection_reason VARCHAR(1000) NULL,
    ADD CONSTRAINT fk_tenant_verified_by FOREIGN KEY (verified_by) REFERENCES app_user(id);

-- Existing active tenants were already provisioned and remain verified.
UPDATE tenant SET verification_status='VERIFIED', verified_at=COALESCE(updated_at,created_at)
WHERE status='ACTIVE';
