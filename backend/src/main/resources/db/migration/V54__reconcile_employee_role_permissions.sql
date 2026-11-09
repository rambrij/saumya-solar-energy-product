-- Reconcile existing employee roles to the SRS fine-grained least-privilege matrix.
-- Vendor Admin grants are intentionally retained; platform grants are not changed.
DELETE rp
FROM role_permission rp
JOIN role r ON r.id = rp.role_id
JOIN permission p ON p.id = rp.permission_id
WHERE r.tenant_id IS NOT NULL
  AND r.name IN (
      'SALES_EXECUTIVE', 'SALES_MANAGER', 'MANAGER', 'INVENTORY_MANAGER',
      'SERVICE_ENGINEER', 'ACCOUNTANT', 'INSTALLER', 'TECHNICIAN',
      'SERVICE_MANAGER', 'OPERATIONS_MANAGER'
  )
  AND p.code IN (
      'CATALOG_VIEW', 'CATALOG_MANAGE', 'CUSTOMER_MANAGE', 'LEAD_MANAGE',
      'SURVEY_MANAGE', 'QUOTATION_MANAGE', 'DISCOUNT_APPROVE',
      'INVENTORY_MANAGE', 'SERVICE_MANAGE', 'FINANCE_MANAGE'
  );
