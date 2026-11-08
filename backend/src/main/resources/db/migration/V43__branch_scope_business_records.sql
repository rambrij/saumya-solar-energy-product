-- Add nullable branch ownership to operational and commercial records.
-- Existing Phase 1/2 records remain unassigned (NULL) and are not rewritten.
ALTER TABLE enquiry ADD COLUMN branch_id BINARY(16) NULL;
ALTER TABLE sales_lead ADD COLUMN branch_id BINARY(16) NULL;
ALTER TABLE site_survey ADD COLUMN branch_id BINARY(16) NULL;
ALTER TABLE quotation ADD COLUMN branch_id BINARY(16) NULL;
ALTER TABLE customer_order ADD COLUMN branch_id BINARY(16) NULL;
ALTER TABLE invoice ADD COLUMN branch_id BINARY(16) NULL;
ALTER TABLE service_request ADD COLUMN branch_id BINARY(16) NULL;
ALTER TABLE installation_job ADD COLUMN branch_id BINARY(16) NULL;

ALTER TABLE enquiry ADD CONSTRAINT fk_enquiry_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id);
ALTER TABLE sales_lead ADD CONSTRAINT fk_sales_lead_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id);
ALTER TABLE site_survey ADD CONSTRAINT fk_site_survey_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id);
ALTER TABLE quotation ADD CONSTRAINT fk_quotation_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id);
ALTER TABLE customer_order ADD CONSTRAINT fk_customer_order_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id);
ALTER TABLE invoice ADD CONSTRAINT fk_invoice_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id);
ALTER TABLE service_request ADD CONSTRAINT fk_service_request_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id);
ALTER TABLE installation_job ADD CONSTRAINT fk_installation_job_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id);

CREATE INDEX ix_enquiry_tenant_branch ON enquiry(tenant_id,branch_id);
CREATE INDEX ix_sales_lead_tenant_branch ON sales_lead(tenant_id,branch_id);
CREATE INDEX ix_site_survey_tenant_branch ON site_survey(tenant_id,branch_id);
CREATE INDEX ix_quotation_tenant_branch ON quotation(tenant_id,branch_id);
CREATE INDEX ix_customer_order_tenant_branch ON customer_order(tenant_id,branch_id);
CREATE INDEX ix_invoice_tenant_branch ON invoice(tenant_id,branch_id);
CREATE INDEX ix_service_request_tenant_branch ON service_request(tenant_id,branch_id);
CREATE INDEX ix_installation_job_tenant_branch ON installation_job(tenant_id,branch_id);
