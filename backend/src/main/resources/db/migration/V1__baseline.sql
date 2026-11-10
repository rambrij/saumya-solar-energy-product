-- Single clean-install baseline consolidated from migrations V1 through V63.
-- Source migration files are retained in Git history for schema-equivalence validation.

-- SOURCE backend/src/main/resources/db/migration/V1__complete_release2.sql
CREATE TABLE tenant(id BINARY(16) PRIMARY KEY,name VARCHAR(150) NOT NULL,slug VARCHAR(100) NOT NULL UNIQUE,status VARCHAR(30) NOT NULL);
CREATE TABLE permission(id BINARY(16) PRIMARY KEY,code VARCHAR(80) NOT NULL UNIQUE);
CREATE TABLE role(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16),name VARCHAR(80) NOT NULL,UNIQUE(tenant_id,name));
CREATE TABLE role_permission(role_id BINARY(16),permission_id BINARY(16),PRIMARY KEY(role_id,permission_id),FOREIGN KEY(role_id) REFERENCES role(id),FOREIGN KEY(permission_id) REFERENCES permission(id));
CREATE TABLE app_user(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16),name VARCHAR(150),email VARCHAR(190) UNIQUE,password_hash VARCHAR(255),account_type VARCHAR(30),enabled BOOLEAN NOT NULL,FOREIGN KEY(tenant_id) REFERENCES tenant(id));
CREATE TABLE user_role(user_id BINARY(16),role_id BINARY(16),PRIMARY KEY(user_id,role_id),FOREIGN KEY(user_id) REFERENCES app_user(id),FOREIGN KEY(role_id) REFERENCES role(id));
CREATE TABLE product_category(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16) NOT NULL,name VARCHAR(120) NOT NULL,description VARCHAR(500),active BOOLEAN NOT NULL,UNIQUE(tenant_id,name),FOREIGN KEY(tenant_id) REFERENCES tenant(id));
CREATE TABLE product(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16) NOT NULL,category_id BINARY(16),sku VARCHAR(80) NOT NULL,name VARCHAR(180) NOT NULL,description TEXT,price DECIMAL(19,2) NOT NULL,tax_percent DECIMAL(5,2) NOT NULL,active BOOLEAN NOT NULL,attributes_json JSON,UNIQUE(tenant_id,sku),FOREIGN KEY(category_id) REFERENCES product_category(id));
CREATE TABLE solar_package(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16) NOT NULL,code VARCHAR(80) NOT NULL,name VARCHAR(180) NOT NULL,capacity_kw DECIMAL(10,2),package_type VARCHAR(40),base_price DECIMAL(19,2),warranty_years INT,active BOOLEAN NOT NULL,UNIQUE(tenant_id,code));
CREATE TABLE package_item(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16) NOT NULL,package_id BINARY(16),product_id BINARY(16),quantity INT,optional_item BOOLEAN,FOREIGN KEY(package_id) REFERENCES solar_package(id),FOREIGN KEY(product_id) REFERENCES product(id));
CREATE TABLE customer_profile(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16) NOT NULL,user_id BINARY(16),name VARCHAR(150),email VARCHAR(190),phone VARCHAR(30),address VARCHAR(500),UNIQUE(tenant_id,email),FOREIGN KEY(user_id) REFERENCES app_user(id));
CREATE TABLE enquiry(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16) NOT NULL,customer_id BINARY(16),name VARCHAR(150),email VARCHAR(190),phone VARCHAR(30),property_type VARCHAR(50),monthly_bill DECIMAL(19,2),required_capacity_kw DECIMAL(10,2),message TEXT,status VARCHAR(30),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE sales_lead(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16) NOT NULL,enquiry_id BINARY(16),customer_id BINARY(16),stage VARCHAR(40),priority VARCHAR(20),assigned_user_id BINARY(16),next_follow_up TIMESTAMP NULL,lost_reason VARCHAR(255),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,FOREIGN KEY(assigned_user_id) REFERENCES app_user(id));
CREATE TABLE lead_history(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16),lead_id BINARY(16),old_stage VARCHAR(40),new_stage VARCHAR(40),actor_id BINARY(16),reason VARCHAR(500),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,FOREIGN KEY(lead_id) REFERENCES sales_lead(id));
CREATE TABLE site_survey(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16),lead_id BINARY(16),scheduled_at TIMESTAMP NULL,assigned_user_id BINARY(16),property_type VARCHAR(50),roof_type VARCHAR(50),usable_area_sqft DECIMAL(10,2),shading VARCHAR(100),sanctioned_load_kw DECIMAL(10,2),notes TEXT,status VARCHAR(30),completed_at TIMESTAMP NULL,FOREIGN KEY(lead_id) REFERENCES sales_lead(id));
CREATE TABLE quotation(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16),quote_group_id BINARY(16),quotation_number VARCHAR(60),revision INT,customer_id BINARY(16),lead_id BINARY(16),survey_id BINARY(16),status VARCHAR(40),subtotal DECIMAL(19,2),discount_percent DECIMAL(5,2),discount_amount DECIMAL(19,2),tax_amount DECIMAL(19,2),grand_total DECIMAL(19,2),valid_until DATE,approval_required BOOLEAN,created_by BINARY(16),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,UNIQUE(tenant_id,quotation_number,revision));
CREATE TABLE quotation_item(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16),quotation_id BINARY(16),product_id BINARY(16),package_id BINARY(16),description VARCHAR(255),quantity INT,unit_price DECIMAL(19,2),tax_percent DECIMAL(5,2),line_total DECIMAL(19,2),FOREIGN KEY(quotation_id) REFERENCES quotation(id));
CREATE TABLE customer_order(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16),quotation_id BINARY(16) UNIQUE,customer_id BINARY(16),order_number VARCHAR(60),status VARCHAR(40),total DECIMAL(19,2),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE order_item(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16),order_id BINARY(16),description VARCHAR(255),quantity INT,unit_price DECIMAL(19,2),line_total DECIMAL(19,2),FOREIGN KEY(order_id) REFERENCES customer_order(id));
CREATE TABLE idempotency_record(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16),operation_name VARCHAR(80),idempotency_key VARCHAR(120),resource_id BINARY(16),UNIQUE(tenant_id,operation_name,idempotency_key));
CREATE TABLE refresh_token(id BINARY(16) PRIMARY KEY,user_id BINARY(16) NOT NULL,token VARCHAR(190) NOT NULL UNIQUE,expires_at TIMESTAMP NOT NULL,revoked BOOLEAN NOT NULL DEFAULT FALSE,FOREIGN KEY(user_id) REFERENCES app_user(id));
CREATE TABLE password_reset_token(id BINARY(16) PRIMARY KEY,user_id BINARY(16) NOT NULL,token VARCHAR(190) NOT NULL UNIQUE,expires_at TIMESTAMP NOT NULL,used BOOLEAN NOT NULL DEFAULT FALSE,FOREIGN KEY(user_id) REFERENCES app_user(id));
CREATE INDEX ix_product_tenant_active ON product(tenant_id,active);
CREATE INDEX ix_enquiry_tenant_status ON enquiry(tenant_id,status);
CREATE INDEX ix_lead_tenant_stage ON sales_lead(tenant_id,stage);
CREATE INDEX ix_survey_tenant_status ON site_survey(tenant_id,status);
CREATE INDEX ix_quote_tenant_status ON quotation(tenant_id,status);
CREATE INDEX ix_order_tenant_status ON customer_order(tenant_id,status);

-- SOURCE backend/src/main/resources/db/migration/V2__vendor_employee_management_permission.sql
-- Add USER_MANAGE for already-seeded vendor admin roles.
-- On a fresh database, DataSeeder creates the permission and includes it in VENDOR_ADMIN.
INSERT INTO permission (id, code)
SELECT UUID_TO_BIN(UUID()), 'USER_MANAGE'
WHERE EXISTS (
    SELECT 1 FROM role WHERE name = 'VENDOR_ADMIN' AND tenant_id IS NOT NULL
)
AND NOT EXISTS (
    SELECT 1 FROM permission WHERE code = 'USER_MANAGE'
);

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code = 'USER_MANAGE'
WHERE r.name = 'VENDOR_ADMIN'
  AND r.tenant_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- SOURCE backend/src/main/resources/db/migration/V3__quotation_customer_decision_audit.sql
ALTER TABLE quotation
    ADD COLUMN decision_by BINARY(16) NULL,
    ADD COLUMN decision_at TIMESTAMP NULL;

ALTER TABLE quotation
    ADD CONSTRAINT fk_quotation_decision_by
    FOREIGN KEY (decision_by) REFERENCES app_user(id);

-- SOURCE backend/src/main/resources/db/migration/V4__user_access_token_version.sql
ALTER TABLE app_user
    ADD COLUMN token_version INT NOT NULL DEFAULT 0;

-- SOURCE backend/src/main/resources/db/migration/V5__permission_role_and_catalog_bootstrap.sql
INSERT INTO permission (id, code)
SELECT UUID_TO_BIN(UUID()), src.code FROM (
 SELECT 'PLATFORM_MANAGE' code UNION ALL SELECT 'CATALOG_MANAGE' UNION ALL SELECT 'CUSTOMER_MANAGE'
 UNION ALL SELECT 'LEAD_MANAGE' UNION ALL SELECT 'SURVEY_MANAGE' UNION ALL SELECT 'QUOTATION_MANAGE'
 UNION ALL SELECT 'DISCOUNT_APPROVE' UNION ALL SELECT 'ORDER_CREATE' UNION ALL SELECT 'USER_VIEW'
 UNION ALL SELECT 'USER_MANAGE' UNION ALL SELECT 'INVENTORY_MANAGE' UNION ALL SELECT 'SERVICE_MANAGE'
 UNION ALL SELECT 'FINANCE_MANAGE' UNION ALL SELECT 'REPORT_VIEW'
) src LEFT JOIN permission p ON p.code=src.code WHERE p.id IS NULL;

INSERT INTO role (id,tenant_id,name)
SELECT UUID_TO_BIN(UUID()),t.id,r.name FROM tenant t CROSS JOIN (
 SELECT 'VENDOR_ADMIN' name UNION ALL SELECT 'SALES_EXECUTIVE' UNION ALL SELECT 'SALES_MANAGER'
 UNION ALL SELECT 'MANAGER' UNION ALL SELECT 'INVENTORY_MANAGER' UNION ALL SELECT 'SERVICE_ENGINEER'
 UNION ALL SELECT 'ACCOUNTANT' UNION ALL SELECT 'INSTALLER' UNION ALL SELECT 'OPERATIONS_MANAGER'
) r LEFT JOIN role existing ON existing.tenant_id=t.id AND existing.name=r.name WHERE existing.id IS NULL;

INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p
WHERE r.tenant_id IS NOT NULL AND r.name='VENDOR_ADMIN' AND p.code IN
('CATALOG_MANAGE','CUSTOMER_MANAGE','LEAD_MANAGE','SURVEY_MANAGE','QUOTATION_MANAGE','DISCOUNT_APPROVE','ORDER_CREATE','USER_VIEW','USER_MANAGE','INVENTORY_MANAGE','SERVICE_MANAGE','FINANCE_MANAGE','REPORT_VIEW');

INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p
WHERE r.tenant_id IS NULL AND r.name='PLATFORM_SUPER_ADMIN' AND p.code='PLATFORM_MANAGE';

INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p WHERE r.tenant_id IS NOT NULL AND (
(r.name='SALES_EXECUTIVE' AND p.code IN ('CUSTOMER_MANAGE','LEAD_MANAGE','SURVEY_MANAGE','QUOTATION_MANAGE','ORDER_CREATE')) OR
(r.name IN ('SALES_MANAGER','MANAGER') AND p.code IN ('CUSTOMER_MANAGE','LEAD_MANAGE','SURVEY_MANAGE','QUOTATION_MANAGE','DISCOUNT_APPROVE','ORDER_CREATE','REPORT_VIEW')) OR
(r.name='INVENTORY_MANAGER' AND p.code IN ('CATALOG_MANAGE','INVENTORY_MANAGE','ORDER_CREATE')) OR
(r.name IN ('SERVICE_ENGINEER','INSTALLER') AND p.code IN ('SURVEY_MANAGE','SERVICE_MANAGE')) OR
(r.name='ACCOUNTANT' AND p.code IN ('FINANCE_MANAGE','REPORT_VIEW')) OR
(r.name='OPERATIONS_MANAGER' AND p.code IN ('ORDER_CREATE','SURVEY_MANAGE','INVENTORY_MANAGE','SERVICE_MANAGE','REPORT_VIEW'))
);

INSERT INTO product_category(id,tenant_id,name,description,active)
SELECT UUID_TO_BIN(UUID()),t.id,c.name,'Solar catalogue category',TRUE
FROM tenant t CROSS JOIN (
 SELECT 'Solar Panels' name UNION ALL SELECT 'Inverters' UNION ALL SELECT 'Batteries'
 UNION ALL SELECT 'Mounting Structures' UNION ALL SELECT 'Cables'
 UNION ALL SELECT 'AC/DC Distribution Boxes' UNION ALL SELECT 'Solar Accessories'
) c LEFT JOIN product_category existing ON existing.tenant_id=t.id AND existing.name=c.name
WHERE existing.id IS NULL;

-- SOURCE backend/src/main/resources/db/migration/V6__require_product_category.sql
UPDATE product p
JOIN (SELECT tenant_id, MIN(id) category_id FROM product_category GROUP BY tenant_id) c
ON c.tenant_id=p.tenant_id
SET p.category_id=c.category_id
WHERE p.category_id IS NULL;

ALTER TABLE product MODIFY COLUMN category_id BINARY(16) NOT NULL;

-- SOURCE backend/src/main/resources/db/migration/V7__catalogue_read_permission.sql
INSERT INTO permission(id,code)
SELECT UUID_TO_BIN(UUID()),'CATALOG_VIEW'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='CATALOG_VIEW');

INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code='CATALOG_VIEW'
WHERE r.tenant_id IS NOT NULL AND r.name IN ('SALES_EXECUTIVE','SALES_MANAGER','MANAGER');

-- SOURCE backend/src/main/resources/db/migration/V8__lead_assignment_history.sql
ALTER TABLE lead_history
    ADD COLUMN event_type VARCHAR(30) NOT NULL DEFAULT 'STAGE',
    ADD COLUMN old_assigned_user_id BINARY(16) NULL,
    ADD COLUMN new_assigned_user_id BINARY(16) NULL;

CREATE INDEX ix_lead_history_tenant_lead_created
    ON lead_history(tenant_id, lead_id, created_at);

-- SOURCE backend/src/main/resources/db/migration/V9__tenant_discount_approval_policy.sql
ALTER TABLE tenant
    ADD COLUMN discount_approval_threshold DECIMAL(5,2) NOT NULL DEFAULT 10.00;

