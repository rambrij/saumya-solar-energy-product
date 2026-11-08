CREATE TABLE site_survey_attachment (
    id BINARY(16) PRIMARY KEY,
    tenant_id BINARY(16) NOT NULL,
    survey_id BINARY(16) NOT NULL,
    original_name VARCHAR(200) NOT NULL,
    storage_key VARCHAR(100) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    uploaded_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_survey_attachment_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_survey_attachment_survey FOREIGN KEY (survey_id) REFERENCES site_survey(id),
    CONSTRAINT fk_survey_attachment_uploader FOREIGN KEY (uploaded_by) REFERENCES app_user(id),
    CONSTRAINT uq_survey_attachment_storage_key UNIQUE (storage_key),
    INDEX ix_survey_attachment_scope (tenant_id, survey_id, created_at),
    CONSTRAINT chk_survey_attachment_size CHECK (size_bytes > 0 AND size_bytes <= 10485760)
);
