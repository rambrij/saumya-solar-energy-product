-- Platform-only permissions must never be granted to tenant-owned roles.
DELETE rp
FROM role_permission rp
JOIN role r ON r.id = rp.role_id
JOIN permission p ON p.id = rp.permission_id
WHERE r.tenant_id IS NOT NULL
  AND p.code IN ('PLATFORM_MANAGE','SUBSCRIPTION_MANAGE','TENANT_VIEW','TENANT_UPDATE');
