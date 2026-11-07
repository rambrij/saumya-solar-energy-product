-- Restore the platform tenant-management grant for databases where the
-- original global role was created before its permission mapping was present.
INSERT INTO permission (id, code)
SELECT UUID_TO_BIN(UUID()), 'PLATFORM_MANAGE'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'PLATFORM_MANAGE');

INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code = 'PLATFORM_MANAGE'
WHERE r.tenant_id IS NULL
  AND r.name = 'PLATFORM_SUPER_ADMIN';

-- Existing packages remain valid and render the frontend fallback image.
ALTER TABLE solar_package ADD COLUMN image_url VARCHAR(1024) NULL;