INSERT INTO permission(id,code)
SELECT UUID_TO_BIN(UUID()),'DISCOUNT_POLICY_MANAGE'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='DISCOUNT_POLICY_MANAGE');

INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id
FROM role r JOIN permission p ON p.code='DISCOUNT_POLICY_MANAGE'
WHERE r.tenant_id IS NOT NULL AND r.name='VENDOR_ADMIN';

-- SOURCE backend/src/main/resources/db/migration/V10__global_identity_roles.sql
INSERT INTO role(id,tenant_id,name)
SELECT UUID_TO_BIN(UUID()),NULL,'PLATFORM_SUPER_ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM role WHERE tenant_id IS NULL AND name='PLATFORM_SUPER_ADMIN');

INSERT INTO role(id,tenant_id,name)
SELECT UUID_TO_BIN(UUID()),NULL,'CUSTOMER'
WHERE NOT EXISTS (SELECT 1 FROM role WHERE tenant_id IS NULL AND name='CUSTOMER');

INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code='PLATFORM_MANAGE'
WHERE r.tenant_id IS NULL AND r.name='PLATFORM_SUPER_ADMIN';

-- SOURCE backend/src/main/resources/db/migration/V11__release3_operations.sql
-- Release 3: Operations, inventory ledger, installation, commissioning and warranty.
ALTER TABLE product ADD COLUMN manufacturer VARCHAR(150) NULL, ADD COLUMN model VARCHAR(120) NULL, ADD COLUMN technology VARCHAR(120) NULL, ADD COLUMN purchase_price DECIMAL(19,2) NOT NULL DEFAULT 0, ADD COLUMN discount_percent DECIMAL(5,2) NOT NULL DEFAULT 0, ADD COLUMN warranty_years INT NULL, ADD COLUMN image_url VARCHAR(1000) NULL, ADD COLUMN datasheet_url VARCHAR(1000) NULL, ADD COLUMN reorder_level DECIMAL(19,4) NOT NULL DEFAULT 0, ADD COLUMN unit_of_measure VARCHAR(30) NOT NULL DEFAULT 'PCS', ADD COLUMN hsn_code VARCHAR(40) NULL, ADD COLUMN serialized BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE customer_profile ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE order_item ADD COLUMN product_id BINARY(16) NULL, ADD COLUMN package_id BINARY(16) NULL;
CREATE INDEX ix_order_item_order_product ON order_item(tenant_id,order_id,product_id);
CREATE TABLE order_status_history(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16) NOT NULL,order_id BINARY(16) NOT NULL,old_status VARCHAR(40),new_status VARCHAR(40) NOT NULL,reason VARCHAR(500) NOT NULL,actor_id BINARY(16) NOT NULL,created_at TIMESTAMP(6) NOT NULL,FOREIGN KEY(order_id) REFERENCES customer_order(id),INDEX ix_order_history_tenant_order_time(tenant_id,order_id,created_at));
CREATE TABLE warehouse(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16) NOT NULL,code VARCHAR(50) NOT NULL,name VARCHAR(150) NOT NULL,address VARCHAR(700),active BOOLEAN NOT NULL DEFAULT TRUE,created_at TIMESTAMP(6) NOT NULL,FOREIGN KEY(tenant_id) REFERENCES tenant(id),UNIQUE(tenant_id,code),INDEX ix_warehouse_tenant_active(tenant_id,active));
CREATE TABLE inventory_balance(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16) NOT NULL,warehouse_id BINARY(16) NOT NULL,product_id BINARY(16) NOT NULL,on_hand DECIMAL(19,4) NOT NULL DEFAULT 0,reserved DECIMAL(19,4) NOT NULL DEFAULT 0,damaged DECIMAL(19,4) NOT NULL DEFAULT 0,version BIGINT NOT NULL DEFAULT 0,FOREIGN KEY(tenant_id) REFERENCES tenant(id),FOREIGN KEY(warehouse_id) REFERENCES warehouse(id),FOREIGN KEY(product_id) REFERENCES product(id),UNIQUE(tenant_id,warehouse_id,product_id),CHECK(on_hand>=0 AND reserved>=0 AND damaged>=0 AND reserved+damaged<=on_hand),INDEX ix_inventory_balance_tenant_product(tenant_id,product_id));
CREATE TABLE inventory_transaction(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16) NOT NULL,warehouse_id BINARY(16) NOT NULL,product_id BINARY(16) NOT NULL,order_id BINARY(16) NULL,installation_id BINARY(16) NULL,type VARCHAR(40) NOT NULL,quantity_delta DECIMAL(19,4) NOT NULL,reason VARCHAR(500) NOT NULL,actor_id BINARY(16) NOT NULL,created_at TIMESTAMP(6) NOT NULL,FOREIGN KEY(tenant_id) REFERENCES tenant(id),FOREIGN KEY(warehouse_id) REFERENCES warehouse(id),FOREIGN KEY(product_id) REFERENCES product(id),FOREIGN KEY(order_id) REFERENCES customer_order(id),INDEX ix_inv_tx_tenant_time(tenant_id,created_at),INDEX ix_inv_tx_tenant_product(tenant_id,product_id));
CREATE TABLE inventory_reservation(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16) NOT NULL,order_id BINARY(16) NOT NULL,warehouse_id BINARY(16) NOT NULL,product_id BINARY(16) NOT NULL,quantity DECIMAL(19,4) NOT NULL,status VARCHAR(20) NOT NULL,created_at TIMESTAMP(6) NOT NULL,updated_at TIMESTAMP(6) NOT NULL,FOREIGN KEY(tenant_id) REFERENCES tenant(id),FOREIGN KEY(order_id) REFERENCES customer_order(id),FOREIGN KEY(warehouse_id) REFERENCES warehouse(id),FOREIGN KEY(product_id) REFERENCES product(id),CHECK(quantity>0),INDEX ix_reservation_tenant_order_status(tenant_id,order_id,status));
CREATE TABLE installation_job(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16) NOT NULL,order_id BINARY(16) NOT NULL,customer_id BINARY(16) NOT NULL,site_address VARCHAR(700) NOT NULL,scheduled_at TIMESTAMP(6) NOT NULL,assigned_engineer_id BINARY(16) NOT NULL,team_name VARCHAR(150),capacity_kw DECIMAL(10,2),notes TEXT,status VARCHAR(30) NOT NULL,customer_signed_off BOOLEAN NOT NULL DEFAULT FALSE,completed_at TIMESTAMP(6) NULL,commissioned_at TIMESTAMP(6) NULL,commissioning_notes TEXT,reschedule_reason VARCHAR(500),created_at TIMESTAMP(6) NOT NULL,FOREIGN KEY(tenant_id) REFERENCES tenant(id),FOREIGN KEY(order_id) REFERENCES customer_order(id),FOREIGN KEY(customer_id) REFERENCES customer_profile(id),FOREIGN KEY(assigned_engineer_id) REFERENCES app_user(id),UNIQUE(tenant_id,order_id),INDEX ix_install_tenant_schedule(tenant_id,scheduled_at,status));
CREATE TABLE installation_checklist_item(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16) NOT NULL,installation_id BINARY(16) NOT NULL,code VARCHAR(60) NOT NULL,title VARCHAR(200) NOT NULL,mandatory BOOLEAN NOT NULL DEFAULT TRUE,completed BOOLEAN NOT NULL DEFAULT FALSE,evidence_url VARCHAR(1000),notes VARCHAR(2000),completed_by BINARY(16) NULL,completed_at TIMESTAMP(6) NULL,FOREIGN KEY(tenant_id) REFERENCES tenant(id),FOREIGN KEY(installation_id) REFERENCES installation_job(id),FOREIGN KEY(completed_by) REFERENCES app_user(id),UNIQUE(installation_id,code),INDEX ix_checklist_tenant_installation(tenant_id,installation_id));
CREATE TABLE warranty(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16) NOT NULL,order_id BINARY(16) NOT NULL,installation_id BINARY(16) NOT NULL,customer_id BINARY(16) NOT NULL,product_id BINARY(16) NOT NULL,serial_number VARCHAR(150) NULL,quantity INT NOT NULL DEFAULT 1,start_date DATE NOT NULL,end_date DATE NOT NULL,terms VARCHAR(2000),status VARCHAR(20) NOT NULL,activated_at TIMESTAMP(6) NOT NULL,FOREIGN KEY(tenant_id) REFERENCES tenant(id),FOREIGN KEY(order_id) REFERENCES customer_order(id),FOREIGN KEY(installation_id) REFERENCES installation_job(id),FOREIGN KEY(customer_id) REFERENCES customer_profile(id),FOREIGN KEY(product_id) REFERENCES product(id),UNIQUE(tenant_id,serial_number),INDEX ix_warranty_tenant_customer(tenant_id,customer_id,status));

-- SOURCE backend/src/main/resources/db/migration/V12__installation_teams_checklist_templates_and_inventory_dimensions.sql
ALTER TABLE inventory_transaction ADD COLUMN reserved_delta DECIMAL(19,4) NOT NULL DEFAULT 0, ADD COLUMN damaged_delta DECIMAL(19,4) NOT NULL DEFAULT 0;
CREATE TABLE installation_team(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16) NOT NULL,name VARCHAR(150) NOT NULL,member_ids_json VARCHAR(2000),active BOOLEAN NOT NULL DEFAULT TRUE,created_at TIMESTAMP(6) NOT NULL,FOREIGN KEY(tenant_id) REFERENCES tenant(id),UNIQUE(tenant_id,name),INDEX ix_install_team_tenant_active(tenant_id,active));
ALTER TABLE installation_job ADD COLUMN team_id BINARY(16) NULL;
ALTER TABLE installation_job ADD CONSTRAINT fk_install_job_team FOREIGN KEY(team_id) REFERENCES installation_team(id);
CREATE TABLE installation_checklist_template(id BINARY(16) PRIMARY KEY,tenant_id BINARY(16) NOT NULL,code VARCHAR(60) NOT NULL,title VARCHAR(200) NOT NULL,mandatory BOOLEAN NOT NULL DEFAULT TRUE,active BOOLEAN NOT NULL DEFAULT TRUE,created_at TIMESTAMP(6) NOT NULL,FOREIGN KEY(tenant_id) REFERENCES tenant(id),UNIQUE(tenant_id,code),INDEX ix_check_template_tenant_active(tenant_id,active));

-- SOURCE backend/src/main/resources/db/migration/V13__technician_and_service_manager_roles.sql
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

-- SOURCE backend/src/main/resources/db/migration/V14__order_tax_snapshot_and_immutable_inventory_ledger.sql
-- Release 3 hardening marker.
-- The tax_percent column is ensured by V15__ensure_order_tax_snapshot_column.java,
-- which is safe to re-run after MySQL's non-transactional partial DDL behavior.
-- Inventory transactions are append-only in the application repository/service.
-- Do not create triggers here: managed MySQL instances with binary logging commonly
-- deny trigger creation to the application account (ERROR 1419 / SUPER privilege).

-- SOURCE backend/src/main/resources/db/migration/V15__ensure_order_tax_snapshot_column.sql
-- MySQL DDL is not transactional. V14 may have added this column before a later
-- statement failed, so make the tax snapshot column addition idempotent.
SET @tax_percent_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'order_item'
      AND column_name = 'tax_percent'
);
SET @tax_percent_ddl = IF(
    @tax_percent_exists = 0,
    'ALTER TABLE order_item ADD COLUMN tax_percent DECIMAL(5,2) NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE tax_percent_stmt FROM @tax_percent_ddl;
EXECUTE tax_percent_stmt;
DEALLOCATE PREPARE tax_percent_stmt;


-- SOURCE backend/src/main/resources/db/migration/V16__repair_platform_permission_and_package_images.sql
-- Restore the platform tenant-management grant for databases where the
-- original global role was created before its permission mapping was present.
INSERT INTO permission (id, code)
SELECT UUID_TO_BIN(UUID()), 'PLATFORM_MANAGE'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'PLATFORM_MANAGE');

INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code = 'PLATFORM_MANAGE'
WHERE r.tenant_id IS NULL
  AND r.name = 'PLATFORM_SUPER_ADMIN';

-- Existing packages remain valid and render the frontend fallback image.
-- Make the column addition safe if a DBA already added it during recovery.
SET @package_image_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'solar_package'
      AND column_name = 'image_url'
);
SET @package_image_ddl = IF(
    @package_image_column_exists = 0,
    'ALTER TABLE solar_package ADD COLUMN image_url VARCHAR(1024) NULL',
    'SELECT 1'
);
PREPARE package_image_stmt FROM @package_image_ddl;
EXECUTE package_image_stmt;
DEALLOCATE PREPARE package_image_stmt;

-- SOURCE backend/src/main/resources/db/migration/V17__tenant_admin_dashboard_audit_timestamps.sql
ALTER TABLE tenant
    ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
UPDATE tenant SET updated_at=created_at WHERE updated_at IS NULL;
CREATE INDEX ix_tenant_status_created ON tenant(status, created_at);

-- SOURCE backend/src/main/resources/db/migration/V18__release4_finance_and_service.sql
-- Release 4 finance, payments, service management and SLA configuration.
ALTER TABLE tenant ADD COLUMN gstin VARCHAR(15) NULL, ADD COLUMN invoice_prefix VARCHAR(12) NOT NULL DEFAULT 'INV', ADD COLUMN service_reopen_days INT NULL;
ALTER TABLE customer_profile ADD COLUMN gstin VARCHAR(15) NULL;
ALTER TABLE inventory_transaction ADD COLUMN service_request_id BINARY(16) NULL;
CREATE INDEX ix_inventory_tx_service_request ON inventory_transaction(tenant_id,service_request_id);

