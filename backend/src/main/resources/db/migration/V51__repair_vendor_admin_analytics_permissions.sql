-- Repair vendor administrator analytics access for existing tenants.
-- The API continues to enforce both permissions; this migration ensures every
-- tenant-scoped VENDOR_ADMIN role has the required grants.
INSERT INTO permission (id, code)
SELECT UUID_TO_BIN(UUID()), src.code
FROM (
    SELECT 'REPORT_VIEW' AS code
    UNION ALL
    SELECT 'BRANCH_MANAGE' AS code
) src
LEFT JOIN permission existing ON existing.code = src.code
WHERE existing.id IS NULL;

INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN ('REPORT_VIEW', 'BRANCH_MANAGE')
WHERE r.tenant_id IS NOT NULL
  AND r.name = 'VENDOR_ADMIN';
