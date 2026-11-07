-- Add USER_MANAGE for already-seeded vendor admin roles.
-- On a fresh database, DataSeeder creates the permission and includes it in VENDOR_ADMIN.
INSERT INTO permission (id, code)
SELECT UUID_TO_BIN(UUID()), 'USER_MANAGE'
WHERE EXISTS (
    SELECT 1 FROM role WHERE name = 'VENDOR_ADMIN' AND tenant_id IS NOT NULL
)
AND NOT EXISTS (
    SELECT 1 FROM permission WHERE code = 'USER_MANAGE'
);

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code = 'USER_MANAGE'
WHERE r.name = 'VENDOR_ADMIN'
  AND r.tenant_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
