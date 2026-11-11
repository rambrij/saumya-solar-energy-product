-- Adds the explicit permission consumed by EnterpriseSaasService branding
-- and custom-domain operations. Keep this additive for already-migrated databases.

INSERT INTO permission (id, code)
SELECT UUID_TO_BIN(UUID()), 'BRANDING_MANAGE'
WHERE NOT EXISTS (
    SELECT 1 FROM permission WHERE code = 'BRANDING_MANAGE'
);

-- Branding and custom-domain administration is a tenant-admin capability.
-- Do not grant it to platform roles or employee/custom roles implicitly.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code = 'BRANDING_MANAGE'
WHERE r.tenant_id IS NOT NULL
  AND r.name = 'VENDOR_ADMIN';
