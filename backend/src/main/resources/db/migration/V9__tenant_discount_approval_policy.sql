ALTER TABLE tenant
    ADD COLUMN discount_approval_threshold DECIMAL(5,2) NOT NULL DEFAULT 10.00;

INSERT INTO permission(id,code)
SELECT UUID_TO_BIN(UUID()),'DISCOUNT_POLICY_MANAGE'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='DISCOUNT_POLICY_MANAGE');

INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id
FROM role r JOIN permission p ON p.code='DISCOUNT_POLICY_MANAGE'
WHERE r.tenant_id IS NOT NULL AND r.name='VENDOR_ADMIN';
