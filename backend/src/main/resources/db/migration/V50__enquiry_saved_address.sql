-- Optional customer-selected saved address snapshot for enquiries.
ALTER TABLE enquiry
 ADD COLUMN address_id BINARY(16) NULL,
 ADD COLUMN site_address VARCHAR(500) NULL,
 ADD COLUMN city_pincode VARCHAR(160) NULL,
 ADD CONSTRAINT fk_enquiry_customer_address
   FOREIGN KEY (tenant_id,address_id) REFERENCES customer_address(tenant_id,id),
 ADD INDEX ix_enquiry_tenant_address (tenant_id,address_id);
