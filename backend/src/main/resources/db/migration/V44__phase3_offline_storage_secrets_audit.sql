-- Phase 3 hardening foundations. Additive only; existing offline operations and audit rows are retained.
ALTER TABLE offline_sync_operation
    DROP CHECK chk_offline_sync_status,
    ADD CONSTRAINT chk_offline_sync_status
      CHECK (status IN ('APPLIED','DUPLICATE','CONFLICT','REJECTED','PENDING')),
    ADD COLUMN expected_version BIGINT NULL,
    ADD COLUMN resulting_version BIGINT NULL,
    ADD COLUMN conflict_code VARCHAR(64) NULL,
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN resolved_at TIMESTAMP(6) NULL,
    ADD COLUMN resolution_action VARCHAR(32) NULL;

ALTER TABLE audit_event
    ADD COLUMN branch_id BINARY(16) NULL,
    ADD COLUMN request_id VARCHAR(100) NULL,
    ADD COLUMN source_ip VARCHAR(64) NULL,
    ADD COLUMN user_agent VARCHAR(512) NULL,
    ADD INDEX ix_audit_tenant_branch_created (tenant_id, branch_id, created_at),
    ADD INDEX ix_audit_entity (tenant_id, entity_type, entity_id, created_at);

CREATE TABLE tenant_storage_ledger (
    id BINARY(16) PRIMARY KEY,
    tenant_id BINARY(16) NOT NULL,
    operation_id VARCHAR(120) NOT NULL,
    object_key VARCHAR(700) NOT NULL,
    object_category VARCHAR(40) NOT NULL,
    byte_delta BIGINT NOT NULL,
    checksum_sha256 CHAR(64) NULL,
    recorded_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_storage_ledger_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT uq_storage_ledger_operation UNIQUE (tenant_id, operation_id),
    INDEX ix_storage_ledger_tenant_time (tenant_id, recorded_at),
    INDEX ix_storage_ledger_object (tenant_id, object_key)
);

CREATE TABLE offline_sync_attachment (
    id BINARY(16) PRIMARY KEY,
    tenant_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    client_attachment_id BINARY(16) NOT NULL,
    operation_id BINARY(16) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    expected_size BIGINT NOT NULL,
    received_size BIGINT NOT NULL DEFAULT 0,
    checksum_sha256 CHAR(64) NOT NULL,
    object_key VARCHAR(700) NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_offline_attachment_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_offline_attachment_actor FOREIGN KEY (actor_id) REFERENCES app_user(id),
    CONSTRAINT uq_offline_attachment_client UNIQUE (tenant_id, actor_id, client_attachment_id),
    CONSTRAINT chk_offline_attachment_size CHECK (expected_size > 0 AND received_size >= 0 AND received_size <= expected_size),
    CONSTRAINT chk_offline_attachment_status CHECK (status IN ('PENDING','UPLOADING','COMPLETE','REJECTED','EXPIRED')),
    INDEX ix_offline_attachment_operation (tenant_id, operation_id, status)
);

CREATE TABLE integration_secret_reference (
    id BINARY(16) PRIMARY KEY,
    tenant_id BINARY(16) NOT NULL,
    integration_connection_id BINARY(16) NOT NULL,
    secret_provider VARCHAR(40) NOT NULL,
    secret_reference VARCHAR(700) NOT NULL,
    secret_version VARCHAR(120) NULL,
    status VARCHAR(20) NOT NULL,
    rotated_at TIMESTAMP(6) NULL,
    expires_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_secret_reference_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_secret_reference_connection FOREIGN KEY (integration_connection_id) REFERENCES integration_connection(id),
    CONSTRAINT uq_secret_reference_connection UNIQUE (tenant_id, integration_connection_id),
    CONSTRAINT chk_secret_reference_status CHECK (status IN ('ACTIVE','ROTATION_DUE','REVOKED','EXPIRED')),
    INDEX ix_secret_reference_expiry (tenant_id, status, expires_at)
);