CREATE TABLE invoice_sequence(
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, fiscal_year VARCHAR(9) NOT NULL,
 next_number BIGINT NOT NULL DEFAULT 0,
 UNIQUE KEY uq_invoice_sequence_tenant_year(tenant_id,fiscal_year),
 CONSTRAINT fk_invoice_sequence_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id)
);
CREATE TABLE invoice(
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, order_id BINARY(16) NOT NULL,
 invoice_number VARCHAR(80) NOT NULL, fiscal_year VARCHAR(9) NOT NULL, status VARCHAR(24) NOT NULL,
 customer_name VARCHAR(150) NOT NULL, customer_email VARCHAR(190), customer_address VARCHAR(700),
 customer_gstin VARCHAR(15), vendor_name VARCHAR(150) NOT NULL, vendor_gstin VARCHAR(15),
 currency CHAR(3) NOT NULL DEFAULT 'INR', subtotal DECIMAL(19,4) NOT NULL,
 tax_total DECIMAL(19,4) NOT NULL, grand_total DECIMAL(19,4) NOT NULL,
 tax_treatment VARCHAR(20) NULL, due_date DATE, issued_at TIMESTAMP(6) NOT NULL,
 created_by BINARY(16) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
 void_reason VARCHAR(500), UNIQUE KEY uq_invoice_tenant_number(tenant_id,invoice_number),
 UNIQUE KEY uq_invoice_order(tenant_id,order_id),
 CONSTRAINT fk_invoice_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 CONSTRAINT fk_invoice_order FOREIGN KEY(order_id) REFERENCES customer_order(id),
 INDEX ix_invoice_tenant_status_date(tenant_id,status,issued_at)
);
CREATE TABLE invoice_item(
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, invoice_id BINARY(16) NOT NULL,
 product_id BINARY(16), description VARCHAR(255) NOT NULL, hsn_code VARCHAR(40),
 quantity INT NOT NULL, unit_price DECIMAL(19,4) NOT NULL, tax_percent DECIMAL(7,4) NOT NULL,
 taxable_amount DECIMAL(19,4) NOT NULL, tax_amount DECIMAL(19,4) NOT NULL,
 line_total DECIMAL(19,4) NOT NULL,
 CONSTRAINT fk_invoice_item_invoice FOREIGN KEY(invoice_id) REFERENCES invoice(id),
 CONSTRAINT fk_invoice_item_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 INDEX ix_invoice_item_tenant_invoice(tenant_id,invoice_id)
);
CREATE TABLE payment(
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, invoice_id BINARY(16) NOT NULL,
 order_id BINARY(16) NOT NULL, amount DECIMAL(19,4) NOT NULL, currency CHAR(3) NOT NULL DEFAULT 'INR',
 method VARCHAR(30) NOT NULL, source VARCHAR(20) NOT NULL, status VARCHAR(24) NOT NULL,
 reference VARCHAR(150), provider VARCHAR(60), provider_reference VARCHAR(150),
 paid_at TIMESTAMP(6) NOT NULL, recorded_by BINARY(16) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_payment_invoice FOREIGN KEY(invoice_id) REFERENCES invoice(id),
 CONSTRAINT fk_payment_order FOREIGN KEY(order_id) REFERENCES customer_order(id),
 CONSTRAINT fk_payment_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 UNIQUE KEY uq_payment_provider_ref(provider,provider_reference),
 INDEX ix_payment_tenant_invoice(tenant_id,invoice_id,status)
);
CREATE TABLE payment_webhook_event(
 id BINARY(16) PRIMARY KEY, provider VARCHAR(60) NOT NULL, event_id VARCHAR(180) NOT NULL,
 payload_hash VARCHAR(64) NOT NULL, processing_status VARCHAR(24) NOT NULL,
 received_at TIMESTAMP(6) NOT NULL, processed_at TIMESTAMP(6),
 UNIQUE KEY uq_payment_webhook_provider_event(provider,event_id)
);
CREATE TABLE payment_refund(
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, payment_id BINARY(16) NOT NULL,
 amount DECIMAL(19,4) NOT NULL, reason VARCHAR(500) NOT NULL, status VARCHAR(24) NOT NULL,
 recorded_by BINARY(16) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_refund_payment FOREIGN KEY(payment_id) REFERENCES payment(id),
 CONSTRAINT fk_refund_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 INDEX ix_refund_tenant_payment(tenant_id,payment_id)
);
CREATE TABLE service_sla_policy(
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, priority VARCHAR(20) NOT NULL,
 response_minutes INT NOT NULL, resolution_minutes INT NOT NULL, time_zone VARCHAR(80) NOT NULL DEFAULT 'Asia/Kolkata',
 business_start TIME NULL, business_end TIME NULL, holidays_json JSON NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 UNIQUE KEY uq_service_sla_tenant_priority(tenant_id,priority),
 CONSTRAINT fk_service_sla_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 CHECK(response_minutes>0 AND resolution_minutes>=response_minutes)
);
CREATE TABLE service_request(
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, ticket_number VARCHAR(80) NOT NULL,
 customer_id BINARY(16) NOT NULL, installation_id BINARY(16), product_id BINARY(16), serial_number VARCHAR(150),
 category VARCHAR(50) NOT NULL, priority VARCHAR(20) NOT NULL, description VARCHAR(4000) NOT NULL,
 warranty_status VARCHAR(30) NOT NULL, status VARCHAR(30) NOT NULL, assigned_engineer_id BINARY(16),
 response_due_at TIMESTAMP(6), resolution_due_at TIMESTAMP(6), first_responded_at TIMESTAMP(6),
 resolved_at TIMESTAMP(6), customer_confirmed_at TIMESTAMP(6), parts_consumed_json JSON NULL, closed_at TIMESTAMP(6),
 paused_seconds BIGINT NOT NULL DEFAULT 0, sla_paused_at TIMESTAMP(6), response_breached_at TIMESTAMP(6), resolution_breached_at TIMESTAMP(6), resolution_notes VARCHAR(2000),
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 UNIQUE KEY uq_service_ticket_tenant_number(tenant_id,ticket_number),
 CONSTRAINT fk_service_request_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 CONSTRAINT fk_service_request_customer FOREIGN KEY(customer_id) REFERENCES customer_profile(id),
 CONSTRAINT fk_service_request_installation FOREIGN KEY(installation_id) REFERENCES installation_job(id),
 CONSTRAINT fk_service_request_product FOREIGN KEY(product_id) REFERENCES product(id),
 CONSTRAINT fk_service_request_engineer FOREIGN KEY(assigned_engineer_id) REFERENCES app_user(id),
 INDEX ix_service_tenant_status_created(tenant_id,status,created_at),
 INDEX ix_service_tenant_assignee_status(tenant_id,assigned_engineer_id,status)
);
CREATE TABLE service_visit(
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, service_request_id BINARY(16) NOT NULL,
 engineer_id BINARY(16) NOT NULL, scheduled_at TIMESTAMP(6) NOT NULL, started_at TIMESTAMP(6),
 completed_at TIMESTAMP(6), engineer_notes VARCHAR(4000), resolution VARCHAR(2000),
 evidence_url VARCHAR(1000), customer_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
 customer_confirmed_at TIMESTAMP(6), parts_consumed_json JSON NULL,
 CONSTRAINT fk_service_visit_request FOREIGN KEY(service_request_id) REFERENCES service_request(id),
 CONSTRAINT fk_service_visit_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 CONSTRAINT fk_service_visit_engineer FOREIGN KEY(engineer_id) REFERENCES app_user(id),
 INDEX ix_service_visit_tenant_schedule(tenant_id,scheduled_at)
);
CREATE TABLE service_event(
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, service_request_id BINARY(16) NOT NULL,
 actor_id BINARY(16) NULL, event_type VARCHAR(40) NOT NULL, old_status VARCHAR(30), new_status VARCHAR(30),
 reason VARCHAR(2000), occurred_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_service_event_request FOREIGN KEY(service_request_id) REFERENCES service_request(id),
 CONSTRAINT fk_service_event_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 INDEX ix_service_event_tenant_ticket_time(tenant_id,service_request_id,occurred_at)
);

-- SOURCE backend/src/main/resources/db/migration/V19__finance_refund_permissions.sql
INSERT INTO permission(id,code)
SELECT UUID_TO_BIN(UUID()),'PAYMENT_REFUND'
WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='PAYMENT_REFUND');

INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code='PAYMENT_REFUND'
WHERE r.tenant_id IS NOT NULL AND r.name IN ('VENDOR_ADMIN','ACCOUNTANT');

-- SOURCE backend/src/main/resources/db/migration/V20__invoice_credit_notes_and_tax_breakdown.sql
ALTER TABLE invoice ADD COLUMN cgst_amount DECIMAL(19,4) NOT NULL DEFAULT 0, ADD COLUMN sgst_amount DECIMAL(19,4) NOT NULL DEFAULT 0, ADD COLUMN igst_amount DECIMAL(19,4) NOT NULL DEFAULT 0;
CREATE TABLE credit_note(
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, invoice_id BINARY(16) NOT NULL,
 credit_note_number VARCHAR(100) NOT NULL, reason VARCHAR(500) NOT NULL, amount DECIMAL(19,4) NOT NULL,
 status VARCHAR(24) NOT NULL, created_by BINARY(16) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
 UNIQUE KEY uq_credit_note_tenant_number(tenant_id,credit_note_number),
 CONSTRAINT fk_credit_note_invoice FOREIGN KEY(invoice_id) REFERENCES invoice(id),
 CONSTRAINT fk_credit_note_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 INDEX ix_credit_note_tenant_invoice(tenant_id,invoice_id)
);

-- SOURCE backend/src/main/resources/db/migration/V21__service_business_calendar_and_reopen_policy.sql
ALTER TABLE service_sla_policy ADD COLUMN working_days_json JSON NULL;

-- SOURCE backend/src/main/resources/db/migration/V22__private_service_attachments.sql
CREATE TABLE service_attachment(
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, service_request_id BINARY(16) NOT NULL,
 original_name VARCHAR(255) NOT NULL, storage_key VARCHAR(120) NOT NULL, content_type VARCHAR(100) NOT NULL,
 size_bytes BIGINT NOT NULL, uploaded_by BINARY(16) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_service_attachment_ticket FOREIGN KEY(service_request_id) REFERENCES service_request(id),
 CONSTRAINT fk_service_attachment_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 CONSTRAINT fk_service_attachment_user FOREIGN KEY(uploaded_by) REFERENCES app_user(id),
 INDEX ix_service_attachment_tenant_ticket(tenant_id,service_request_id,created_at)
);

-- SOURCE backend/src/main/resources/db/migration/V23__notification_delivery.sql
CREATE TABLE notification_delivery (
    id BINARY(16) PRIMARY KEY,
    tenant_id BINARY(16) NULL,
    user_id BINARY(16) NULL,
    event_type VARCHAR(60) NOT NULL,
    recipient VARCHAR(320) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    sent_at TIMESTAMP(6) NULL,
    last_error VARCHAR(1000) NULL,
    CONSTRAINT fk_notification_delivery_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_notification_delivery_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT chk_notification_delivery_status CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    INDEX ix_notification_delivery_tenant_created (tenant_id, created_at),
    INDEX ix_notification_delivery_status_created (status, created_at)
);

-- SOURCE backend/src/main/resources/db/migration/V24__platform_mfa_challenge.sql
CREATE TABLE mfa_challenge (
    id BINARY(16) PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_mfa_challenge_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    INDEX ix_mfa_challenge_user_created (user_id, created_at),
    INDEX ix_mfa_challenge_expiry (expires_at)
);

-- SOURCE backend/src/main/resources/db/migration/V25__accepted_quotation_financial_snapshots.sql
-- Preserve the accepted quotation's immutable financial calculation on the order.
ALTER TABLE customer_order
    ADD COLUMN subtotal DECIMAL(19,4) NULL,
    ADD COLUMN discount_percent DECIMAL(7,4) NOT NULL DEFAULT 0,
    ADD COLUMN discount_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN tax_total DECIMAL(19,4) NULL;

UPDATE customer_order co
JOIN quotation q ON q.id = co.quotation_id AND q.tenant_id = co.tenant_id
SET co.subtotal = q.subtotal,
    co.discount_percent = COALESCE(q.discount_percent, 0),
    co.discount_amount = COALESCE(q.discount_amount, 0),
    co.tax_total = q.tax_amount
WHERE co.quotation_id IS NOT NULL;

-- Legacy direct-product orders do not have a quotation. Their existing total is retained;
-- subtotal/tax remain nullable so the invoicing service can use their line snapshots.

-- SOURCE backend/src/main/resources/db/migration/V26__quotation_and_order_line_financial_snapshots.sql
-- Store the exact rounded per-line values used by the accepted quotation.
-- Existing historical rows remain NULL and are handled by the legacy invoice fallback.
ALTER TABLE quotation_item
    ADD COLUMN discount_amount DECIMAL(19,4) NULL,
    ADD COLUMN taxable_amount DECIMAL(19,4) NULL,
    ADD COLUMN tax_amount DECIMAL(19,4) NULL;

ALTER TABLE order_item
    ADD COLUMN discount_amount DECIMAL(19,4) NULL,
    ADD COLUMN taxable_amount DECIMAL(19,4) NULL,
    ADD COLUMN tax_amount DECIMAL(19,4) NULL;

-- SOURCE backend/src/main/resources/db/migration/V27__survey_lifecycle_and_technical_assessment.sql
ALTER TABLE site_survey
    ADD COLUMN latitude DECIMAL(10,7) NULL,
    ADD COLUMN longitude DECIMAL(10,7) NULL,
    ADD COLUMN roof_orientation VARCHAR(40) NULL,
    ADD COLUMN monthly_electricity_bill DECIMAL(19,2) NULL,
    ADD COLUMN electrical_phase VARCHAR(30) NULL,
    ADD COLUMN meter_type VARCHAR(40) NULL,
    ADD COLUMN findings TEXT NULL,
    ADD COLUMN recommendation TEXT NULL,
    ADD COLUMN submitted_at TIMESTAMP(6) NULL,
    ADD COLUMN reviewed_by BINARY(16) NULL,
    ADD COLUMN reviewed_at TIMESTAMP(6) NULL,
    ADD COLUMN rejection_reason VARCHAR(1000) NULL,
    ADD INDEX ix_survey_tenant_status_schedule (tenant_id, status, scheduled_at),
    ADD CONSTRAINT fk_site_survey_reviewer FOREIGN KEY (reviewed_by) REFERENCES app_user(id);

CREATE TABLE site_survey_history (
    id BINARY(16) PRIMARY KEY,
    tenant_id BINARY(16) NOT NULL,
    survey_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    old_status VARCHAR(30) NULL,
    new_status VARCHAR(30) NULL,
    reason VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_survey_history_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_survey_history_survey FOREIGN KEY (survey_id) REFERENCES site_survey(id),
    CONSTRAINT fk_survey_history_actor FOREIGN KEY (actor_id) REFERENCES app_user(id),
    INDEX ix_survey_history_tenant_survey_time (tenant_id, survey_id, created_at)
);

