CREATE TABLE approval_workflow (
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, code VARCHAR(60) NOT NULL, name VARCHAR(150) NOT NULL,
 entity_type VARCHAR(60) NOT NULL, steps_json LONGTEXT NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_approval_workflow_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 CONSTRAINT uq_approval_workflow_code UNIQUE(tenant_id,code),
 CONSTRAINT uq_approval_workflow_tenant_id UNIQUE(tenant_id,id),
 INDEX ix_approval_workflow_tenant_entity (tenant_id,entity_type,active)
);
CREATE TABLE approval_instance (
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, workflow_id BINARY(16) NOT NULL,
 entity_type VARCHAR(60) NOT NULL, entity_id VARCHAR(100) NOT NULL, steps_snapshot_json LONGTEXT NOT NULL,
 status VARCHAR(20) NOT NULL, current_step INT NOT NULL, requested_by BINARY(16) NOT NULL,
 requested_at TIMESTAMP(6) NOT NULL, completed_at TIMESTAMP(6) NULL,
 CONSTRAINT fk_approval_instance_workflow FOREIGN KEY(tenant_id,workflow_id) REFERENCES approval_workflow(tenant_id,id),
 CONSTRAINT fk_approval_instance_actor FOREIGN KEY(requested_by) REFERENCES app_user(id),
 CONSTRAINT chk_approval_instance_status CHECK(status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
 INDEX ix_approval_instance_tenant_entity (tenant_id,entity_type,entity_id,status),
 INDEX ix_approval_instance_tenant_status (tenant_id,status,requested_at)
);
CREATE TABLE approval_decision (
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, instance_id BINARY(16) NOT NULL,
 step_order INT NOT NULL, decision VARCHAR(20) NOT NULL, actor_id BINARY(16) NOT NULL,
 comments VARCHAR(1000), created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_approval_decision_instance FOREIGN KEY(tenant_id,instance_id) REFERENCES approval_instance(tenant_id,id),
 CONSTRAINT fk_approval_decision_actor FOREIGN KEY(actor_id) REFERENCES app_user(id),
 CONSTRAINT chk_approval_decision_value CHECK(decision IN ('APPROVE','REJECT')),
 INDEX ix_approval_decision_tenant_instance (tenant_id,instance_id,step_order)
);
INSERT INTO permission(id,code)
SELECT UUID_TO_BIN(UUID()),'APPROVAL_WORKFLOW_MANAGE'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='APPROVAL_WORKFLOW_MANAGE');
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p
WHERE r.tenant_id IS NOT NULL AND r.name='VENDOR_ADMIN' AND p.code='APPROVAL_WORKFLOW_MANAGE';
