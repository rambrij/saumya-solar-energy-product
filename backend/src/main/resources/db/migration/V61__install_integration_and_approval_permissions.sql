-- These are tenant-level configuration permissions. Do not create roles or users here.
-- Grant only to already-existing tenant VENDOR_ADMIN roles; custom roles may be granted these
-- codes through the existing ROLE_CREATE/ROLE_UPDATE allowlist after this migration.
INSERT INTO permission (id, code)
VALUES
    (UUID_TO_BIN(UUID()), 'INTEGRATION_MANAGE'),
    (UUID_TO_BIN(UUID()), 'APPROVAL_WORKFLOW_MANAGE')
ON DUPLICATE KEY UPDATE code = VALUES(code);

INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN ('INTEGRATION_MANAGE', 'APPROVAL_WORKFLOW_MANAGE')
WHERE r.tenant_id IS NOT NULL
  AND r.name = 'VENDOR_ADMIN';
