ALTER TABLE invoice ADD COLUMN cgst_amount DECIMAL(19,4) NOT NULL DEFAULT 0, ADD COLUMN sgst_amount DECIMAL(19,4) NOT NULL DEFAULT 0, ADD COLUMN igst_amount DECIMAL(19,4) NOT NULL DEFAULT 0;
CREATE TABLE credit_note(
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, invoice_id BINARY(16) NOT NULL,
 credit_note_number VARCHAR(100) NOT NULL, reason VARCHAR(500) NOT NULL, amount DECIMAL(19,4) NOT NULL,
 status VARCHAR(24) NOT NULL, created_by BINARY(16) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
 UNIQUE KEY uq_credit_note_tenant_number(tenant_id,credit_note_number),
 CONSTRAINT fk_credit_note_invoice FOREIGN KEY(invoice_id) REFERENCES invoice(id),
 CONSTRAINT fk_credit_note_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 INDEX ix_credit_note_tenant_invoice(tenant_id,invoice_id)
);
