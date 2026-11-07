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
-- Make the column addition safe if a DBA already added it during recovery.
SET @package_image_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'solar_package'
      AND column_name = 'image_url'
);
SET @package_image_ddl = IF(
    @package_image_column_exists = 0,
    'ALTER TABLE solar_package ADD COLUMN image_url VARCHAR(1024) NULL',
    'SELECT 1'
);
PREPARE package_image_stmt FROM @package_image_ddl;
EXECUTE package_image_stmt;
DEALLOCATE PREPARE package_image_stmt;
