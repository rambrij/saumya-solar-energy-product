ALTER TABLE lead_history
    ADD COLUMN event_type VARCHAR(30) NOT NULL DEFAULT 'STAGE',
    ADD COLUMN old_assigned_user_id BINARY(16) NULL,
    ADD COLUMN new_assigned_user_id BINARY(16) NULL;

CREATE INDEX ix_lead_history_tenant_lead_created
    ON lead_history(tenant_id, lead_id, created_at);
