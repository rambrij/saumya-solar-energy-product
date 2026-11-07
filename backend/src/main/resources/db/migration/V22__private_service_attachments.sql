CREATE TABLE service_attachment(
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, service_request_id BINARY(16) NOT NULL,
 original_name VARCHAR(255) NOT NULL, storage_key VARCHAR(120) NOT NULL, content_type VARCHAR(100) NOT NULL,
 size_bytes BIGINT NOT NULL, uploaded_by BINARY(16) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_service_attachment_ticket FOREIGN KEY(service_request_id) REFERENCES service_request(id),
 CONSTRAINT fk_service_attachment_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 CONSTRAINT fk_service_attachment_user FOREIGN KEY(uploaded_by) REFERENCES app_user(id),
 INDEX ix_service_attachment_tenant_ticket(tenant_id,service_request_id,created_at)
);
