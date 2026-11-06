INSERT INTO permission(id,code)
SELECT UUID_TO_BIN(UUID()),'CATALOG_VIEW'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='CATALOG_VIEW');

INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code='CATALOG_VIEW'
WHERE r.tenant_id IS NOT NULL AND r.name IN ('SALES_EXECUTIVE','SALES_MANAGER','MANAGER');
