CREATE TABLE audit_log (
    id BINARY(16) NOT NULL PRIMARY KEY,
    tenant_id BINARY(16) NULL,
    actor_id BINARY(16) NULL,
    account_type VARCHAR(20) NULL,
    http_method VARCHAR(10) NOT NULL,
    request_path VARCHAR(500) NOT NULL,
    response_status INT NOT NULL,
    source_ip VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    INDEX ix_audit_tenant_created (tenant_id, created_at),
    INDEX ix_audit_actor_created (actor_id, created_at),
    CONSTRAINT fk_audit_log_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_audit_log_actor FOREIGN KEY (actor_id) REFERENCES app_user(id)
);