-- SOURCE backend/src/main/resources/db/migration/V28__engineer_profiles_and_warranty_claims.sql
CREATE TABLE engineer_profile (
    id BINARY(16) PRIMARY KEY,
    tenant_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    skills_json JSON NOT NULL,
    certifications_json JSON NOT NULL,
    territory VARCHAR(500) NULL,
    available_from TIME NULL,
    available_until TIME NULL,
    max_concurrent_jobs INT NOT NULL DEFAULT 3,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_engineer_profile_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_engineer_profile_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT uq_engineer_profile_user UNIQUE (tenant_id,user_id),
    CONSTRAINT chk_engineer_profile_workload CHECK (max_concurrent_jobs BETWEEN 1 AND 50),
    INDEX ix_engineer_profile_tenant_active (tenant_id,active)
);
CREATE TABLE warranty_claim (
    id BINARY(16) PRIMARY KEY,
    tenant_id BINARY(16) NOT NULL,
    warranty_id BINARY(16) NOT NULL,
    customer_id BINARY(16) NOT NULL,
    claim_number VARCHAR(50) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    evidence_url VARCHAR(1000) NULL,
    status VARCHAR(30) NOT NULL,
    decision_reason VARCHAR(1000) NULL,
    resolution_notes VARCHAR(2000) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    reviewed_by BINARY(16) NULL,
    reviewed_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_warranty_claim_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_warranty_claim_warranty FOREIGN KEY (warranty_id) REFERENCES warranty(id),
    CONSTRAINT fk_warranty_claim_customer FOREIGN KEY (customer_id) REFERENCES customer_profile(id),
    CONSTRAINT fk_warranty_claim_reviewer FOREIGN KEY (reviewed_by) REFERENCES app_user(id),
    CONSTRAINT uq_warranty_claim_number UNIQUE (tenant_id,claim_number),
    CONSTRAINT chk_warranty_claim_status CHECK (status IN ('SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED','RESOLVED','CANCELLED')),
    INDEX ix_warranty_claim_tenant_status_created (tenant_id,status,created_at),
    INDEX ix_warranty_claim_warranty (tenant_id,warranty_id)
);
CREATE TABLE installation_schedule_history (
    id BINARY(16) PRIMARY KEY,
    tenant_id BINARY(16) NOT NULL,
    installation_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    previous_scheduled_at TIMESTAMP(6) NULL,
    new_scheduled_at TIMESTAMP(6) NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_install_schedule_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_install_schedule_job FOREIGN KEY (installation_id) REFERENCES installation_job(id),
    CONSTRAINT fk_install_schedule_actor FOREIGN KEY (actor_id) REFERENCES app_user(id),
    INDEX ix_install_schedule_history (tenant_id,installation_id,created_at)
);
ALTER TABLE notification_delivery
    ADD COLUMN channel VARCHAR(20) NOT NULL DEFAULT 'EMAIL',
    ADD COLUMN idempotency_key VARCHAR(120) NULL,
    ADD COLUMN provider_message_id VARCHAR(120) NULL,
    ADD COLUMN next_attempt_at TIMESTAMP(6) NULL,
    ADD INDEX ix_notification_retry (status,next_attempt_at),
    ADD UNIQUE INDEX uq_notification_idempotency (tenant_id,idempotency_key);

-- SOURCE backend/src/main/resources/db/migration/V29__customer_messaging_consent.sql
ALTER TABLE customer_profile
    ADD COLUMN sms_consent BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN whatsapp_consent BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN messaging_consent_updated_at TIMESTAMP(6) NULL;
ALTER TABLE notification_delivery
    ADD COLUMN message_body TEXT NULL;

-- SOURCE backend/src/main/resources/db/migration/V30__installation_approved_survey_link.sql
ALTER TABLE installation_job
    ADD COLUMN approved_survey_id BINARY(16) NULL,
    ADD CONSTRAINT fk_installation_approved_survey FOREIGN KEY (approved_survey_id) REFERENCES site_survey(id),
    ADD INDEX ix_installation_tenant_survey (tenant_id,approved_survey_id);


-- SOURCE backend/src/main/resources/db/migration/V31__service_ticket_comments.sql
CREATE TABLE service_comment (
    id BINARY(16) PRIMARY KEY,
    tenant_id BINARY(16) NOT NULL,
    service_request_id BINARY(16) NOT NULL,
    author_id BINARY(16) NOT NULL,
    visibility VARCHAR(20) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_service_comment_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_service_comment_ticket FOREIGN KEY (service_request_id) REFERENCES service_request(id),
    CONSTRAINT fk_service_comment_author FOREIGN KEY (author_id) REFERENCES app_user(id),
    CONSTRAINT chk_service_comment_visibility CHECK (visibility IN ('CUSTOMER','INTERNAL')),
    INDEX ix_service_comment_ticket (tenant_id,service_request_id,created_at)
);

-- SOURCE backend/src/main/resources/db/migration/V32__warranty_expiry_lookup_index.sql
-- Supports bounded daily expiry processing without scanning the full warranty table.
CREATE INDEX ix_warranty_status_end_date ON warranty (status, end_date);

-- SOURCE backend/src/main/resources/db/migration/V33__inventory_transaction_reference.sql
ALTER TABLE inventory_transaction
    ADD COLUMN transaction_reference VARCHAR(50) NULL;

UPDATE inventory_transaction
SET transaction_reference = CONCAT('INV-', UPPER(REPLACE(UUID(), '-', '')))
WHERE transaction_reference IS NULL;

ALTER TABLE inventory_transaction
    MODIFY COLUMN transaction_reference VARCHAR(50) NOT NULL,
    ADD UNIQUE INDEX uq_inventory_transaction_reference (tenant_id, transaction_reference);

-- SOURCE backend/src/main/resources/db/migration/V34__inventory_transaction_compensation_link.sql
-- Each original inventory ledger row may have at most one compensating entry per tenant.
ALTER TABLE inventory_transaction
    ADD COLUMN reversal_of_transaction_id BINARY(16) NULL;

CREATE UNIQUE INDEX uq_inventory_transaction_reversal
    ON inventory_transaction (tenant_id, reversal_of_transaction_id);

-- SOURCE backend/src/main/resources/db/migration/V35__site_survey_attachments.sql
CREATE TABLE site_survey_attachment (
    id BINARY(16) PRIMARY KEY,
    tenant_id BINARY(16) NOT NULL,
    survey_id BINARY(16) NOT NULL,
    original_name VARCHAR(200) NOT NULL,
    storage_key VARCHAR(100) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    uploaded_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_survey_attachment_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_survey_attachment_survey FOREIGN KEY (survey_id) REFERENCES site_survey(id),
    CONSTRAINT fk_survey_attachment_uploader FOREIGN KEY (uploaded_by) REFERENCES app_user(id),
    CONSTRAINT uq_survey_attachment_storage_key UNIQUE (storage_key),
    INDEX ix_survey_attachment_scope (tenant_id, survey_id, created_at),
    CONSTRAINT chk_survey_attachment_size CHECK (size_bytes > 0 AND size_bytes <= 10485760)
);

-- SOURCE backend/src/main/resources/db/migration/V36__warranty_claim_attachments.sql
CREATE TABLE warranty_claim_attachment (
    id BINARY(16) PRIMARY KEY,
    tenant_id BINARY(16) NOT NULL,
    claim_id BINARY(16) NOT NULL,
    original_name VARCHAR(200) NOT NULL,
    storage_key VARCHAR(100) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    uploaded_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_warranty_attachment_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_warranty_attachment_claim FOREIGN KEY (claim_id) REFERENCES warranty_claim(id),
    CONSTRAINT fk_warranty_attachment_uploader FOREIGN KEY (uploaded_by) REFERENCES app_user(id),
    CONSTRAINT uq_warranty_attachment_storage_key UNIQUE (storage_key),
    INDEX ix_warranty_attachment_scope (tenant_id, claim_id, created_at),
    CONSTRAINT chk_warranty_attachment_size CHECK (size_bytes > 0 AND size_bytes <= 10485760)
);

-- SOURCE backend/src/main/resources/db/migration/V37__enterprise_saas_foundation.sql
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

-- SOURCE backend/src/main/resources/db/migration/V38__tenant_branding_and_custom_domains.sql
ALTER TABLE tenant
 ADD COLUMN display_name VARCHAR(150) NULL,
 ADD COLUMN logo_url VARCHAR(1000) NULL,
 ADD COLUMN primary_color VARCHAR(7) NOT NULL DEFAULT '#0B6B4B',
 ADD COLUMN secondary_color VARCHAR(7) NOT NULL DEFAULT '#F6C85F',
 ADD COLUMN contact_email VARCHAR(254) NULL,
 ADD COLUMN contact_phone VARCHAR(40) NULL,
 ADD COLUMN support_email VARCHAR(254) NULL,
 ADD COLUMN support_phone VARCHAR(40) NULL;

CREATE TABLE tenant_domain (
 id BINARY(16) PRIMARY KEY,
 tenant_id BINARY(16) NOT NULL,
 domain VARCHAR(253) NOT NULL,
 verification_token VARCHAR(100) NOT NULL,
 status VARCHAR(20) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 verified_at TIMESTAMP(6) NULL,
 CONSTRAINT fk_tenant_domain_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
 CONSTRAINT uq_tenant_domain UNIQUE (domain),
 CONSTRAINT chk_tenant_domain_status CHECK (status IN ('PENDING','VERIFIED','DISABLED')),
 INDEX ix_tenant_domain_tenant_status (tenant_id,status)
);

INSERT INTO permission(id,code)
SELECT UUID_TO_BIN(UUID()),'BRANDING_MANAGE'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='BRANDING_MANAGE');
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p
WHERE r.tenant_id IS NOT NULL AND r.name='VENDOR_ADMIN' AND p.code='BRANDING_MANAGE';

-- SOURCE backend/src/main/resources/db/migration/V39__configurable_approval_workflows.sql
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
 CONSTRAINT uq_approval_instance_tenant_id UNIQUE(tenant_id,id),
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

-- SOURCE backend/src/main/resources/db/migration/V40__tenant_integration_registry_and_sync_jobs.sql
CREATE TABLE integration_connection (
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, provider_code VARCHAR(60) NOT NULL,
 integration_type VARCHAR(30) NOT NULL, display_name VARCHAR(150) NOT NULL, status VARCHAR(20) NOT NULL,
 secret_reference VARCHAR(500), config_json LONGTEXT NOT NULL, created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_integration_connection_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 CONSTRAINT uq_integration_connection_tenant_id UNIQUE(tenant_id,id),
 CONSTRAINT chk_integration_connection_type CHECK(integration_type IN ('ACCOUNTING','PAYMENT_GATEWAY','SMS','WHATSAPP','GENERIC')),
 CONSTRAINT chk_integration_connection_status CHECK(status IN ('ACTIVE','DISABLED','ERROR')),
 INDEX ix_integration_connection_tenant_type(tenant_id,integration_type,status)
);
CREATE TABLE integration_sync_job (
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16) NOT NULL, connection_id BINARY(16) NOT NULL,
 entity_type VARCHAR(60) NOT NULL, entity_id VARCHAR(100) NOT NULL, operation VARCHAR(30) NOT NULL,
 idempotency_key VARCHAR(160) NOT NULL, payload_json LONGTEXT NOT NULL, status VARCHAR(30) NOT NULL,
 attempt_count INT NOT NULL DEFAULT 0, next_attempt_at TIMESTAMP(6) NOT NULL, last_error VARCHAR(1000),
 external_reference VARCHAR(160), created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_integration_job_connection FOREIGN KEY(tenant_id,connection_id) REFERENCES integration_connection(tenant_id,id),
 CONSTRAINT uq_integration_job_idempotency UNIQUE(tenant_id,connection_id,idempotency_key),
 CONSTRAINT chk_integration_job_status CHECK(status IN ('QUEUED','PROCESSING','SUCCEEDED','RETRY','WAITING_FOR_ADAPTER','DEAD_LETTER')),
 INDEX ix_integration_job_dispatch(status,next_attempt_at,created_at),
 INDEX ix_integration_job_tenant_entity(tenant_id,entity_type,entity_id)
);
CREATE TABLE integration_webhook_event (
 id BINARY(16) PRIMARY KEY, tenant_id BINARY(16), provider_code VARCHAR(60) NOT NULL,
 provider_event_id VARCHAR(160) NOT NULL, payload_hash VARCHAR(128) NOT NULL, status VARCHAR(20) NOT NULL,
 received_at TIMESTAMP(6) NOT NULL, processed_at TIMESTAMP(6),
 CONSTRAINT fk_integration_webhook_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 CONSTRAINT uq_integration_webhook_provider_event UNIQUE(provider_code,provider_event_id),
 INDEX ix_integration_webhook_tenant_received(tenant_id,received_at)
);
INSERT INTO permission(id,code) SELECT UUID_TO_BIN(UUID()),'INTEGRATION_MANAGE'
WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='INTEGRATION_MANAGE');
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p WHERE r.tenant_id IS NOT NULL AND r.name='VENDOR_ADMIN' AND p.code='INTEGRATION_MANAGE';

-- SOURCE backend/src/main/resources/db/migration/V41__offline_business_sync_inbox.sql
CREATE TABLE offline_sync_operation (
    id BINARY(16) PRIMARY KEY,
    tenant_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    client_operation_id BINARY(16) NOT NULL,
    operation_type VARCHAR(40) NOT NULL,
    resource_id BINARY(16) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    result_json LONGTEXT,
    error_message VARCHAR(1000),
    client_created_at TIMESTAMP(6) NOT NULL,
    received_at TIMESTAMP(6) NOT NULL,
    applied_at TIMESTAMP(6),
    CONSTRAINT fk_offline_sync_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_offline_sync_actor FOREIGN KEY (actor_id) REFERENCES app_user(id),
    CONSTRAINT uq_offline_sync_client_operation UNIQUE (tenant_id, actor_id, client_operation_id),
    CONSTRAINT chk_offline_sync_status CHECK (status IN ('APPLIED')),
    CONSTRAINT chk_offline_sync_operation_type CHECK (operation_type IN (
        'SERVICE_COMMENT_CREATE', 'SURVEY_UPDATE', 'SURVEY_START', 'SURVEY_SUBMIT'
    )),
    INDEX ix_offline_sync_actor_received (tenant_id, actor_id, received_at),
    INDEX ix_offline_sync_resource (tenant_id, operation_type, resource_id)
);

-- SOURCE backend/src/main/resources/db/migration/V42__provider_neutral_payment_checkout.sql
ALTER TABLE payment
    ADD COLUMN idempotency_key VARCHAR(160) NULL,
    ADD COLUMN checkout_url VARCHAR(1000) NULL;

CREATE UNIQUE INDEX uq_payment_tenant_idempotency
    ON payment(tenant_id, idempotency_key);

