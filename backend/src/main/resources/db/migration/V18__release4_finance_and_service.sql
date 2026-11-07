-- Release 4 finance, payments, service management and SLA configuration.
ALTER TABLE tenant ADD COLUMN gstin VARCHAR(15) NULL, ADD COLUMN invoice_prefix VARCHAR(12) NOT NULL DEFAULT 'INV', ADD COLUMN service_reopen_days INT NULL;
ALTER TABLE customer_profile ADD COLUMN gstin VARCHAR(15) NULL;
ALTER TABLE inventory_transaction ADD COLUMN service_request_id BINARY(16) NULL;
CREATE INDEX ix_inventory_tx_service_request ON inventory_transaction(tenant_id,service_request_id);

CREATE TABLE invoice_sequence(
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, fiscal_year VARCHAR(9) NOT NULL,
 next_number BIGINT NOT NULL DEFAULT 0,
 UNIQUE KEY uq_invoice_sequence_tenant_year(tenant_id,fiscal_year),
 CONSTRAINT fk_invoice_sequence_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id)
);
CREATE TABLE invoice(
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, order_id BINARY(16) NOT NULL,
 invoice_number VARCHAR(80) NOT NULL, fiscal_year VARCHAR(9) NOT NULL, status VARCHAR(24) NOT NULL,
 customer_name VARCHAR(150) NOT NULL, customer_email VARCHAR(190), customer_address VARCHAR(700),
 customer_gstin VARCHAR(15), vendor_name VARCHAR(150) NOT NULL, vendor_gstin VARCHAR(15),
 currency CHAR(3) NOT NULL DEFAULT 'INR', subtotal DECIMAL(19,4) NOT NULL,
 tax_total DECIMAL(19,4) NOT NULL, grand_total DECIMAL(19,4) NOT NULL,
 tax_treatment VARCHAR(20) NULL, due_date DATE, issued_at TIMESTAMP(6) NOT NULL,
 created_by BINARY(16) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
 void_reason VARCHAR(500), UNIQUE KEY uq_invoice_tenant_number(tenant_id,invoice_number),
 UNIQUE KEY uq_invoice_order(tenant_id,order_id),
 CONSTRAINT fk_invoice_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 CONSTRAINT fk_invoice_order FOREIGN KEY(order_id) REFERENCES customer_order(id),
 INDEX ix_invoice_tenant_status_date(tenant_id,status,issued_at)
);
CREATE TABLE invoice_item(
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, invoice_id BINARY(16) NOT NULL,
 product_id BINARY(16), description VARCHAR(255) NOT NULL, hsn_code VARCHAR(40),
 quantity INT NOT NULL, unit_price DECIMAL(19,4) NOT NULL, tax_percent DECIMAL(7,4) NOT NULL,
 taxable_amount DECIMAL(19,4) NOT NULL, tax_amount DECIMAL(19,4) NOT NULL,
 line_total DECIMAL(19,4) NOT NULL,
 CONSTRAINT fk_invoice_item_invoice FOREIGN KEY(invoice_id) REFERENCES invoice(id),
 CONSTRAINT fk_invoice_item_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 INDEX ix_invoice_item_tenant_invoice(tenant_id,invoice_id)
);
CREATE TABLE payment(
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, invoice_id BINARY(16) NOT NULL,
 order_id BINARY(16) NOT NULL, amount DECIMAL(19,4) NOT NULL, currency CHAR(3) NOT NULL DEFAULT 'INR',
 method VARCHAR(30) NOT NULL, source VARCHAR(20) NOT NULL, status VARCHAR(24) NOT NULL,
 reference VARCHAR(150), provider VARCHAR(60), provider_reference VARCHAR(150),
 paid_at TIMESTAMP(6) NOT NULL, recorded_by BINARY(16) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_payment_invoice FOREIGN KEY(invoice_id) REFERENCES invoice(id),
 CONSTRAINT fk_payment_order FOREIGN KEY(order_id) REFERENCES customer_order(id),
 CONSTRAINT fk_payment_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 UNIQUE KEY uq_payment_provider_ref(provider,provider_reference),
 INDEX ix_payment_tenant_invoice(tenant_id,invoice_id,status)
);
CREATE TABLE payment_webhook_event(
 id BINARY(16) PRIMARY KEY, provider VARCHAR(60) NOT NULL, event_id VARCHAR(180) NOT NULL,
 payload_hash VARCHAR(64) NOT NULL, processing_status VARCHAR(24) NOT NULL,
 received_at TIMESTAMP(6) NOT NULL, processed_at TIMESTAMP(6),
 UNIQUE KEY uq_payment_webhook_provider_event(provider,event_id)
);
CREATE TABLE payment_refund(
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, payment_id BINARY(16) NOT NULL,
 amount DECIMAL(19,4) NOT NULL, reason VARCHAR(500) NOT NULL, status VARCHAR(24) NOT NULL,
 recorded_by BINARY(16) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_refund_payment FOREIGN KEY(payment_id) REFERENCES payment(id),
 CONSTRAINT fk_refund_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 INDEX ix_refund_tenant_payment(tenant_id,payment_id)
);
CREATE TABLE service_sla_policy(
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, priority VARCHAR(20) NOT NULL,
 response_minutes INT NOT NULL, resolution_minutes INT NOT NULL, time_zone VARCHAR(80) NOT NULL DEFAULT 'Asia/Kolkata',
 business_start TIME NULL, business_end TIME NULL, holidays_json JSON NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 UNIQUE KEY uq_service_sla_tenant_priority(tenant_id,priority),
 CONSTRAINT fk_service_sla_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 CHECK(response_minutes>0 AND resolution_minutes>=response_minutes)
);
CREATE TABLE service_request(
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, ticket_number VARCHAR(80) NOT NULL,
 customer_id BINARY(16) NOT NULL, installation_id BINARY(16), product_id BINARY(16), serial_number VARCHAR(150),
 category VARCHAR(50) NOT NULL, priority VARCHAR(20) NOT NULL, description VARCHAR(4000) NOT NULL,
 warranty_status VARCHAR(30) NOT NULL, status VARCHAR(30) NOT NULL, assigned_engineer_id BINARY(16),
 response_due_at TIMESTAMP(6), resolution_due_at TIMESTAMP(6), first_responded_at TIMESTAMP(6),
 resolved_at TIMESTAMP(6), customer_confirmed_at TIMESTAMP(6), parts_consumed_json JSON NULL, closed_at TIMESTAMP(6),
 paused_seconds BIGINT NOT NULL DEFAULT 0, sla_paused_at TIMESTAMP(6), response_breached_at TIMESTAMP(6), resolution_breached_at TIMESTAMP(6), resolution_notes VARCHAR(2000),
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 UNIQUE KEY uq_service_ticket_tenant_number(tenant_id,ticket_number),
 CONSTRAINT fk_service_request_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 CONSTRAINT fk_service_request_customer FOREIGN KEY(customer_id) REFERENCES customer_profile(id),
 CONSTRAINT fk_service_request_installation FOREIGN KEY(installation_id) REFERENCES installation_job(id),
 CONSTRAINT fk_service_request_product FOREIGN KEY(product_id) REFERENCES product(id),
 CONSTRAINT fk_service_request_engineer FOREIGN KEY(assigned_engineer_id) REFERENCES app_user(id),
 INDEX ix_service_tenant_status_created(tenant_id,status,created_at),
 INDEX ix_service_tenant_assignee_status(tenant_id,assigned_engineer_id,status)
);
CREATE TABLE service_visit(
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, service_request_id BINARY(16) NOT NULL,
 engineer_id BINARY(16) NOT NULL, scheduled_at TIMESTAMP(6) NOT NULL, started_at TIMESTAMP(6),
 completed_at TIMESTAMP(6), engineer_notes VARCHAR(4000), resolution VARCHAR(2000),
 evidence_url VARCHAR(1000), customer_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
 customer_confirmed_at TIMESTAMP(6), parts_consumed_json JSON NULL,
 CONSTRAINT fk_service_visit_request FOREIGN KEY(service_request_id) REFERENCES service_request(id),
 CONSTRAINT fk_service_visit_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 CONSTRAINT fk_service_visit_engineer FOREIGN KEY(engineer_id) REFERENCES app_user(id),
 INDEX ix_service_visit_tenant_schedule(tenant_id,scheduled_at)
);
CREATE TABLE service_event(
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, service_request_id BINARY(16) NOT NULL,
 actor_id BINARY(16) NULL, event_type VARCHAR(40) NOT NULL, old_status VARCHAR(30), new_status VARCHAR(30),
 reason VARCHAR(2000), occurred_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_service_event_request FOREIGN KEY(service_request_id) REFERENCES service_request(id),
 CONSTRAINT fk_service_event_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 INDEX ix_service_event_tenant_ticket_time(tenant_id,service_request_id,occurred_at)
);
