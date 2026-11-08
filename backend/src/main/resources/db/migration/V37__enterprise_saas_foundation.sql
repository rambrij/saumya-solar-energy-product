-- Enterprise SaaS foundation: subscriptions, branch hierarchy and central audit.
CREATE TABLE branch (
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, code VARCHAR(50) NOT NULL,
 name VARCHAR(150) NOT NULL, address VARCHAR(700), phone VARCHAR(40), email VARCHAR(254),
 active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_branch_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
 CONSTRAINT uq_branch_tenant_code UNIQUE (tenant_id,code),
 CONSTRAINT uq_branch_tenant_id UNIQUE (tenant_id,id),
 INDEX ix_branch_tenant_active (tenant_id,active)
);
CREATE TABLE branch_user (
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, branch_id BINARY(16) NOT NULL,
 user_id BINARY(16) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_branch_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
 CONSTRAINT fk_branch_user_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id),
 CONSTRAINT fk_branch_user_user FOREIGN KEY (user_id) REFERENCES app_user(id),
 CONSTRAINT uq_branch_user UNIQUE (tenant_id,branch_id,user_id),
 INDEX ix_branch_user_tenant_user (tenant_id,user_id)
);
ALTER TABLE warehouse ADD COLUMN branch_id BINARY(16) NULL;
ALTER TABLE warehouse ADD CONSTRAINT fk_warehouse_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id);
CREATE INDEX ix_warehouse_tenant_branch_active ON warehouse(tenant_id,branch_id,active);
CREATE TABLE subscription_plan (
 id BINARY(16) PRIMARY KEY, plan_code VARCHAR(50) NOT NULL, name VARCHAR(120) NOT NULL,
 description VARCHAR(1000), billing_cycle VARCHAR(20) NOT NULL, price DECIMAL(19,2) NOT NULL,
 currency CHAR(3) NOT NULL DEFAULT 'INR', max_users INT NOT NULL, max_branches INT NOT NULL,
 max_warehouses INT NOT NULL, max_storage_mb BIGINT NOT NULL, entitlements_json LONGTEXT NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT uq_subscription_plan_code UNIQUE(plan_code),
 CONSTRAINT chk_subscription_plan_price CHECK(price>=0),
 CONSTRAINT chk_subscription_plan_limits CHECK(max_users>=1 AND max_branches>=1 AND max_warehouses>=1 AND max_storage_mb>=0),
 CONSTRAINT chk_subscription_plan_cycle CHECK(billing_cycle IN ('MONTHLY','YEARLY'))
);
CREATE TABLE tenant_subscription (
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, plan_id BINARY(16) NOT NULL,
 status VARCHAR(20) NOT NULL, starts_at TIMESTAMP(6) NOT NULL, ends_at TIMESTAMP(6), renews_at TIMESTAMP(6),
 external_reference VARCHAR(160), created_by BINARY(16), created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_subscription_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 CONSTRAINT fk_subscription_plan FOREIGN KEY(plan_id) REFERENCES subscription_plan(id),
 CONSTRAINT fk_subscription_actor FOREIGN KEY(created_by) REFERENCES app_user(id),
 CONSTRAINT chk_tenant_subscription_status CHECK(status IN ('TRIAL','ACTIVE','PAST_DUE','SUSPENDED','CANCELLED','EXPIRED')),
 INDEX ix_subscription_tenant_status(tenant_id,status,starts_at), INDEX ix_subscription_expiry(status,ends_at)
);
CREATE TABLE subscription_usage (
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, meter_code VARCHAR(60) NOT NULL,
 period_start DATE NOT NULL, period_end DATE NOT NULL, quantity BIGINT NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_subscription_usage_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 CONSTRAINT uq_subscription_usage_period UNIQUE(tenant_id,meter_code,period_start,period_end),
 CONSTRAINT chk_subscription_usage_quantity CHECK(quantity>=0),
 INDEX ix_subscription_usage_tenant_period(tenant_id,period_start,period_end)
);
CREATE TABLE audit_event (
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16), actor_id BINARY(16), actor_type VARCHAR(30) NOT NULL,
 action VARCHAR(100) NOT NULL, entity_type VARCHAR(100) NOT NULL, entity_id VARCHAR(100),
 outcome VARCHAR(20) NOT NULL, before_json LONGTEXT, after_json LONGTEXT, reason VARCHAR(1000),
 correlation_id VARCHAR(100), created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_audit_event_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 CONSTRAINT fk_audit_event_actor FOREIGN KEY(actor_id) REFERENCES app_user(id),
 INDEX ix_audit_event_tenant_time(tenant_id,created_at),
 INDEX ix_audit_event_tenant_entity(tenant_id,entity_type,entity_id),
 INDEX ix_audit_event_actor_time(actor_id,created_at)
);
CREATE TRIGGER trg_audit_event_no_update BEFORE UPDATE ON audit_event
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='audit_event is append-only';
CREATE TRIGGER trg_audit_event_no_delete BEFORE DELETE ON audit_event
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='audit_event is append-only';
INSERT INTO permission(id,code)
SELECT UUID_TO_BIN(UUID()),src.code FROM (
 SELECT 'BRANCH_MANAGE' code UNION ALL SELECT 'SUBSCRIPTION_MANAGE' UNION ALL SELECT 'AUDIT_VIEW'
) src LEFT JOIN permission p ON p.code=src.code WHERE p.id IS NULL;
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p
WHERE r.tenant_id IS NOT NULL AND r.name='VENDOR_ADMIN' AND p.code IN ('BRANCH_MANAGE','AUDIT_VIEW');
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p
WHERE r.tenant_id IS NULL AND r.name='PLATFORM_SUPER_ADMIN' AND p.code IN ('SUBSCRIPTION_MANAGE','AUDIT_VIEW');