-- SOURCE backend/src/main/resources/db/migration/V43__branch_scope_business_records.sql
-- Add nullable branch ownership to operational and commercial records.
-- Existing Phase 1/2 records remain unassigned (NULL) and are not rewritten.
ALTER TABLE enquiry ADD COLUMN branch_id BINARY(16) NULL;
ALTER TABLE sales_lead ADD COLUMN branch_id BINARY(16) NULL;
ALTER TABLE site_survey ADD COLUMN branch_id BINARY(16) NULL;
ALTER TABLE quotation ADD COLUMN branch_id BINARY(16) NULL;
ALTER TABLE customer_order ADD COLUMN branch_id BINARY(16) NULL;
ALTER TABLE invoice ADD COLUMN branch_id BINARY(16) NULL;
ALTER TABLE service_request ADD COLUMN branch_id BINARY(16) NULL;
ALTER TABLE installation_job ADD COLUMN branch_id BINARY(16) NULL;

ALTER TABLE enquiry ADD CONSTRAINT fk_enquiry_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id);
ALTER TABLE sales_lead ADD CONSTRAINT fk_sales_lead_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id);
ALTER TABLE site_survey ADD CONSTRAINT fk_site_survey_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id);
ALTER TABLE quotation ADD CONSTRAINT fk_quotation_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id);
ALTER TABLE customer_order ADD CONSTRAINT fk_customer_order_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id);
ALTER TABLE invoice ADD CONSTRAINT fk_invoice_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id);
ALTER TABLE service_request ADD CONSTRAINT fk_service_request_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id);
ALTER TABLE installation_job ADD CONSTRAINT fk_installation_job_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id);

CREATE INDEX ix_enquiry_tenant_branch ON enquiry(tenant_id,branch_id);
CREATE INDEX ix_sales_lead_tenant_branch ON sales_lead(tenant_id,branch_id);
CREATE INDEX ix_site_survey_tenant_branch ON site_survey(tenant_id,branch_id);
CREATE INDEX ix_quotation_tenant_branch ON quotation(tenant_id,branch_id);
CREATE INDEX ix_customer_order_tenant_branch ON customer_order(tenant_id,branch_id);
CREATE INDEX ix_invoice_tenant_branch ON invoice(tenant_id,branch_id);
CREATE INDEX ix_service_request_tenant_branch ON service_request(tenant_id,branch_id);
CREATE INDEX ix_installation_job_tenant_branch ON installation_job(tenant_id,branch_id);

-- SOURCE backend/src/main/resources/db/migration/V44__phase3_offline_storage_secrets_audit.sql
-- Phase 3 hardening foundations. Additive only; existing offline operations and audit rows are retained.
ALTER TABLE offline_sync_operation
    DROP CHECK chk_offline_sync_status,
    ADD CONSTRAINT chk_offline_sync_status
      CHECK (status IN ('APPLIED','DUPLICATE','CONFLICT','REJECTED','PENDING')),
    ADD COLUMN expected_version BIGINT NULL,
    ADD COLUMN resulting_version BIGINT NULL,
    ADD COLUMN conflict_code VARCHAR(64) NULL,
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN resolved_at TIMESTAMP(6) NULL,
    ADD COLUMN resolution_action VARCHAR(32) NULL;

ALTER TABLE audit_event
    ADD COLUMN branch_id BINARY(16) NULL,
    ADD COLUMN request_id VARCHAR(100) NULL,
    ADD COLUMN source_ip VARCHAR(64) NULL,
    ADD COLUMN user_agent VARCHAR(512) NULL,
    ADD INDEX ix_audit_tenant_branch_created (tenant_id, branch_id, created_at),
    ADD INDEX ix_audit_entity (tenant_id, entity_type, entity_id, created_at);

CREATE TABLE tenant_storage_ledger (
    id BINARY(16) PRIMARY KEY,
    tenant_id BINARY(16) NOT NULL,
    operation_id VARCHAR(120) NOT NULL,
    object_key VARCHAR(700) NOT NULL,
    object_category VARCHAR(40) NOT NULL,
    byte_delta BIGINT NOT NULL,
    checksum_sha256 CHAR(64) NULL,
    recorded_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_storage_ledger_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT uq_storage_ledger_operation UNIQUE (tenant_id, operation_id),
    INDEX ix_storage_ledger_tenant_time (tenant_id, recorded_at),
    INDEX ix_storage_ledger_object (tenant_id, object_key)
);

CREATE TABLE offline_sync_attachment (
    id BINARY(16) PRIMARY KEY,
    tenant_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    client_attachment_id BINARY(16) NOT NULL,
    operation_id BINARY(16) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    expected_size BIGINT NOT NULL,
    received_size BIGINT NOT NULL DEFAULT 0,
    checksum_sha256 CHAR(64) NOT NULL,
    object_key VARCHAR(700) NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_offline_attachment_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_offline_attachment_actor FOREIGN KEY (actor_id) REFERENCES app_user(id),
    CONSTRAINT uq_offline_attachment_client UNIQUE (tenant_id, actor_id, client_attachment_id),
    CONSTRAINT chk_offline_attachment_size CHECK (expected_size > 0 AND received_size >= 0 AND received_size <= expected_size),
    CONSTRAINT chk_offline_attachment_status CHECK (status IN ('PENDING','UPLOADING','COMPLETE','REJECTED','EXPIRED')),
    INDEX ix_offline_attachment_operation (tenant_id, operation_id, status)
);

CREATE TABLE integration_secret_reference (
    id BINARY(16) PRIMARY KEY,
    tenant_id BINARY(16) NOT NULL,
    integration_connection_id BINARY(16) NOT NULL,
    secret_provider VARCHAR(40) NOT NULL,
    secret_reference VARCHAR(700) NOT NULL,
    secret_version VARCHAR(120) NULL,
    status VARCHAR(20) NOT NULL,
    rotated_at TIMESTAMP(6) NULL,
    expires_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_secret_reference_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_secret_reference_connection FOREIGN KEY (integration_connection_id) REFERENCES integration_connection(id),
    CONSTRAINT uq_secret_reference_connection UNIQUE (tenant_id, integration_connection_id),
    CONSTRAINT chk_secret_reference_status CHECK (status IN ('ACTIVE','ROTATION_DUE','REVOKED','EXPIRED')),
    INDEX ix_secret_reference_expiry (tenant_id, status, expires_at)
);

-- SOURCE backend/src/main/resources/db/migration/V45__seed_default_subscription_plans.sql
-- Idempotent baseline subscription catalogue. Existing tenant assignments and plan history are untouched.
INSERT INTO subscription_plan
(id,plan_code,name,description,billing_cycle,price,currency,max_users,max_branches,max_warehouses,max_storage_mb,entitlements_json,active,created_at,updated_at)
SELECT UUID_TO_BIN(UUID()),src.plan_code,src.name,src.description,src.billing_cycle,src.price,'INR',
       src.max_users,src.max_branches,src.max_warehouses,src.max_storage_mb,src.entitlements_json,TRUE,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)
FROM (
 SELECT 'STARTER' plan_code,'Starter' name,'For small solar businesses starting digital operations.' description,
        'MONTHLY' billing_cycle,999.00 price,5 max_users,1 max_branches,1 max_warehouses,512 max_storage_mb,
        '{"branches":false,"multiWarehouse":false,"customBranding":false,"approvalWorkflows":false}' entitlements_json
 UNION ALL
 SELECT 'PROFESSIONAL','Professional','For growing solar businesses managing sales and field operations.',
        'MONTHLY',2999.00,25,3,3,5120,
        '{"branches":true,"multiWarehouse":true,"customBranding":true,"approvalWorkflows":true,"accountingIntegration":false}'
 UNION ALL
 SELECT 'ENTERPRISE','Enterprise','For multi-location solar businesses with advanced operational needs.',
        'MONTHLY',9999.00,100,20,20,51200,
        '{"branches":true,"multiWarehouse":true,"customBranding":true,"customDomains":true,"approvalWorkflows":true,"accountingIntegration":true,"paymentGateway":true,"apiIntegrations":true}'
) src
LEFT JOIN subscription_plan existing ON existing.plan_code=src.plan_code
WHERE existing.id IS NULL;


-- SOURCE backend/src/main/resources/db/migration/V46__subscription_management_and_assignment_history.sql
-- Expand existing subscription records without replacing their identities or history.
ALTER TABLE subscription_plan
 ADD COLUMN max_products INT NOT NULL DEFAULT 100,
 ADD COLUMN max_customers INT NOT NULL DEFAULT 100,
 ADD COLUMN trial_period_days INT NOT NULL DEFAULT 14,
 ADD COLUMN display_order INT NOT NULL DEFAULT 100,
 ADD CONSTRAINT chk_subscription_plan_extended_limits CHECK(max_products>=1 AND max_customers>=1 AND trial_period_days>=0);
ALTER TABLE tenant_subscription
 ADD COLUMN billing_cycle VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
 ADD COLUMN trial_ends_at TIMESTAMP(6) NULL,
 ADD COLUMN auto_renew BOOLEAN NOT NULL DEFAULT TRUE,
 ADD COLUMN notes VARCHAR(2000) NULL,
 ADD COLUMN replaced_by_subscription_id BINARY(16) NULL,
 ADD CONSTRAINT fk_subscription_replaced_by FOREIGN KEY(replaced_by_subscription_id) REFERENCES tenant_subscription(id),
 ADD CONSTRAINT chk_subscription_billing_cycle CHECK(billing_cycle IN ('MONTHLY','YEARLY'));
UPDATE tenant_subscription ts JOIN subscription_plan p ON p.id=ts.plan_id SET ts.billing_cycle=p.billing_cycle;
CREATE TABLE subscription_assignment_history (
 id BINARY(16) PRIMARY KEY,
 tenant_id BINARY(16) NOT NULL,
 subscription_id BINARY(16) NOT NULL,
 actor_id BINARY(16) NULL,
 action VARCHAR(40) NOT NULL,
 previous_status VARCHAR(20) NULL,
 new_status VARCHAR(20) NULL,
 previous_plan_id BINARY(16) NULL,
 new_plan_id BINARY(16) NULL,
 details_json LONGTEXT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_sub_history_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
 CONSTRAINT fk_sub_history_subscription FOREIGN KEY(subscription_id) REFERENCES tenant_subscription(id),
 CONSTRAINT fk_sub_history_actor FOREIGN KEY(actor_id) REFERENCES app_user(id),
 INDEX ix_sub_history_tenant_time(tenant_id,created_at),
 INDEX ix_sub_history_subscription_time(subscription_id,created_at)
);
INSERT INTO subscription_assignment_history(id,tenant_id,subscription_id,actor_id,action,previous_status,new_status,previous_plan_id,new_plan_id,details_json,created_at)
SELECT UUID_TO_BIN(UUID()),tenant_id,id,created_by,'MIGRATED',NULL,status,NULL,plan_id,
       JSON_OBJECT('source','V46 migration','externalReference',external_reference),created_at
FROM tenant_subscription;
UPDATE subscription_plan SET
 max_products=CASE plan_code WHEN 'STARTER' THEN 100 WHEN 'PROFESSIONAL' THEN 1000 WHEN 'ENTERPRISE' THEN 10000 ELSE max_products END,
 max_customers=CASE plan_code WHEN 'STARTER' THEN 100 WHEN 'PROFESSIONAL' THEN 1000 WHEN 'ENTERPRISE' THEN 10000 ELSE max_customers END,
 trial_period_days=CASE plan_code WHEN 'STARTER' THEN 14 WHEN 'PROFESSIONAL' THEN 14 WHEN 'ENTERPRISE' THEN 30 ELSE trial_period_days END,
 display_order=CASE plan_code WHEN 'STARTER' THEN 1 WHEN 'PROFESSIONAL' THEN 2 WHEN 'ENTERPRISE' THEN 3 ELSE display_order END;

-- SOURCE backend/src/main/resources/db/migration/V47__starter_plan_warehouse_allowance.sql
-- Starter includes a small multi-warehouse allowance so existing small-vendor operations remain usable.
-- Only normalize the original seeded default; do not overwrite plans already customized by an administrator.
UPDATE subscription_plan
SET max_warehouses=3,entitlements_json=JSON_SET(entitlements_json,'$.multiWarehouse',TRUE),updated_at=CURRENT_TIMESTAMP(6)
WHERE plan_code='STARTER' AND max_warehouses=1;

-- SOURCE backend/src/main/resources/db/migration/V48__subscription_assignment_history_immutable.sql
-- Subscription assignment history is immutable; corrections are recorded as new events.
CREATE TRIGGER trg_subscription_assignment_history_no_update
BEFORE UPDATE ON subscription_assignment_history
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='subscription_assignment_history is append-only';
CREATE TRIGGER trg_subscription_assignment_history_no_delete
BEFORE DELETE ON subscription_assignment_history
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='subscription_assignment_history is append-only';

-- SOURCE backend/src/main/resources/db/migration/V49__customer_addresses.sql
-- Customer-owned saved addresses. Records are isolated by both tenant and customer profile.
ALTER TABLE customer_profile ADD UNIQUE KEY uq_customer_profile_tenant_id (tenant_id,id);
CREATE TABLE customer_address (
 id BINARY(16) PRIMARY KEY,
 tenant_id BINARY(16) NOT NULL,
 customer_id BINARY(16) NOT NULL,
 label VARCHAR(80) NOT NULL,
 line1 VARCHAR(180) NOT NULL,
 line2 VARCHAR(180),
 city VARCHAR(100) NOT NULL,
 state VARCHAR(100) NOT NULL,
 pincode VARCHAR(12) NOT NULL,
 country VARCHAR(80) NOT NULL DEFAULT 'India',
 landmark VARCHAR(120),
 is_default BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMP(6) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_customer_address_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
 CONSTRAINT fk_customer_address_profile FOREIGN KEY (tenant_id,customer_id) REFERENCES customer_profile(tenant_id,id),
 UNIQUE KEY uq_customer_address_tenant_id (tenant_id,id),
 INDEX ix_customer_address_owner_default (tenant_id,customer_id,is_default,created_at)
);

-- SOURCE backend/src/main/resources/db/migration/V50__enquiry_saved_address.sql
-- Optional customer-selected saved address snapshot for enquiries.
ALTER TABLE enquiry
 ADD COLUMN address_id BINARY(16) NULL,
 ADD COLUMN site_address VARCHAR(500) NULL,
 ADD COLUMN city_pincode VARCHAR(160) NULL,
 ADD CONSTRAINT fk_enquiry_customer_address
   FOREIGN KEY (tenant_id,address_id) REFERENCES customer_address(tenant_id,id),
 ADD INDEX ix_enquiry_tenant_address (tenant_id,address_id);

-- SOURCE backend/src/main/resources/db/migration/V51__repair_vendor_admin_analytics_permissions.sql
-- Repair vendor administrator analytics access for existing tenants.
-- The API continues to enforce both permissions; this migration ensures every
-- tenant-scoped VENDOR_ADMIN role has the required grants.
INSERT INTO permission (id, code)
SELECT UUID_TO_BIN(UUID()), src.code
FROM (
    SELECT 'REPORT_VIEW' AS code
    UNION ALL
    SELECT 'BRANCH_MANAGE' AS code
) src
LEFT JOIN permission existing ON existing.code = src.code
WHERE existing.id IS NULL;

INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN ('REPORT_VIEW', 'BRANCH_MANAGE')
WHERE r.tenant_id IS NOT NULL
  AND r.name = 'VENDOR_ADMIN';

-- SOURCE backend/src/main/resources/db/migration/V52__authentication_lockout_and_rate_limit.sql
ALTER TABLE app_user
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMP(6) NULL,
    ADD COLUMN last_login_at TIMESTAMP(6) NULL;

CREATE INDEX ix_app_user_locked_until ON app_user(locked_until);

CREATE TABLE auth_rate_limit_bucket (
    bucket_key CHAR(64) NOT NULL PRIMARY KEY,
    window_started_at TIMESTAMP(6) NOT NULL,
    request_count INT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    INDEX ix_auth_rate_limit_updated (updated_at)
);

-- SOURCE backend/src/main/resources/db/migration/V53__refresh_token_families_and_srs_permissions.sql
-- Authentication hardening: refresh token families and the SRS permission catalogue.
ALTER TABLE refresh_token
    ADD COLUMN family_id BINARY(16) NULL,
    ADD COLUMN replaced_by_token_id BINARY(16) NULL,
    ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

-- Existing refresh tokens predate family tracking. Isolate each legacy token as a
-- one-token family; new rotations preserve their family identifier.
UPDATE refresh_token SET family_id = UUID_TO_BIN(UUID()) WHERE family_id IS NULL;
ALTER TABLE refresh_token MODIFY family_id BINARY(16) NOT NULL;
CREATE INDEX ix_refresh_token_family ON refresh_token(family_id);
CREATE INDEX ix_refresh_token_user_family ON refresh_token(user_id, family_id);
ALTER TABLE refresh_token
    ADD CONSTRAINT fk_refresh_token_replacement
    FOREIGN KEY (replaced_by_token_id) REFERENCES refresh_token(id);

INSERT INTO permission(id, code)
SELECT UUID_TO_BIN(UUID()), src.code
FROM (
    SELECT 'TENANT_VIEW' code UNION ALL SELECT 'TENANT_UPDATE'
    UNION ALL SELECT 'USER_CREATE' UNION ALL SELECT 'USER_UPDATE' UNION ALL SELECT 'USER_DISABLE'
    UNION ALL SELECT 'ROLE_VIEW' UNION ALL SELECT 'ROLE_CREATE' UNION ALL SELECT 'ROLE_UPDATE' UNION ALL SELECT 'ROLE_ASSIGN'
    UNION ALL SELECT 'CUSTOMER_VIEW' UNION ALL SELECT 'CUSTOMER_CREATE' UNION ALL SELECT 'CUSTOMER_UPDATE' UNION ALL SELECT 'CUSTOMER_EXPORT'
    UNION ALL SELECT 'PRODUCT_VIEW' UNION ALL SELECT 'PRODUCT_CREATE' UNION ALL SELECT 'PRODUCT_UPDATE' UNION ALL SELECT 'PRODUCT_ARCHIVE'
    UNION ALL SELECT 'PACKAGE_VIEW' UNION ALL SELECT 'PACKAGE_CREATE' UNION ALL SELECT 'PACKAGE_UPDATE' UNION ALL SELECT 'PACKAGE_PUBLISH'
    UNION ALL SELECT 'LEAD_VIEW' UNION ALL SELECT 'LEAD_CREATE' UNION ALL SELECT 'LEAD_UPDATE' UNION ALL SELECT 'LEAD_ASSIGN' UNION ALL SELECT 'LEAD_EXPORT'
    UNION ALL SELECT 'SURVEY_VIEW' UNION ALL SELECT 'SURVEY_CREATE' UNION ALL SELECT 'SURVEY_ASSIGN' UNION ALL SELECT 'SURVEY_COMPLETE'
    UNION ALL SELECT 'QUOTATION_VIEW' UNION ALL SELECT 'QUOTATION_CREATE' UNION ALL SELECT 'QUOTATION_UPDATE' UNION ALL SELECT 'QUOTATION_SEND' UNION ALL SELECT 'QUOTATION_APPROVE_DISCOUNT' UNION ALL SELECT 'QUOTATION_CONVERT'
    UNION ALL SELECT 'ORDER_VIEW' UNION ALL SELECT 'ORDER_UPDATE' UNION ALL SELECT 'ORDER_CANCEL' UNION ALL SELECT 'ORDER_ASSIGN'
    UNION ALL SELECT 'INVENTORY_VIEW' UNION ALL SELECT 'INVENTORY_RECEIVE' UNION ALL SELECT 'INVENTORY_RESERVE' UNION ALL SELECT 'INVENTORY_TRANSFER' UNION ALL SELECT 'INVENTORY_ADJUST' UNION ALL SELECT 'INVENTORY_EXPORT'
    UNION ALL SELECT 'INSTALLATION_VIEW' UNION ALL SELECT 'INSTALLATION_SCHEDULE' UNION ALL SELECT 'INSTALLATION_ASSIGN' UNION ALL SELECT 'INSTALLATION_UPDATE' UNION ALL SELECT 'INSTALLATION_COMMISSION'
    UNION ALL SELECT 'INVOICE_VIEW' UNION ALL SELECT 'INVOICE_CREATE' UNION ALL SELECT 'INVOICE_SEND' UNION ALL SELECT 'INVOICE_VOID'
    UNION ALL SELECT 'PAYMENT_VIEW' UNION ALL SELECT 'PAYMENT_RECORD' UNION ALL SELECT 'PAYMENT_REFUND'
    UNION ALL SELECT 'SERVICE_VIEW' UNION ALL SELECT 'SERVICE_CREATE' UNION ALL SELECT 'SERVICE_ASSIGN' UNION ALL SELECT 'SERVICE_UPDATE' UNION ALL SELECT 'SERVICE_RESOLVE' UNION ALL SELECT 'SERVICE_CLOSE'
    UNION ALL SELECT 'REPORT_EXPORT' UNION ALL SELECT 'CMS_VIEW' UNION ALL SELECT 'CMS_CREATE' UNION ALL SELECT 'CMS_UPDATE' UNION ALL SELECT 'CMS_PUBLISH'
) src
LEFT JOIN permission existing ON existing.code = src.code
WHERE existing.id IS NULL;

INSERT INTO role(id, tenant_id, name)
SELECT UUID_TO_BIN(UUID()), t.id, 'CONTENT_MANAGER'
FROM tenant t
LEFT JOIN role r ON r.tenant_id=t.id AND r.name='CONTENT_MANAGER'
WHERE r.id IS NULL;

INSERT INTO role(id, tenant_id, name)
SELECT UUID_TO_BIN(UUID()), NULL, 'PLATFORM_SUPPORT'
WHERE NOT EXISTS (SELECT 1 FROM role WHERE tenant_id IS NULL AND name='PLATFORM_SUPPORT');

-- Platform Super Admin receives the full platform catalogue and is never tenant scoped.
INSERT IGNORE INTO role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p
WHERE r.tenant_id IS NULL AND r.name='PLATFORM_SUPER_ADMIN'
  AND p.code IN ('PLATFORM_MANAGE','SUBSCRIPTION_MANAGE','AUDIT_VIEW','TENANT_VIEW','TENANT_UPDATE');

-- Vendor Admin receives all vendor-level business permissions, never platform permissions.
INSERT IGNORE INTO role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p
WHERE r.tenant_id IS NOT NULL AND r.name='VENDOR_ADMIN'
  AND p.code NOT IN ('PLATFORM_MANAGE','SUBSCRIPTION_MANAGE','TENANT_UPDATE');

