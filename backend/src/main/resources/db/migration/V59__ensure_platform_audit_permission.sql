-- Ensure the SRS platform audit permission exists on upgraded databases.
-- V53 grants AUDIT_VIEW to platform roles but its permission insert list omitted the code.
INSERT INTO permission(id, code)
SELECT UUID_TO_BIN(UUID()), 'AUDIT_VIEW'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='AUDIT_VIEW');

-- Restore the intended platform-only grants. Tenant role grants are not modified.
INSERT IGNORE INTO role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code='AUDIT_VIEW'
WHERE r.tenant_id IS NULL
  AND r.name IN ('PLATFORM_SUPER_ADMIN', 'PLATFORM_SUPPORT');
