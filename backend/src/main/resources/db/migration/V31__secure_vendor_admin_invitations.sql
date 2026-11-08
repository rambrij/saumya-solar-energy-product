CREATE TABLE vendor_invitation (
    id BINARY(16) NOT NULL PRIMARY KEY,
    tenant_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    accepted_at TIMESTAMP(6) NULL,
    revoked_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    invited_by BINARY(16) NULL,
    UNIQUE KEY uk_vendor_invitation_token_hash (token_hash),
    INDEX ix_vendor_invitation_tenant_user (tenant_id, user_id, created_at),
    CONSTRAINT fk_vendor_invitation_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_vendor_invitation_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_vendor_invitation_actor FOREIGN KEY (invited_by) REFERENCES app_user(id)
);
