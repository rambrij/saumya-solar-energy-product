ALTER TABLE tenant
    ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
UPDATE tenant SET updated_at=created_at WHERE updated_at IS NULL;
CREATE INDEX ix_tenant_status_created ON tenant(status, created_at);
