ALTER TABLE quotation
    ADD COLUMN decision_by BINARY(16) NULL,
    ADD COLUMN decision_at TIMESTAMP NULL;

ALTER TABLE quotation
    ADD CONSTRAINT fk_quotation_decision_by
    FOREIGN KEY (decision_by) REFERENCES app_user(id);
