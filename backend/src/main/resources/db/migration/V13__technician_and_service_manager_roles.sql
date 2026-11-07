INSERT INTO role(id,tenant_id,name)
SELECT UUID_TO_BIN(UUID()),t.id,'TECHNICIAN' FROM tenant t
WHERE NOT EXISTS(SELECT 1 FROM role r WHERE r.tenant_id=t.id AND r.name='TECHNICIAN');
INSERT INTO role(id,tenant_id,name)
SELECT UUID_TO_BIN(UUID()),t.id,'SERVICE_MANAGER' FROM tenant t
WHERE NOT EXISTS(SELECT 1 FROM role r WHERE r.tenant_id=t.id AND r.name='SERVICE_MANAGER');
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code IN ('SURVEY_MANAGE','SERVICE_MANAGE')
WHERE r.tenant_id IS NOT NULL AND r.name IN ('TECHNICIAN','SERVICE_MANAGER');
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code='REPORT_VIEW'
WHERE r.tenant_id IS NOT NULL AND r.name='SERVICE_MANAGER';
