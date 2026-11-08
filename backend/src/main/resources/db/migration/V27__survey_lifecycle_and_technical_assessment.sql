ALTER TABLE site_survey
    ADD COLUMN latitude DECIMAL(10,7) NULL,
    ADD COLUMN longitude DECIMAL(10,7) NULL,
    ADD COLUMN roof_orientation VARCHAR(40) NULL,
    ADD COLUMN monthly_electricity_bill DECIMAL(19,2) NULL,
    ADD COLUMN electrical_phase VARCHAR(30) NULL,
    ADD COLUMN meter_type VARCHAR(40) NULL,
    ADD COLUMN findings TEXT NULL,
    ADD COLUMN recommendation TEXT NULL,
    ADD COLUMN submitted_at TIMESTAMP(6) NULL,
    ADD COLUMN reviewed_by BINARY(16) NULL,
    ADD COLUMN reviewed_at TIMESTAMP(6) NULL,
    ADD COLUMN rejection_reason VARCHAR(1000) NULL,
    ADD INDEX ix_survey_tenant_status_schedule (tenant_id, status, scheduled_at),
    ADD CONSTRAINT fk_site_survey_reviewer FOREIGN KEY (reviewed_by) REFERENCES app_user(id);

CREATE TABLE site_survey_history (
    id BINARY(16) PRIMARY KEY,
    tenant_id BINARY(16) NOT NULL,
    survey_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    old_status VARCHAR(30) NULL,
    new_status VARCHAR(30) NULL,
    reason VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_survey_history_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_survey_history_survey FOREIGN KEY (survey_id) REFERENCES site_survey(id),
    CONSTRAINT fk_survey_history_actor FOREIGN KEY (actor_id) REFERENCES app_user(id),
    INDEX ix_survey_history_tenant_survey_time (tenant_id, survey_id, created_at)
);
