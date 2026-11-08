CREATE TABLE integration_connection (
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, provider_code VARCHAR(60) NOT NULL,
 integration_type VARCHAR(30) NOT NULL, display_name VARCHAR(150) NOT NULL, status VARCHAR(20) NOT NULL,
 secret_reference VARCHAR(500), config_json LONGTEXT NOT NULL, created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_integration_connection_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 CONSTRAINT uq_integration_connection_tenant_id UNIQUE(tenant_id,id),
 CONSTRAINT chk_integration_connection_type CHECK(integration_type IN ('ACCOUNTING','PAYMENT_GATEWAY','SMS','WHATSAPP','GENERIC')),
 CONSTRAINT chk_integration_connection_status CHECK(status IN ('ACTIVE','DISABLED','ERROR')),
 INDEX ix_integration_connection_tenant_type(tenant_id,integration_type,status)
);
CREATE TABLE integration_sync_job (
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, connection_id BINARY(16) NOT NULL,
 entity_type VARCHAR(60) NOT NULL, entity_id VARCHAR(100) NOT NULL, operation VARCHAR(30) NOT NULL,
 idempotency_key VARCHAR(160) NOT NULL, payload_json LONGTEXT NOT NULL, status VARCHAR(30) NOT NULL,
 attempt_count INT NOT NULL DEFAULT 0, next_attempt_at TIMESTAMP(6) NOT NULL, last_error VARCHAR(1000),
 external_reference VARCHAR(160), created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_integration_job_connection FOREIGN KEY(tenant_id,connection_id) REFERENCES integration_connection(tenant_id,id),
 CONSTRAINT uq_integration_job_idempotency UNIQUE(tenant_id,connection_id,idempotency_key),
 CONSTRAINT chk_integration_job_status CHECK(status IN ('QUEUED','PROCESSING','SUCCEEDED','RETRY','WAITING_FOR_ADAPTER','DEAD_LETTER')),
 INDEX ix_integration_job_dispatch(status,next_attempt_at,created_at),
 INDEX ix_integration_job_tenant_entity(tenant_id,entity_type,entity_id)
);
CREATE TABLE integration_webhook_event (
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16), provider_code VARCHAR(60) NOT NULL,
 provider_event_id VARCHAR(160) NOT NULL, payload_hash VARCHAR(128) NOT NULL, status VARCHAR(20) NOT NULL,
 received_at TIMESTAMP(6) NOT NULL, processed_at TIMESTAMP(6),
 CONSTRAINT fk_integration_webhook_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 CONSTRAINT uq_integration_webhook_provider_event UNIQUE(provider_code,provider_event_id),
 INDEX ix_integration_webhook_tenant_received(tenant_id,received_at)
);
INSERT INTO permission(id,code) SELECT UUID_TO_BIN(UUID()),'INTEGRATION_MANAGE'
WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='INTEGRATION_MANAGE');
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p WHERE r.tenant_id IS NOT NULL AND r.name='VENDOR_ADMIN' AND p.code='INTEGRATION_MANAGE';
