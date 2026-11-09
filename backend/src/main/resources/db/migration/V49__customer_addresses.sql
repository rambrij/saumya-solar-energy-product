-- Customer-owned saved addresses. Records are isolated by both tenant and customer profile.
ALTER TABLE customer_profile ADD UNIQUE KEY uq_customer_profile_tenant_id (tenant_id,id);
CREATE TABLE customer_address (
 id BINARY(16) PRIMARY KEY,
 tenant_id BINARY(16) NOT NULL,
 customer_id BINARY(16) NOT NULL,
 label VARCHAR(80) NOT NULL,
 line1 VARCHAR(180) NOT NULL,
 line2 VARCHAR(180),
 city VARCHAR(100) NOT NULL,
 state VARCHAR(100) NOT NULL,
 pincode VARCHAR(12) NOT NULL,
 country VARCHAR(80) NOT NULL DEFAULT 'India',
 landmark VARCHAR(120),
 is_default BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMP(6) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_customer_address_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
 CONSTRAINT fk_customer_address_profile FOREIGN KEY (tenant_id,customer_id) REFERENCES customer_profile(tenant_id,id),
 INDEX ix_customer_address_owner_default (tenant_id,customer_id,is_default,created_at)
);
