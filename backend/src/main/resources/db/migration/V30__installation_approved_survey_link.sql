ALTER TABLE installation_job
    ADD COLUMN approved_survey_id BINARY(16) NULL,
    ADD CONSTRAINT fk_installation_approved_survey FOREIGN KEY (approved_survey_id) REFERENCES site_survey(id),
    ADD INDEX ix_installation_tenant_survey (tenant_id,approved_survey_id);