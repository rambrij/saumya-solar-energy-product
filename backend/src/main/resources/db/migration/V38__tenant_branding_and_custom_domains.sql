ALTER TABLE tenant
 ADD COLUMN display_name VARCHAR(150) NULL,
 ADD COLUMN logo_url VARCHAR(1000) NULL,
 ADD COLUMN primary_color VARCHAR(7) NOT NULL DEFAULT '#0B6B4B',
 ADD COLUMN secondary_color VARCHAR(7) NOT NULL DEFAULT '#F6C85F',
 ADD COLUMN contact_email VARCHAR(254) NULL,
 ADD COLUMN contact_phone VARCHAR(40) NULL,
 ADD COLUMN support_email VARCHAR(254) NULL,
 ADD COLUMN support_phone VARCHAR(40) NULL;

CREATE TABLE tenant_domain (
 id BINARY(16) PRIMARY KEY,
 tenant_id BINARY(16) NOT NULL,
 domain VARCHAR(253) NOT NULL,
 verification_token VARCHAR(100) NOT NULL,
 status VARCHAR(20) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 verified_at TIMESTAMP(6) NULL,
 CONSTRAINT fk_tenant_domain_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
 CONSTRAINT uq_tenant_domain UNIQUE (domain),
 CONSTRAINT chk_tenant_domain_status CHECK (status IN ('PENDING','VERIFIED','DISABLED')),
 INDEX ix_tenant_domain_tenant_status (tenant_id,status)
);

INSERT INTO permission(id,code)
SELECT UUID_TO_BIN(UUID()),'BRANDING_MANAGE'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='BRANDING_MANAGE');
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p
WHERE r.tenant_id IS NOT NULL AND r.name='VENDOR_ADMIN' AND p.code='BRANDING_MANAGE';