-- Fine-grained employee grants. Existing coarse grants remain for backward compatibility;
-- endpoint-by-endpoint enforcement migration is separately required before removing them.
INSERT IGNORE INTO role_permission(role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p
WHERE r.tenant_id IS NOT NULL AND (
 (r.name='SALES_EXECUTIVE' AND p.code IN ('PRODUCT_VIEW','PACKAGE_VIEW','CUSTOMER_VIEW','CUSTOMER_CREATE','CUSTOMER_UPDATE','LEAD_VIEW','LEAD_CREATE','LEAD_UPDATE','SURVEY_VIEW','SURVEY_CREATE','SURVEY_COMPLETE','QUOTATION_VIEW','QUOTATION_CREATE','QUOTATION_UPDATE','QUOTATION_SEND','QUOTATION_CONVERT','ORDER_VIEW','ORDER_CREATE')) OR
 (r.name='SALES_MANAGER' AND p.code IN ('PRODUCT_VIEW','PACKAGE_VIEW','CUSTOMER_VIEW','CUSTOMER_CREATE','CUSTOMER_UPDATE','CUSTOMER_EXPORT','LEAD_VIEW','LEAD_CREATE','LEAD_UPDATE','LEAD_ASSIGN','LEAD_EXPORT','SURVEY_VIEW','SURVEY_CREATE','SURVEY_ASSIGN','SURVEY_COMPLETE','QUOTATION_VIEW','QUOTATION_CREATE','QUOTATION_UPDATE','QUOTATION_SEND','QUOTATION_APPROVE_DISCOUNT','QUOTATION_CONVERT','ORDER_VIEW','ORDER_CREATE','ORDER_UPDATE','ORDER_ASSIGN','REPORT_VIEW','REPORT_EXPORT')) OR
 (r.name='INVENTORY_MANAGER' AND p.code IN ('PRODUCT_VIEW','PRODUCT_CREATE','PRODUCT_UPDATE','PACKAGE_VIEW','INVENTORY_VIEW','INVENTORY_RECEIVE','INVENTORY_RESERVE','INVENTORY_TRANSFER','INVENTORY_ADJUST','INVENTORY_EXPORT','ORDER_VIEW')) OR
 (r.name IN ('SERVICE_ENGINEER','TECHNICIAN','INSTALLER') AND p.code IN ('SURVEY_VIEW','SURVEY_COMPLETE','INSTALLATION_VIEW','INSTALLATION_UPDATE','INSTALLATION_COMMISSION','SERVICE_VIEW','SERVICE_UPDATE','SERVICE_RESOLVE')) OR
 (r.name='SERVICE_MANAGER' AND p.code IN ('SERVICE_VIEW','SERVICE_CREATE','SERVICE_ASSIGN','SERVICE_UPDATE','SERVICE_RESOLVE','SERVICE_CLOSE','INSTALLATION_VIEW','INSTALLATION_ASSIGN','REPORT_VIEW')) OR
 (r.name='OPERATIONS_MANAGER' AND p.code IN ('SURVEY_VIEW','SURVEY_CREATE','SURVEY_ASSIGN','SURVEY_COMPLETE','INSTALLATION_VIEW','INSTALLATION_SCHEDULE','INSTALLATION_ASSIGN','INSTALLATION_UPDATE','INSTALLATION_COMMISSION','ORDER_VIEW','ORDER_UPDATE','INVENTORY_VIEW','INVENTORY_RESERVE','REPORT_VIEW')) OR
 (r.name='ACCOUNTANT' AND p.code IN ('INVOICE_VIEW','INVOICE_CREATE','INVOICE_SEND','PAYMENT_VIEW','PAYMENT_RECORD','PAYMENT_REFUND','REPORT_VIEW','REPORT_EXPORT')) OR
 (r.name='CONTENT_MANAGER' AND p.code IN ('CMS_VIEW','CMS_CREATE','CMS_UPDATE','CMS_PUBLISH','PRODUCT_VIEW','PACKAGE_VIEW'))
);

-- SOURCE backend/src/main/resources/db/migration/V54__reconcile_employee_role_permissions.sql
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

-- SOURCE backend/src/main/resources/db/migration/V55__strict_permission_reconciliation_and_platform_support.sql
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

-- SOURCE backend/src/main/resources/db/migration/V56__remove_platform_permissions_from_tenant_roles.sql
-- Platform-only permissions must never be granted to tenant-owned roles.
DELETE rp
FROM role_permission rp
JOIN role r ON r.id = rp.role_id
JOIN permission p ON p.id = rp.permission_id
WHERE r.tenant_id IS NOT NULL
  AND p.code IN ('PLATFORM_MANAGE','SUBSCRIPTION_MANAGE','TENANT_VIEW','TENANT_UPDATE');

-- SOURCE backend/src/main/resources/db/migration/V57__tenant_aware_catalogue_foreign_keys.sql
-- Phase 2: enforce tenant ownership for core catalogue relationships.
-- Preflight is deliberately fail-closed: do not add constraints over dirty legacy data.
DROP PROCEDURE IF EXISTS assert_phase2_catalogue_tenant_integrity;
DELIMITER $
CREATE PROCEDURE assert_phase2_catalogue_tenant_integrity()
BEGIN
    IF EXISTS (
        SELECT 1 FROM product p
        LEFT JOIN product_category c ON c.id=p.category_id AND c.tenant_id=p.tenant_id
        WHERE p.category_id IS NOT NULL AND c.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V57 blocked: product.category_id references a missing or cross-tenant category';
    END IF;

    IF EXISTS (
        SELECT 1 FROM package_item i
        LEFT JOIN solar_package p ON p.id=i.package_id AND p.tenant_id=i.tenant_id
        WHERE i.package_id IS NOT NULL AND (i.tenant_id IS NULL OR p.id IS NULL)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V57 blocked: package_item.package_id is orphaned or cross-tenant';
    END IF;

    IF EXISTS (
        SELECT 1 FROM package_item i
        LEFT JOIN product p ON p.id=i.product_id AND p.tenant_id=i.tenant_id
        WHERE i.product_id IS NOT NULL AND (i.tenant_id IS NULL OR p.id IS NULL)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V57 blocked: package_item.product_id is orphaned or cross-tenant';
    END IF;

    IF EXISTS (SELECT 1 FROM package_item WHERE tenant_id IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V57 blocked: package_item contains rows without tenant_id';
    END IF;
END$
DELIMITER ;
CALL assert_phase2_catalogue_tenant_integrity();
DROP PROCEDURE assert_phase2_catalogue_tenant_integrity;

-- Composite referenced keys are required by MySQL for tenant-aware foreign keys.
ALTER TABLE product_category
    ADD CONSTRAINT uq_product_category_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE product
    ADD CONSTRAINT uq_product_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE solar_package
    ADD CONSTRAINT uq_solar_package_tenant_id UNIQUE (tenant_id,id);

ALTER TABLE product
    ADD CONSTRAINT fk_product_category_tenant
    FOREIGN KEY (tenant_id,category_id) REFERENCES product_category(tenant_id,id);

ALTER TABLE package_item
    MODIFY tenant_id BINARY(16) NOT NULL,
    ADD CONSTRAINT fk_package_item_package_tenant
        FOREIGN KEY (tenant_id,package_id) REFERENCES solar_package(tenant_id,id),
    ADD CONSTRAINT fk_package_item_product_tenant
        FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id);

-- Rollback (manual, forward-only Flyway deployment):
-- ALTER TABLE package_item DROP FOREIGN KEY fk_package_item_product_tenant,
--     DROP FOREIGN KEY fk_package_item_package_tenant;
-- ALTER TABLE product DROP FOREIGN KEY fk_product_category_tenant;
-- ALTER TABLE solar_package DROP INDEX uq_solar_package_tenant_id;
-- ALTER TABLE product DROP INDEX uq_product_tenant_id;
-- ALTER TABLE product_category DROP INDEX uq_product_category_tenant_id;
-- ALTER TABLE package_item MODIFY tenant_id BINARY(16) NULL;

-- SOURCE backend/src/main/resources/db/migration/V58__operational_tenant_composite_foreign_keys.sql
-- Phase 2: enforce tenant-aware relationships for operational, financial and evidence records.
-- This migration is deliberately fail-closed. Repair any reported rows before retrying.
DROP PROCEDURE IF EXISTS assert_phase2_operational_tenant_integrity;
DELIMITER $
CREATE PROCEDURE assert_phase2_operational_tenant_integrity()
BEGIN
    -- Legacy tenant columns were nullable in the original Release 2 DDL.
    IF EXISTS (SELECT 1 FROM customer_profile c LEFT JOIN app_user u ON u.tenant_id=c.tenant_id AND u.id=c.user_id WHERE c.user_id IS NOT NULL AND u.id IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V58 blocked: customer profile user is missing or belongs to another tenant';
    END IF;

    IF EXISTS (SELECT 1 FROM lead_history WHERE tenant_id IS NULL)
       OR EXISTS (SELECT 1 FROM site_survey WHERE tenant_id IS NULL)
       OR EXISTS (SELECT 1 FROM quotation WHERE tenant_id IS NULL)
       OR EXISTS (SELECT 1 FROM quotation_item WHERE tenant_id IS NULL)
       OR EXISTS (SELECT 1 FROM customer_order WHERE tenant_id IS NULL)
       OR EXISTS (SELECT 1 FROM order_item WHERE tenant_id IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V58 blocked: legacy tenant-owned sales rows have NULL tenant_id';
    END IF;

    -- Sales, survey and quotation graph.
    IF EXISTS (SELECT 1 FROM sales_lead c LEFT JOIN enquiry p ON p.tenant_id=c.tenant_id AND p.id=c.enquiry_id WHERE c.enquiry_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM sales_lead c LEFT JOIN customer_profile p ON p.tenant_id=c.tenant_id AND p.id=c.customer_id WHERE c.customer_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM sales_lead c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.assigned_user_id WHERE c.assigned_user_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM lead_history c LEFT JOIN sales_lead p ON p.tenant_id=c.tenant_id AND p.id=c.lead_id WHERE c.lead_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM lead_history c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.actor_id WHERE c.actor_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM site_survey c LEFT JOIN sales_lead p ON p.tenant_id=c.tenant_id AND p.id=c.lead_id WHERE c.lead_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM site_survey c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.assigned_user_id WHERE c.assigned_user_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM site_survey c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.reviewed_by WHERE c.reviewed_by IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM quotation c LEFT JOIN customer_profile p ON p.tenant_id=c.tenant_id AND p.id=c.customer_id WHERE c.customer_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM quotation c LEFT JOIN sales_lead p ON p.tenant_id=c.tenant_id AND p.id=c.lead_id WHERE c.lead_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM quotation c LEFT JOIN site_survey p ON p.tenant_id=c.tenant_id AND p.id=c.survey_id WHERE c.survey_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM quotation c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.created_by WHERE c.created_by IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM quotation c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.decision_by WHERE c.decision_by IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM quotation_item c LEFT JOIN quotation p ON p.tenant_id=c.tenant_id AND p.id=c.quotation_id WHERE c.quotation_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM quotation_item c LEFT JOIN product p ON p.tenant_id=c.tenant_id AND p.id=c.product_id WHERE c.product_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM quotation_item c LEFT JOIN solar_package p ON p.tenant_id=c.tenant_id AND p.id=c.package_id WHERE c.package_id IS NOT NULL AND p.id IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V58 blocked: sales, survey or quotation relationship is orphaned or cross-tenant';
    END IF;

    -- Orders, inventory, installation and warranty graph.
    IF EXISTS (SELECT 1 FROM customer_order c LEFT JOIN quotation p ON p.tenant_id=c.tenant_id AND p.id=c.quotation_id WHERE c.quotation_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM customer_order c LEFT JOIN customer_profile p ON p.tenant_id=c.tenant_id AND p.id=c.customer_id WHERE c.customer_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM order_item c LEFT JOIN customer_order p ON p.tenant_id=c.tenant_id AND p.id=c.order_id WHERE c.order_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM order_item c LEFT JOIN product p ON p.tenant_id=c.tenant_id AND p.id=c.product_id WHERE c.product_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM order_item c LEFT JOIN solar_package p ON p.tenant_id=c.tenant_id AND p.id=c.package_id WHERE c.package_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM order_status_history c LEFT JOIN customer_order p ON p.tenant_id=c.tenant_id AND p.id=c.order_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM order_status_history c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.actor_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM inventory_balance c LEFT JOIN warehouse p ON p.tenant_id=c.tenant_id AND p.id=c.warehouse_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM inventory_balance c LEFT JOIN product p ON p.tenant_id=c.tenant_id AND p.id=c.product_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM inventory_transaction c LEFT JOIN warehouse p ON p.tenant_id=c.tenant_id AND p.id=c.warehouse_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM inventory_transaction c LEFT JOIN product p ON p.tenant_id=c.tenant_id AND p.id=c.product_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM inventory_transaction c LEFT JOIN customer_order p ON p.tenant_id=c.tenant_id AND p.id=c.order_id WHERE c.order_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM inventory_transaction c LEFT JOIN installation_job p ON p.tenant_id=c.tenant_id AND p.id=c.installation_id WHERE c.installation_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM inventory_transaction c LEFT JOIN service_request p ON p.tenant_id=c.tenant_id AND p.id=c.service_request_id WHERE c.service_request_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM inventory_transaction c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.actor_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM inventory_reservation c LEFT JOIN customer_order p ON p.tenant_id=c.tenant_id AND p.id=c.order_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM inventory_reservation c LEFT JOIN warehouse p ON p.tenant_id=c.tenant_id AND p.id=c.warehouse_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM inventory_reservation c LEFT JOIN product p ON p.tenant_id=c.tenant_id AND p.id=c.product_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM installation_job c LEFT JOIN customer_order p ON p.tenant_id=c.tenant_id AND p.id=c.order_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM installation_job c LEFT JOIN customer_profile p ON p.tenant_id=c.tenant_id AND p.id=c.customer_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM installation_job c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.assigned_engineer_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM installation_job c LEFT JOIN installation_team p ON p.tenant_id=c.tenant_id AND p.id=c.team_id WHERE c.team_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM installation_checklist_item c LEFT JOIN installation_job p ON p.tenant_id=c.tenant_id AND p.id=c.installation_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM installation_checklist_item c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.completed_by WHERE c.completed_by IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM warranty c LEFT JOIN customer_order p ON p.tenant_id=c.tenant_id AND p.id=c.order_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM warranty c LEFT JOIN installation_job p ON p.tenant_id=c.tenant_id AND p.id=c.installation_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM warranty c LEFT JOIN customer_profile p ON p.tenant_id=c.tenant_id AND p.id=c.customer_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM warranty c LEFT JOIN product p ON p.tenant_id=c.tenant_id AND p.id=c.product_id WHERE p.id IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V58 blocked: order, inventory, installation or warranty relationship is orphaned or cross-tenant';
    END IF;

    -- Finance, service and private evidence graph.
    IF EXISTS (SELECT 1 FROM invoice c LEFT JOIN customer_order p ON p.tenant_id=c.tenant_id AND p.id=c.order_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM invoice c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.created_by WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM invoice_item c LEFT JOIN invoice p ON p.tenant_id=c.tenant_id AND p.id=c.invoice_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM invoice_item c LEFT JOIN product p ON p.tenant_id=c.tenant_id AND p.id=c.product_id WHERE c.product_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM payment c LEFT JOIN invoice p ON p.tenant_id=c.tenant_id AND p.id=c.invoice_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM payment c LEFT JOIN customer_order p ON p.tenant_id=c.tenant_id AND p.id=c.order_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM payment c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.recorded_by WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM payment_refund c LEFT JOIN payment p ON p.tenant_id=c.tenant_id AND p.id=c.payment_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM payment_refund c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.recorded_by WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM credit_note c LEFT JOIN invoice p ON p.tenant_id=c.tenant_id AND p.id=c.invoice_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM credit_note c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.created_by WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_request c LEFT JOIN customer_profile p ON p.tenant_id=c.tenant_id AND p.id=c.customer_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_request c LEFT JOIN installation_job p ON p.tenant_id=c.tenant_id AND p.id=c.installation_id WHERE c.installation_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_request c LEFT JOIN product p ON p.tenant_id=c.tenant_id AND p.id=c.product_id WHERE c.product_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_request c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.assigned_engineer_id WHERE c.assigned_engineer_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_visit c LEFT JOIN service_request p ON p.tenant_id=c.tenant_id AND p.id=c.service_request_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_visit c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.engineer_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_event c LEFT JOIN service_request p ON p.tenant_id=c.tenant_id AND p.id=c.service_request_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_event c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.actor_id WHERE c.actor_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_attachment c LEFT JOIN service_request p ON p.tenant_id=c.tenant_id AND p.id=c.service_request_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_attachment c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.uploaded_by WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_comment c LEFT JOIN service_request p ON p.tenant_id=c.tenant_id AND p.id=c.service_request_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_comment c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.author_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM site_survey_history c LEFT JOIN site_survey p ON p.tenant_id=c.tenant_id AND p.id=c.survey_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM site_survey_history c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.actor_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM site_survey_attachment c LEFT JOIN site_survey p ON p.tenant_id=c.tenant_id AND p.id=c.survey_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM site_survey_attachment c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.uploaded_by WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM warranty_claim c LEFT JOIN warranty p ON p.tenant_id=c.tenant_id AND p.id=c.warranty_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM warranty_claim c LEFT JOIN customer_profile p ON p.tenant_id=c.tenant_id AND p.id=c.customer_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM warranty_claim c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.reviewed_by WHERE c.reviewed_by IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM warranty_claim_attachment c LEFT JOIN warranty_claim p ON p.tenant_id=c.tenant_id AND p.id=c.claim_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM warranty_claim_attachment c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.uploaded_by WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM installation_schedule_history c LEFT JOIN installation_job p ON p.tenant_id=c.tenant_id AND p.id=c.installation_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM installation_schedule_history c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.actor_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM offline_sync_operation c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.actor_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM offline_sync_attachment c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.actor_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM offline_sync_attachment c LEFT JOIN offline_sync_operation p ON p.tenant_id=c.tenant_id AND p.id=c.operation_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM integration_secret_reference c LEFT JOIN integration_connection p ON p.tenant_id=c.tenant_id AND p.id=c.integration_connection_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM branch_user c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.user_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM engineer_profile c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.user_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM approval_instance c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.requested_by WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM approval_decision c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.actor_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM audit_event c LEFT JOIN branch p ON p.tenant_id=c.tenant_id AND p.id=c.branch_id WHERE c.branch_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM notification_delivery c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.user_id WHERE c.tenant_id IS NOT NULL AND c.user_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM notification_delivery c JOIN app_user u ON u.id=c.user_id WHERE c.tenant_id IS NULL AND u.tenant_id IS NOT NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V58 blocked: finance, service, evidence, identity or integration relationship is orphaned or cross-tenant';
    END IF;
END$
DELIMITER ;
CALL assert_phase2_operational_tenant_integrity();
DROP PROCEDURE assert_phase2_operational_tenant_integrity;

-- Add composite referenced keys. Existing global primary keys remain unchanged.
ALTER TABLE app_user ADD CONSTRAINT uq_app_user_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE enquiry ADD CONSTRAINT uq_enquiry_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE sales_lead ADD CONSTRAINT uq_sales_lead_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE site_survey ADD CONSTRAINT uq_site_survey_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE quotation ADD CONSTRAINT uq_quotation_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE customer_order ADD CONSTRAINT uq_customer_order_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE warehouse ADD CONSTRAINT uq_warehouse_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE installation_team ADD CONSTRAINT uq_installation_team_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE installation_job ADD CONSTRAINT uq_installation_job_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE invoice ADD CONSTRAINT uq_invoice_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE payment ADD CONSTRAINT uq_payment_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE service_request ADD CONSTRAINT uq_service_request_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE warranty ADD CONSTRAINT uq_warranty_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE warranty_claim ADD CONSTRAINT uq_warranty_claim_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE offline_sync_operation ADD CONSTRAINT uq_offline_sync_operation_tenant_id UNIQUE (tenant_id,id);

-- Sales, survey and quotation graph.
ALTER TABLE sales_lead
    ADD CONSTRAINT fk_sales_lead_enquiry_tenant FOREIGN KEY (tenant_id,enquiry_id) REFERENCES enquiry(tenant_id,id),
    ADD CONSTRAINT fk_sales_lead_customer_tenant FOREIGN KEY (tenant_id,customer_id) REFERENCES customer_profile(tenant_id,id),
    ADD CONSTRAINT fk_sales_lead_assignee_tenant FOREIGN KEY (tenant_id,assigned_user_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE lead_history
    ADD CONSTRAINT fk_lead_history_lead_tenant FOREIGN KEY (tenant_id,lead_id) REFERENCES sales_lead(tenant_id,id),
    ADD CONSTRAINT fk_lead_history_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE site_survey
    ADD CONSTRAINT fk_site_survey_lead_tenant FOREIGN KEY (tenant_id,lead_id) REFERENCES sales_lead(tenant_id,id),
    ADD CONSTRAINT fk_site_survey_assignee_tenant FOREIGN KEY (tenant_id,assigned_user_id) REFERENCES app_user(tenant_id,id),
    ADD CONSTRAINT fk_site_survey_reviewer_tenant FOREIGN KEY (tenant_id,reviewed_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE quotation
    ADD CONSTRAINT fk_quotation_customer_tenant FOREIGN KEY (tenant_id,customer_id) REFERENCES customer_profile(tenant_id,id),
    ADD CONSTRAINT fk_quotation_lead_tenant FOREIGN KEY (tenant_id,lead_id) REFERENCES sales_lead(tenant_id,id),
    ADD CONSTRAINT fk_quotation_survey_tenant FOREIGN KEY (tenant_id,survey_id) REFERENCES site_survey(tenant_id,id),
    ADD CONSTRAINT fk_quotation_created_by_tenant FOREIGN KEY (tenant_id,created_by) REFERENCES app_user(tenant_id,id),
    ADD CONSTRAINT fk_quotation_decision_by_tenant FOREIGN KEY (tenant_id,decision_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE quotation_item
    ADD CONSTRAINT fk_quotation_item_quote_tenant FOREIGN KEY (tenant_id,quotation_id) REFERENCES quotation(tenant_id,id),
    ADD CONSTRAINT fk_quotation_item_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id),
    ADD CONSTRAINT fk_quotation_item_package_tenant FOREIGN KEY (tenant_id,package_id) REFERENCES solar_package(tenant_id,id);

-- Orders, inventory, installation and warranty graph.
ALTER TABLE customer_order
    ADD CONSTRAINT fk_customer_order_quote_tenant FOREIGN KEY (tenant_id,quotation_id) REFERENCES quotation(tenant_id,id),
    ADD CONSTRAINT fk_customer_order_customer_tenant FOREIGN KEY (tenant_id,customer_id) REFERENCES customer_profile(tenant_id,id);
ALTER TABLE order_item
    ADD CONSTRAINT fk_order_item_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
    ADD CONSTRAINT fk_order_item_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id),
    ADD CONSTRAINT fk_order_item_package_tenant FOREIGN KEY (tenant_id,package_id) REFERENCES solar_package(tenant_id,id);
ALTER TABLE order_status_history
    ADD CONSTRAINT fk_order_history_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
    ADD CONSTRAINT fk_order_history_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE inventory_balance
    ADD CONSTRAINT fk_inventory_balance_warehouse_tenant FOREIGN KEY (tenant_id,warehouse_id) REFERENCES warehouse(tenant_id,id),
    ADD CONSTRAINT fk_inventory_balance_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id);
ALTER TABLE inventory_transaction
    ADD CONSTRAINT fk_inventory_tx_warehouse_tenant FOREIGN KEY (tenant_id,warehouse_id) REFERENCES warehouse(tenant_id,id),
    ADD CONSTRAINT fk_inventory_tx_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id),
    ADD CONSTRAINT fk_inventory_tx_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
    ADD CONSTRAINT fk_inventory_tx_installation_tenant FOREIGN KEY (tenant_id,installation_id) REFERENCES installation_job(tenant_id,id),
    ADD CONSTRAINT fk_inventory_tx_service_tenant FOREIGN KEY (tenant_id,service_request_id) REFERENCES service_request(tenant_id,id),
    ADD CONSTRAINT fk_inventory_tx_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE inventory_reservation
    ADD CONSTRAINT fk_inventory_reservation_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
    ADD CONSTRAINT fk_inventory_reservation_warehouse_tenant FOREIGN KEY (tenant_id,warehouse_id) REFERENCES warehouse(tenant_id,id),
    ADD CONSTRAINT fk_inventory_reservation_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id);
ALTER TABLE installation_job
    ADD CONSTRAINT fk_install_job_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
    ADD CONSTRAINT fk_install_job_customer_tenant FOREIGN KEY (tenant_id,customer_id) REFERENCES customer_profile(tenant_id,id),
    ADD CONSTRAINT fk_install_job_engineer_tenant FOREIGN KEY (tenant_id,assigned_engineer_id) REFERENCES app_user(tenant_id,id),
    ADD CONSTRAINT fk_install_job_team_tenant FOREIGN KEY (tenant_id,team_id) REFERENCES installation_team(tenant_id,id);
ALTER TABLE installation_checklist_item
    ADD CONSTRAINT fk_checklist_installation_tenant FOREIGN KEY (tenant_id,installation_id) REFERENCES installation_job(tenant_id,id),
    ADD CONSTRAINT fk_checklist_completed_by_tenant FOREIGN KEY (tenant_id,completed_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE warranty
    ADD CONSTRAINT fk_warranty_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
    ADD CONSTRAINT fk_warranty_installation_tenant FOREIGN KEY (tenant_id,installation_id) REFERENCES installation_job(tenant_id,id),
    ADD CONSTRAINT fk_warranty_customer_tenant FOREIGN KEY (tenant_id,customer_id) REFERENCES customer_profile(tenant_id,id),
    ADD CONSTRAINT fk_warranty_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id);

-- Finance, service and private evidence graph.
ALTER TABLE invoice
    ADD CONSTRAINT fk_invoice_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
    ADD CONSTRAINT fk_invoice_created_by_tenant FOREIGN KEY (tenant_id,created_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE invoice_item
    ADD CONSTRAINT fk_invoice_item_invoice_tenant FOREIGN KEY (tenant_id,invoice_id) REFERENCES invoice(tenant_id,id),
    ADD CONSTRAINT fk_invoice_item_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id);
ALTER TABLE payment
    ADD CONSTRAINT fk_payment_invoice_tenant FOREIGN KEY (tenant_id,invoice_id) REFERENCES invoice(tenant_id,id),
    ADD CONSTRAINT fk_payment_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
    ADD CONSTRAINT fk_payment_recorded_by_tenant FOREIGN KEY (tenant_id,recorded_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE payment_refund
    ADD CONSTRAINT fk_refund_payment_tenant FOREIGN KEY (tenant_id,payment_id) REFERENCES payment(tenant_id,id),
    ADD CONSTRAINT fk_refund_recorded_by_tenant FOREIGN KEY (tenant_id,recorded_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE credit_note
    ADD CONSTRAINT fk_credit_note_invoice_tenant FOREIGN KEY (tenant_id,invoice_id) REFERENCES invoice(tenant_id,id),
    ADD CONSTRAINT fk_credit_note_created_by_tenant FOREIGN KEY (tenant_id,created_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE service_request
    ADD CONSTRAINT fk_service_request_customer_tenant FOREIGN KEY (tenant_id,customer_id) REFERENCES customer_profile(tenant_id,id),
    ADD CONSTRAINT fk_service_request_installation_tenant FOREIGN KEY (tenant_id,installation_id) REFERENCES installation_job(tenant_id,id),
    ADD CONSTRAINT fk_service_request_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id),
    ADD CONSTRAINT fk_service_request_engineer_tenant FOREIGN KEY (tenant_id,assigned_engineer_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE service_visit
    ADD CONSTRAINT fk_service_visit_ticket_tenant FOREIGN KEY (tenant_id,service_request_id) REFERENCES service_request(tenant_id,id),
    ADD CONSTRAINT fk_service_visit_engineer_tenant FOREIGN KEY (tenant_id,engineer_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE service_event
    ADD CONSTRAINT fk_service_event_ticket_tenant FOREIGN KEY (tenant_id,service_request_id) REFERENCES service_request(tenant_id,id),
    ADD CONSTRAINT fk_service_event_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE service_attachment
    ADD CONSTRAINT fk_service_attachment_ticket_tenant FOREIGN KEY (tenant_id,service_request_id) REFERENCES service_request(tenant_id,id),
    ADD CONSTRAINT fk_service_attachment_uploader_tenant FOREIGN KEY (tenant_id,uploaded_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE service_comment
    ADD CONSTRAINT fk_service_comment_ticket_tenant FOREIGN KEY (tenant_id,service_request_id) REFERENCES service_request(tenant_id,id),
    ADD CONSTRAINT fk_service_comment_author_tenant FOREIGN KEY (tenant_id,author_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE site_survey_history
    ADD CONSTRAINT fk_survey_history_survey_tenant FOREIGN KEY (tenant_id,survey_id) REFERENCES site_survey(tenant_id,id),
    ADD CONSTRAINT fk_survey_history_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE site_survey_attachment
    ADD CONSTRAINT fk_survey_attachment_survey_tenant FOREIGN KEY (tenant_id,survey_id) REFERENCES site_survey(tenant_id,id),
    ADD CONSTRAINT fk_survey_attachment_uploader_tenant FOREIGN KEY (tenant_id,uploaded_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE warranty_claim
    ADD CONSTRAINT fk_warranty_claim_warranty_tenant FOREIGN KEY (tenant_id,warranty_id) REFERENCES warranty(tenant_id,id),
    ADD CONSTRAINT fk_warranty_claim_customer_tenant FOREIGN KEY (tenant_id,customer_id) REFERENCES customer_profile(tenant_id,id),
    ADD CONSTRAINT fk_warranty_claim_reviewer_tenant FOREIGN KEY (tenant_id,reviewed_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE warranty_claim_attachment
    ADD CONSTRAINT fk_warranty_attachment_claim_tenant FOREIGN KEY (tenant_id,claim_id) REFERENCES warranty_claim(tenant_id,id),
    ADD CONSTRAINT fk_warranty_attachment_uploader_tenant FOREIGN KEY (tenant_id,uploaded_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE installation_schedule_history
    ADD CONSTRAINT fk_install_schedule_job_tenant FOREIGN KEY (tenant_id,installation_id) REFERENCES installation_job(tenant_id,id),
    ADD CONSTRAINT fk_install_schedule_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE offline_sync_operation
    ADD CONSTRAINT fk_offline_sync_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE offline_sync_attachment
    ADD CONSTRAINT fk_offline_attachment_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id),
    ADD CONSTRAINT fk_offline_attachment_operation_tenant FOREIGN KEY (tenant_id,operation_id) REFERENCES offline_sync_operation(tenant_id,id);
ALTER TABLE integration_secret_reference
    ADD CONSTRAINT fk_secret_reference_connection_tenant FOREIGN KEY (tenant_id,integration_connection_id) REFERENCES integration_connection(tenant_id,id);
ALTER TABLE customer_profile
    ADD CONSTRAINT fk_customer_profile_user_tenant FOREIGN KEY (tenant_id,user_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE branch_user
    ADD CONSTRAINT fk_branch_user_user_tenant FOREIGN KEY (tenant_id,user_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE engineer_profile
    ADD CONSTRAINT fk_engineer_profile_user_tenant FOREIGN KEY (tenant_id,user_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE approval_instance
    ADD CONSTRAINT fk_approval_instance_requester_tenant FOREIGN KEY (tenant_id,requested_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE approval_decision
    ADD CONSTRAINT fk_approval_decision_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE audit_event
    ADD CONSTRAINT fk_audit_event_branch_tenant FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id);
ALTER TABLE notification_delivery
    ADD CONSTRAINT fk_notification_delivery_user_tenant FOREIGN KEY (tenant_id,user_id) REFERENCES app_user(tenant_id,id);

-- Supporting lookup indexes for the most frequently traversed tenant relationships.
CREATE INDEX ix_sales_lead_tenant_enquiry ON sales_lead(tenant_id,enquiry_id);
CREATE INDEX ix_quotation_tenant_customer ON quotation(tenant_id,customer_id);
CREATE INDEX ix_customer_order_tenant_customer ON customer_order(tenant_id,customer_id);
CREATE INDEX ix_invoice_item_tenant_product ON invoice_item(tenant_id,product_id);
CREATE INDEX ix_service_request_tenant_customer ON service_request(tenant_id,customer_id);
CREATE INDEX ix_warranty_claim_tenant_customer ON warranty_claim(tenant_id,customer_id);

-- Rollback is a new forward migration only; never edit V58 after it has been applied.
-- To roll back manually, drop all fk_*_tenant constraints added above (including
-- fk_engineer_profile_user_tenant, fk_approval_instance_requester_tenant and
-- fk_approval_decision_actor_tenant), then uq_*_tenant_id
-- unique indexes. Do not drop pre-existing single-column or V37/V43/V49/V57 constraints.

-- SOURCE backend/src/main/resources/db/migration/V59__ensure_platform_audit_permission.sql
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

-- SOURCE backend/src/main/resources/db/migration/V60__enforce_tenant_id_not_null_on_tenant_owned_tables.sql
-- Phase 2 tenant ownership hardening: tenant-owned rows must always carry tenant_id.
-- V58 preflight runs before this migration. These ALTERs intentionally fail closed if
-- any legacy NULL tenant_id remains; no tenant value is guessed or backfilled.
-- notification_delivery is intentionally excluded: it stores both tenant-scoped notifications
-- and platform-level notifications (for example, MFA_CODE for a global app_user whose tenant_id
-- is NULL). Keep tenant_id nullable here; application writes must still set it for tenant events.
ALTER TABLE customer_order MODIFY tenant_id BINARY(16) NOT NULL;
ALTER TABLE order_item MODIFY tenant_id BINARY(16) NOT NULL;
ALTER TABLE quotation MODIFY tenant_id BINARY(16) NOT NULL;
ALTER TABLE quotation_item MODIFY tenant_id BINARY(16) NOT NULL;
ALTER TABLE site_survey MODIFY tenant_id BINARY(16) NOT NULL;
ALTER TABLE lead_history MODIFY tenant_id BINARY(16) NOT NULL;
ALTER TABLE integration_webhook_event MODIFY tenant_id BINARY(16) NOT NULL;
ALTER TABLE idempotency_record MODIFY tenant_id BINARY(16) NOT NULL;


-- SOURCE backend/src/main/resources/db/migration/V61__install_integration_and_approval_permissions.sql
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

-- SOURCE backend/src/main/resources/db/migration/V62__notification_delivery_idempotency.sql
-- Enforce notification idempotency across concurrent application instances.
-- MySQL permits multiple NULL tenant IDs, preserving platform-scoped MFA delivery rows.
CREATE UNIQUE INDEX uq_notification_delivery_tenant_idempotency ON notification_delivery(tenant_id, idempotency_key);

-- SOURCE backend/src/main/resources/db/migration/V63__tenant_scoped_export_jobs.sql
CREATE TABLE export_job (
    id BINARY(16) NOT NULL PRIMARY KEY,
    tenant_id BINARY(16) NOT NULL,
    requested_by BINARY(16) NOT NULL,
    report_type VARCHAR(40) NOT NULL,
    format VARCHAR(10) NOT NULL,
    from_date DATE NULL,
    to_date DATE NULL,
    status VARCHAR(20) NOT NULL,
    storage_key VARCHAR(500) NULL,
    content_type VARCHAR(120) NULL,
    file_size BIGINT NULL,
    last_error VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    expires_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_export_job_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_export_job_requester_tenant FOREIGN KEY (tenant_id, requested_by) REFERENCES app_user(tenant_id, id),
    CONSTRAINT chk_export_job_format CHECK (format IN ('CSV','XLSX','PDF')),
    CONSTRAINT chk_export_job_status CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED','EXPIRED')),
    INDEX ix_export_job_tenant_requester_created (tenant_id, requested_by, created_at),
    INDEX ix_export_job_status_updated (status, updated_at),
    INDEX ix_export_job_expiry (status, expires_at)
);
