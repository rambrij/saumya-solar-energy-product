-- Least-privilege access to each authenticated vendor user's own inbox.
INSERT INTO permission(id,code)
SELECT UUID_TO_BIN(UUID()),'NOTIFICATION_VIEW'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='NOTIFICATION_VIEW');

INSERT INTO role_permission(role_id,permission_id)
SELECT r.id,p.id
FROM role r
JOIN permission p ON p.code='NOTIFICATION_VIEW'
WHERE r.tenant_id IS NOT NULL
  AND r.name IN (
    'VENDOR_ADMIN','SALES_EXECUTIVE','SALES_MANAGER','MANAGER','INVENTORY_MANAGER',
    'SERVICE_ENGINEER','ACCOUNTANT','INSTALLER','TECHNICIAN','SERVICE_MANAGER',
    'OPERATIONS_MANAGER','CONTENT_MANAGER'
  )
  AND NOT EXISTS (
    SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id
  );
