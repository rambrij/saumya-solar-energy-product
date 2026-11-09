-- Phase 1 authorization reconciliation: remove coarse legacy grants from every tenant role,
-- including custom roles created before fine-grained permission enforcement.
DELETE rp
FROM role_permission rp
JOIN role r ON r.id = rp.role_id
JOIN permission p ON p.id = rp.permission_id
WHERE r.tenant_id IS NOT NULL
  AND p.code IN (
    'CATALOG_MANAGE','CATALOG_VIEW','DISCOUNT_POLICY_MANAGE','CUSTOMER_MANAGE',
    'LEAD_MANAGE','SURVEY_MANAGE','QUOTATION_MANAGE','DISCOUNT_APPROVE',
    'USER_MANAGE','INVENTORY_MANAGE','SERVICE_MANAGE','FINANCE_MANAGE'
  );

-- Restricted platform support is read-only: tenant directory and audit events.
INSERT INTO role(id, tenant_id, name)
SELECT UUID_TO_BIN(UUID()), NULL, 'PLATFORM_SUPPORT'
WHERE NOT EXISTS (
    SELECT 1 FROM role WHERE tenant_id IS NULL AND name = 'PLATFORM_SUPPORT'
);

INSERT IGNORE INTO role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN ('AUDIT_VIEW', 'TENANT_VIEW')
WHERE r.tenant_id IS NULL AND r.name = 'PLATFORM_SUPPORT';
