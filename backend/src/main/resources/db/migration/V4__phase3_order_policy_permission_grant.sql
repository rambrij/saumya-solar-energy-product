-- Backfill the order policy editor permission for existing vendor administrators.
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code = 'ORDER_POLICY_MANAGE'
WHERE r.name = 'VENDOR_ADMIN' AND r.tenant_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
