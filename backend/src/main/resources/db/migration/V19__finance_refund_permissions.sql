INSERT INTO permission(id,code)
SELECT UUID_TO_BIN(UUID()),'PAYMENT_REFUND'
WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='PAYMENT_REFUND');

INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code='PAYMENT_REFUND'
WHERE r.tenant_id IS NOT NULL AND r.name IN ('VENDOR_ADMIN','ACCOUNTANT');
