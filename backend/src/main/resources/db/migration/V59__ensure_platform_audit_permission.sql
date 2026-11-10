-- Ensure the SRS platform audit and branch administration permissions exist on upgraded databases.
-- V53 grants AUDIT_VIEW to platform roles but its permission insert list omitted the code.
-- V51 normally creates BRANCH_MANAGE; this idempotent repair also covers upgraded databases
-- where that earlier optional grant was not present.

INSERT INTO permission(id, code)
SELECT UUID_TO_BIN(UUID()), src.code
FROM (SELECT 'AUDIT_VIEW' code UNION ALL SELECT 'BRANCH_MANAGE') src
LEFT JOIN permission existing ON existing.code=src.code
WHERE existing.id IS NULL;

-- Restore the intended platform audit grants and vendor branch administration grant.
INSERT IGNORE INTO role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.code='AUDIT_VIEW'
WHERE r.tenant_id IS NULL AND r.name IN ('PLATFORM_SUPER_ADMIN', 'PLATFORM_SUPPORT');

INSERT IGNORE INTO role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.code='BRANCH_MANAGE'
WHERE r.tenant_id IS NOT NULL AND r.name='VENDOR_ADMIN';
