CREATE TABLE offline_sync_operation (
    id BINARY(16) PRIMARY KEY,
    tenant_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    client_operation_id BINARY(16) NOT NULL,
    operation_type VARCHAR(40) NOT NULL,
    resource_id BINARY(16) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    result_json LONGTEXT,
    error_message VARCHAR(1000),
    client_created_at TIMESTAMP(6) NOT NULL,
    received_at TIMESTAMP(6) NOT NULL,
    applied_at TIMESTAMP(6),
    CONSTRAINT fk_offline_sync_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_offline_sync_actor FOREIGN KEY (actor_id) REFERENCES app_user(id),
    CONSTRAINT uq_offline_sync_client_operation UNIQUE (tenant_id, actor_id, client_operation_id),
    CONSTRAINT chk_offline_sync_status CHECK (status IN ('APPLIED')),
    CONSTRAINT chk_offline_sync_operation_type CHECK (operation_type IN (
        'SERVICE_COMMENT_CREATE', 'SURVEY_UPDATE', 'SURVEY_START', 'SURVEY_SUBMIT'
    )),
    INDEX ix_offline_sync_actor_received (tenant_id, actor_id, received_at),
    INDEX ix_offline_sync_resource (tenant_id, operation_type, resource_id)
);
