CREATE TABLE service_comment (
    id BINARY(16) PRIMARY KEY,
    tenant_id BINARY(16) NOT NULL,
    service_request_id BINARY(16) NOT NULL,
    author_id BINARY(16) NOT NULL,
    visibility VARCHAR(20) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_service_comment_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_service_comment_ticket FOREIGN KEY (service_request_id) REFERENCES service_request(id),
    CONSTRAINT fk_service_comment_author FOREIGN KEY (author_id) REFERENCES app_user(id),
    CONSTRAINT chk_service_comment_visibility CHECK (visibility IN ('CUSTOMER','INTERNAL')),
    INDEX ix_service_comment_ticket (tenant_id,service_request_id,created_at)
);