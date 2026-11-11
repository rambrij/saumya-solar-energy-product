-- Consolidated clean-install baseline for MySQL 8.4.

-- This is the only active Flyway migration. It contains the former V1-V14 schema and seed changes.

-- DDL order: tables (dependency order), table extensions/constraints, standalone indexes, triggers, then seed/reference data.

-- No runtime feature, permission grant, tenant constraint, status check, or seed operation from the former chain is intentionally omitted.

-- Existing deployments are not in scope; this baseline is for a fresh database only.



-- TABLES

-- Source: V1__baseline.sql
-- Clean-install MySQL 8.4 baseline.
-- All columns, indexes, primary/unique keys, checks and foreign keys are declared at table creation.
-- Tables are ordered by foreign-key dependency; no post-creation table alterations are needed.
-- Seed and bootstrap statements execute after all required tables have been created.

CREATE TABLE tenant(
  id BINARY(16) PRIMARY KEY,
  name VARCHAR(150) NOT NULL,
  slug VARCHAR(100) NOT NULL UNIQUE,
  status VARCHAR(30) NOT NULL,
  discount_approval_threshold DECIMAL(5,2) NOT NULL DEFAULT 10.00,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  gstin VARCHAR(15) NULL,
  invoice_prefix VARCHAR(12) NOT NULL DEFAULT 'INV',
  service_reopen_days INT NULL,
  display_name VARCHAR(150) NULL,
  logo_url VARCHAR(1000) NULL,
  primary_color VARCHAR(7) NOT NULL DEFAULT '#0B6B4B',
  secondary_color VARCHAR(7) NOT NULL DEFAULT '#F6C85F',
  contact_email VARCHAR(254) NULL,
  contact_phone VARCHAR(40) NULL,
  support_email VARCHAR(254) NULL,
  support_phone VARCHAR(40) NULL,
  KEY ix_tenant_status_created (status, created_at)
);

-- Source: V1__baseline.sql
CREATE TABLE permission(
  id BINARY(16) PRIMARY KEY,
  code VARCHAR(80) NOT NULL UNIQUE
);

-- Source: V1__baseline.sql
CREATE TABLE role(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16),
  name VARCHAR(80) NOT NULL,
  UNIQUE(tenant_id,name)
);

-- Source: V1__baseline.sql
CREATE TABLE solar_package(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  code VARCHAR(80) NOT NULL,
  name VARCHAR(180) NOT NULL,
  capacity_kw DECIMAL(10,2),
  package_type VARCHAR(40),
  base_price DECIMAL(19,2),
  warranty_years INT,
  active BOOLEAN NOT NULL,
  image_url VARCHAR(1024) NULL,
  UNIQUE(tenant_id,code),
  CONSTRAINT uq_solar_package_tenant_id UNIQUE (tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE idempotency_record(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  operation_name VARCHAR(80),
  idempotency_key VARCHAR(120),
  resource_id BINARY(16),
  UNIQUE(tenant_id,operation_name,idempotency_key)
);

-- Source: V1__baseline.sql
CREATE TABLE payment_webhook_event(
  id BINARY(16) PRIMARY KEY,
  provider VARCHAR(60) NOT NULL,
  event_id VARCHAR(180) NOT NULL,
  payload_hash VARCHAR(64) NOT NULL,
  processing_status VARCHAR(24) NOT NULL,
  received_at TIMESTAMP(6) NOT NULL,
  processed_at TIMESTAMP(6),
  UNIQUE KEY uq_payment_webhook_provider_event(provider,event_id)
);

-- Source: V1__baseline.sql
CREATE TABLE subscription_plan (
  id BINARY(16) PRIMARY KEY,
  plan_code VARCHAR(50) NOT NULL,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(1000),
  billing_cycle VARCHAR(20) NOT NULL,
  price DECIMAL(19,2) NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'INR',
  max_users INT NOT NULL,
  max_branches INT NOT NULL,
  max_warehouses INT NOT NULL,
  max_storage_mb BIGINT NOT NULL,
  entitlements_json LONGTEXT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  max_products INT NOT NULL DEFAULT 100,
  max_customers INT NOT NULL DEFAULT 100,
  trial_period_days INT NOT NULL DEFAULT 14,
  display_order INT NOT NULL DEFAULT 100,
  CONSTRAINT uq_subscription_plan_code UNIQUE(plan_code),
  CONSTRAINT chk_subscription_plan_price CHECK(price>=0),
  CONSTRAINT chk_subscription_plan_limits CHECK(max_users>=1 AND max_branches>=1 AND max_warehouses>=1 AND max_storage_mb>=0),
  CONSTRAINT chk_subscription_plan_cycle CHECK(billing_cycle IN ('MONTHLY','YEARLY')),
  CONSTRAINT chk_subscription_plan_extended_limits CHECK(max_products>=1 AND max_customers>=1 AND trial_period_days>=0)
);

-- Source: V1__baseline.sql
CREATE TABLE auth_rate_limit_bucket (
  bucket_key CHAR(64) NOT NULL PRIMARY KEY,
  window_started_at TIMESTAMP(6) NOT NULL,
  request_count INT NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  INDEX ix_auth_rate_limit_updated (updated_at)
);

-- Source: V1__baseline.sql
CREATE TABLE role_permission(
  role_id BINARY(16),
  permission_id BINARY(16),
  PRIMARY KEY(role_id,permission_id),
  FOREIGN KEY(role_id) REFERENCES role(id),
  FOREIGN KEY(permission_id) REFERENCES permission(id)
);

-- Source: V1__baseline.sql
CREATE TABLE app_user(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16),
  name VARCHAR(150),
  email VARCHAR(190) UNIQUE,
  password_hash VARCHAR(255),
  account_type VARCHAR(30),
  enabled BOOLEAN NOT NULL,
  token_version INT NOT NULL DEFAULT 0,
  failed_login_attempts INT NOT NULL DEFAULT 0,
  locked_until TIMESTAMP(6) NULL,
  last_login_at TIMESTAMP(6) NULL,
  FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  KEY ix_app_user_locked_until (locked_until),
  CONSTRAINT uq_app_user_tenant_id UNIQUE (tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE product_category(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(500),
  active BOOLEAN NOT NULL,
  UNIQUE(tenant_id,name),
  FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  CONSTRAINT uq_product_category_tenant_id UNIQUE (tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE installation_team(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  name VARCHAR(150) NOT NULL,
  member_ids_json VARCHAR(2000),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL,
  FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  UNIQUE(tenant_id,name),
  INDEX ix_install_team_tenant_active(tenant_id,active),
  CONSTRAINT uq_installation_team_tenant_id UNIQUE (tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE installation_checklist_template(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  code VARCHAR(60) NOT NULL,
  title VARCHAR(200) NOT NULL,
  mandatory BOOLEAN NOT NULL DEFAULT TRUE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL,
  FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  UNIQUE(tenant_id,code),
  INDEX ix_check_template_tenant_active(tenant_id,active)
);

-- Source: V1__baseline.sql
CREATE TABLE invoice_sequence(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  fiscal_year VARCHAR(9) NOT NULL,
  next_number BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uq_invoice_sequence_tenant_year(tenant_id,fiscal_year),
  CONSTRAINT fk_invoice_sequence_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id)
);

-- Source: V1__baseline.sql
CREATE TABLE service_sla_policy(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  priority VARCHAR(20) NOT NULL,
  response_minutes INT NOT NULL,
  resolution_minutes INT NOT NULL,
  time_zone VARCHAR(80) NOT NULL DEFAULT 'Asia/Kolkata',
  business_start TIME NULL,
  business_end TIME NULL,
  holidays_json JSON NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  working_days_json JSON NULL,
  UNIQUE KEY uq_service_sla_tenant_priority(tenant_id,priority),
  CONSTRAINT fk_service_sla_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  CHECK(response_minutes>0 AND resolution_minutes>=response_minutes)
);

-- Source: V1__baseline.sql
CREATE TABLE branch (
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  code VARCHAR(50) NOT NULL,
  name VARCHAR(150) NOT NULL,
  address VARCHAR(700),
  phone VARCHAR(40),
  email VARCHAR(254),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_branch_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT uq_branch_tenant_code UNIQUE (tenant_id,code),
  CONSTRAINT uq_branch_tenant_id UNIQUE (tenant_id,id),
  INDEX ix_branch_tenant_active (tenant_id,active)
);

-- Source: V1__baseline.sql
CREATE TABLE subscription_usage (
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  meter_code VARCHAR(60) NOT NULL,
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  quantity BIGINT NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_subscription_usage_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  CONSTRAINT uq_subscription_usage_period UNIQUE(tenant_id,meter_code,period_start,period_end),
  CONSTRAINT chk_subscription_usage_quantity CHECK(quantity>=0),
  INDEX ix_subscription_usage_tenant_period(tenant_id,period_start,period_end)
);

-- Source: V1__baseline.sql
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

-- Source: V1__baseline.sql
CREATE TABLE approval_workflow (
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  code VARCHAR(60) NOT NULL,
  name VARCHAR(150) NOT NULL,
  entity_type VARCHAR(60) NOT NULL,
  steps_json LONGTEXT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_approval_workflow_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  CONSTRAINT uq_approval_workflow_code UNIQUE(tenant_id,code),
  CONSTRAINT uq_approval_workflow_tenant_id UNIQUE(tenant_id,id),
  INDEX ix_approval_workflow_tenant_entity (tenant_id,entity_type,active)
);

-- Source: V1__baseline.sql
CREATE TABLE integration_connection (
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  provider_code VARCHAR(60) NOT NULL,
  integration_type VARCHAR(30) NOT NULL,
  display_name VARCHAR(150) NOT NULL,
  status VARCHAR(20) NOT NULL,
  secret_reference VARCHAR(500),
  config_json LONGTEXT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_integration_connection_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  CONSTRAINT uq_integration_connection_tenant_id UNIQUE(tenant_id,id),
  CONSTRAINT chk_integration_connection_type CHECK(integration_type IN ('ACCOUNTING','PAYMENT_GATEWAY','SMS','WHATSAPP','GENERIC')),
  CONSTRAINT chk_integration_connection_status CHECK(status IN ('ACTIVE','DISABLED','ERROR')),
  INDEX ix_integration_connection_tenant_type(tenant_id,integration_type,status)
);

-- Source: V1__baseline.sql
CREATE TABLE integration_webhook_event (
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  provider_code VARCHAR(60) NOT NULL,
  provider_event_id VARCHAR(160) NOT NULL,
  payload_hash VARCHAR(128) NOT NULL,
  status VARCHAR(20) NOT NULL,
  received_at TIMESTAMP(6) NOT NULL,
  processed_at TIMESTAMP(6),
  CONSTRAINT fk_integration_webhook_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  CONSTRAINT uq_integration_webhook_provider_event UNIQUE(provider_code,provider_event_id),
  INDEX ix_integration_webhook_tenant_received(tenant_id,received_at)
);

-- Source: V1__baseline.sql
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

-- Source: V1__baseline.sql
CREATE TABLE user_role(
  user_id BINARY(16),
  role_id BINARY(16),
  PRIMARY KEY(user_id,role_id),
  FOREIGN KEY(user_id) REFERENCES app_user(id),
  FOREIGN KEY(role_id) REFERENCES role(id)
);

-- Source: V1__baseline.sql
CREATE TABLE product(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  category_id BINARY(16) NOT NULL,
  sku VARCHAR(80) NOT NULL,
  name VARCHAR(180) NOT NULL,
  description TEXT,
  price DECIMAL(19,2) NOT NULL,
  tax_percent DECIMAL(5,2) NOT NULL,
  active BOOLEAN NOT NULL,
  attributes_json JSON,
  manufacturer VARCHAR(150) NULL,
  model VARCHAR(120) NULL,
  technology VARCHAR(120) NULL,
  purchase_price DECIMAL(19,2) NOT NULL DEFAULT 0,
  discount_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
  warranty_years INT NULL,
  image_url VARCHAR(1000) NULL,
  datasheet_url VARCHAR(1000) NULL,
  reorder_level DECIMAL(19,4) NOT NULL DEFAULT 0,
  unit_of_measure VARCHAR(30) NOT NULL DEFAULT 'PCS',
  hsn_code VARCHAR(40) NULL,
  serialized BOOLEAN NOT NULL DEFAULT FALSE,
  UNIQUE(tenant_id,sku),
  FOREIGN KEY(category_id) REFERENCES product_category(id),
  KEY ix_product_tenant_active (tenant_id,active),
  CONSTRAINT uq_product_tenant_id UNIQUE (tenant_id,id),
  CONSTRAINT fk_product_category_tenant FOREIGN KEY (tenant_id,category_id) REFERENCES product_category(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE customer_profile(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  user_id BINARY(16),
  name VARCHAR(150),
  email VARCHAR(190),
  phone VARCHAR(30),
  address VARCHAR(500),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  gstin VARCHAR(15) NULL,
  sms_consent BOOLEAN NOT NULL DEFAULT FALSE,
  whatsapp_consent BOOLEAN NOT NULL DEFAULT FALSE,
  messaging_consent_updated_at TIMESTAMP(6) NULL,
  UNIQUE(tenant_id,email),
  FOREIGN KEY(user_id) REFERENCES app_user(id),
  UNIQUE KEY uq_customer_profile_tenant_id (tenant_id,id),
  CONSTRAINT fk_customer_profile_user_tenant FOREIGN KEY (tenant_id,user_id) REFERENCES app_user(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE refresh_token(
  id BINARY(16) PRIMARY KEY,
  user_id BINARY(16) NOT NULL,
  token VARCHAR(190) NOT NULL UNIQUE,
  expires_at TIMESTAMP NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE,
  family_id BINARY(16) NOT NULL,
  replaced_by_token_id BINARY(16) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  FOREIGN KEY(user_id) REFERENCES app_user(id),
  KEY ix_refresh_token_family (family_id),
  KEY ix_refresh_token_user_family (user_id, family_id),
  CONSTRAINT fk_refresh_token_replacement FOREIGN KEY (replaced_by_token_id) REFERENCES refresh_token(id)
);

-- Source: V1__baseline.sql
CREATE TABLE password_reset_token(
  id BINARY(16) PRIMARY KEY,
  user_id BINARY(16) NOT NULL,
  token VARCHAR(190) NOT NULL UNIQUE,
  expires_at TIMESTAMP NOT NULL,
  used BOOLEAN NOT NULL DEFAULT FALSE,
  FOREIGN KEY(user_id) REFERENCES app_user(id)
);

-- Source: V1__baseline.sql
CREATE TABLE warehouse(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  code VARCHAR(50) NOT NULL,
  name VARCHAR(150) NOT NULL,
  address VARCHAR(700),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL,
  branch_id BINARY(16) NULL,
  FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  UNIQUE(tenant_id,code),
  INDEX ix_warehouse_tenant_active(tenant_id,active),
  CONSTRAINT fk_warehouse_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id),
  KEY ix_warehouse_tenant_branch_active (tenant_id,branch_id,active),
  CONSTRAINT uq_warehouse_tenant_id UNIQUE (tenant_id,id)
);

-- Source: V1__baseline.sql
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
  channel VARCHAR(20) NOT NULL DEFAULT 'EMAIL',
  idempotency_key VARCHAR(120) NULL,
  provider_message_id VARCHAR(120) NULL,
  next_attempt_at TIMESTAMP(6) NULL,
  message_body TEXT NULL,
  CONSTRAINT fk_notification_delivery_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_notification_delivery_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT chk_notification_delivery_status CHECK (status IN (_utf8mb4'PENDING', _utf8mb4'DELIVERED', _utf8mb4'FAILED')),
  INDEX ix_notification_delivery_tenant_created (tenant_id, created_at),
  INDEX ix_notification_delivery_status_created (status, created_at),
  KEY ix_notification_retry (status,next_attempt_at),
  UNIQUE KEY uq_notification_idempotency (tenant_id,idempotency_key),
  CONSTRAINT fk_notification_delivery_user_tenant FOREIGN KEY (tenant_id,user_id) REFERENCES app_user(tenant_id,id),
  UNIQUE KEY uq_notification_delivery_tenant_idempotency (tenant_id, idempotency_key)
);

-- Source: V1__baseline.sql
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

-- Source: V1__baseline.sql
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
  INDEX ix_engineer_profile_tenant_active (tenant_id,active),
  CONSTRAINT fk_engineer_profile_user_tenant FOREIGN KEY (tenant_id,user_id) REFERENCES app_user(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE branch_user (
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  branch_id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_branch_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_branch_user_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id),
  CONSTRAINT fk_branch_user_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT uq_branch_user UNIQUE (tenant_id,branch_id,user_id),
  INDEX ix_branch_user_tenant_user (tenant_id,user_id),
  CONSTRAINT fk_branch_user_user_tenant FOREIGN KEY (tenant_id,user_id) REFERENCES app_user(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE tenant_subscription (
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  plan_id BINARY(16) NOT NULL,
  status VARCHAR(20) NOT NULL,
  starts_at TIMESTAMP(6) NOT NULL,
  ends_at TIMESTAMP(6),
  renews_at TIMESTAMP(6),
  external_reference VARCHAR(160),
  created_by BINARY(16),
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  billing_cycle VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
  trial_ends_at TIMESTAMP(6) NULL,
  auto_renew BOOLEAN NOT NULL DEFAULT TRUE,
  notes VARCHAR(2000) NULL,
  replaced_by_subscription_id BINARY(16) NULL,
  CONSTRAINT fk_subscription_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_subscription_plan FOREIGN KEY(plan_id) REFERENCES subscription_plan(id),
  CONSTRAINT fk_subscription_actor FOREIGN KEY(created_by) REFERENCES app_user(id),
  CONSTRAINT chk_tenant_subscription_status CHECK(status IN ('TRIAL','ACTIVE','PAST_DUE','SUSPENDED','CANCELLED','EXPIRED')),
  INDEX ix_subscription_tenant_status(tenant_id,status,starts_at),
  INDEX ix_subscription_expiry(status,ends_at),
  CONSTRAINT fk_subscription_replaced_by FOREIGN KEY(replaced_by_subscription_id) REFERENCES tenant_subscription(id),
  CONSTRAINT chk_subscription_billing_cycle CHECK(billing_cycle IN ('MONTHLY','YEARLY'))
);

-- Source: V1__baseline.sql
CREATE TABLE audit_event (
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16),
  actor_id BINARY(16),
  actor_type VARCHAR(30) NOT NULL,
  action VARCHAR(100) NOT NULL,
  entity_type VARCHAR(100) NOT NULL,
  entity_id VARCHAR(100),
  outcome VARCHAR(20) NOT NULL,
  before_json LONGTEXT,
  after_json LONGTEXT,
  reason VARCHAR(1000),
  correlation_id VARCHAR(100),
  created_at TIMESTAMP(6) NOT NULL,
  branch_id BINARY(16) NULL,
  request_id VARCHAR(100) NULL,
  source_ip VARCHAR(64) NULL,
  user_agent VARCHAR(512) NULL,
  CONSTRAINT fk_audit_event_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_audit_event_actor FOREIGN KEY(actor_id) REFERENCES app_user(id),
  INDEX ix_audit_event_tenant_time(tenant_id,created_at),
  INDEX ix_audit_event_tenant_entity(tenant_id,entity_type,entity_id),
  INDEX ix_audit_event_actor_time(actor_id,created_at),
  KEY ix_audit_tenant_branch_created (tenant_id, branch_id, created_at),
  KEY ix_audit_entity (tenant_id, entity_type, entity_id, created_at),
  CONSTRAINT fk_audit_event_branch_tenant FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE approval_instance (
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  workflow_id BINARY(16) NOT NULL,
  entity_type VARCHAR(60) NOT NULL,
  entity_id VARCHAR(100) NOT NULL,
  steps_snapshot_json LONGTEXT NOT NULL,
  status VARCHAR(20) NOT NULL,
  current_step INT NOT NULL,
  requested_by BINARY(16) NOT NULL,
  requested_at TIMESTAMP(6) NOT NULL,
  completed_at TIMESTAMP(6) NULL,
  CONSTRAINT uq_approval_instance_tenant_id UNIQUE(tenant_id,id),
  CONSTRAINT fk_approval_instance_workflow FOREIGN KEY(tenant_id,workflow_id) REFERENCES approval_workflow(tenant_id,id),
  CONSTRAINT fk_approval_instance_actor FOREIGN KEY(requested_by) REFERENCES app_user(id),
  CONSTRAINT chk_approval_instance_status CHECK(status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
  INDEX ix_approval_instance_tenant_entity (tenant_id,entity_type,entity_id,status),
  INDEX ix_approval_instance_tenant_status (tenant_id,status,requested_at),
  CONSTRAINT fk_approval_instance_requester_tenant FOREIGN KEY (tenant_id,requested_by) REFERENCES app_user(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE integration_sync_job (
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  connection_id BINARY(16) NOT NULL,
  entity_type VARCHAR(60) NOT NULL,
  entity_id VARCHAR(100) NOT NULL,
  operation VARCHAR(30) NOT NULL,
  idempotency_key VARCHAR(160) NOT NULL,
  payload_json LONGTEXT NOT NULL,
  status VARCHAR(30) NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMP(6) NOT NULL,
  last_error VARCHAR(1000),
  external_reference VARCHAR(160),
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_integration_job_connection FOREIGN KEY(tenant_id,connection_id) REFERENCES integration_connection(tenant_id,id),
  CONSTRAINT uq_integration_job_idempotency UNIQUE(tenant_id,connection_id,idempotency_key),
  CONSTRAINT chk_integration_job_status CHECK(status IN ('QUEUED','PROCESSING','SUCCEEDED','RETRY','WAITING_FOR_ADAPTER','DEAD_LETTER')),
  INDEX ix_integration_job_dispatch(status,next_attempt_at,created_at),
  INDEX ix_integration_job_tenant_entity(tenant_id,entity_type,entity_id)
);

-- Source: V1__baseline.sql
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
  expected_version BIGINT NULL,
  resulting_version BIGINT NULL,
  conflict_code VARCHAR(64) NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  resolved_at TIMESTAMP(6) NULL,
  resolution_action VARCHAR(32) NULL,
  CONSTRAINT fk_offline_sync_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_offline_sync_actor FOREIGN KEY (actor_id) REFERENCES app_user(id),
  CONSTRAINT uq_offline_sync_client_operation UNIQUE (tenant_id, actor_id, client_operation_id),
  CONSTRAINT chk_offline_sync_operation_type CHECK (operation_type IN (
        'SERVICE_COMMENT_CREATE', 'SURVEY_UPDATE', 'SURVEY_START', 'SURVEY_SUBMIT'
    )),
  INDEX ix_offline_sync_actor_received (tenant_id, actor_id, received_at),
  INDEX ix_offline_sync_resource (tenant_id, operation_type, resource_id),
  CONSTRAINT chk_offline_sync_status CHECK (status IN ('APPLIED','DUPLICATE','CONFLICT','REJECTED','PENDING')),
  CONSTRAINT uq_offline_sync_operation_tenant_id UNIQUE (tenant_id,id),
  CONSTRAINT fk_offline_sync_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id)
);

-- Source: V1__baseline.sql
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
  INDEX ix_secret_reference_expiry (tenant_id, status, expires_at),
  CONSTRAINT fk_secret_reference_connection_tenant FOREIGN KEY (tenant_id,integration_connection_id) REFERENCES integration_connection(tenant_id,id)
);

-- Source: V1__baseline.sql
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

-- Source: V1__baseline.sql
CREATE TABLE package_item(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  package_id BINARY(16),
  product_id BINARY(16),
  quantity INT,
  optional_item BOOLEAN,
  FOREIGN KEY(package_id) REFERENCES solar_package(id),
  FOREIGN KEY(product_id) REFERENCES product(id),
  CONSTRAINT fk_package_item_package_tenant FOREIGN KEY (tenant_id,package_id) REFERENCES solar_package(tenant_id,id),
  CONSTRAINT fk_package_item_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE inventory_balance(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  warehouse_id BINARY(16) NOT NULL,
  product_id BINARY(16) NOT NULL,
  on_hand DECIMAL(19,4) NOT NULL DEFAULT 0,
  reserved DECIMAL(19,4) NOT NULL DEFAULT 0,
  damaged DECIMAL(19,4) NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  FOREIGN KEY(warehouse_id) REFERENCES warehouse(id),
  FOREIGN KEY(product_id) REFERENCES product(id),
  UNIQUE(tenant_id,warehouse_id,product_id),
  CHECK(on_hand>=0 AND reserved>=0 AND damaged>=0 AND reserved+damaged<=on_hand),
  INDEX ix_inventory_balance_tenant_product(tenant_id,product_id),
  CONSTRAINT fk_inventory_balance_warehouse_tenant FOREIGN KEY (tenant_id,warehouse_id) REFERENCES warehouse(tenant_id,id),
  CONSTRAINT fk_inventory_balance_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE approval_decision (
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  instance_id BINARY(16) NOT NULL,
  step_order INT NOT NULL,
  decision VARCHAR(20) NOT NULL,
  actor_id BINARY(16) NOT NULL,
  comments VARCHAR(1000),
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_approval_decision_instance FOREIGN KEY(tenant_id,instance_id) REFERENCES approval_instance(tenant_id,id),
  CONSTRAINT fk_approval_decision_actor FOREIGN KEY(actor_id) REFERENCES app_user(id),
  CONSTRAINT chk_approval_decision_value CHECK(decision IN ('APPROVE','REJECT')),
  INDEX ix_approval_decision_tenant_instance (tenant_id,instance_id,step_order),
  CONSTRAINT fk_approval_decision_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id)
);

-- Source: V1__baseline.sql
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
  INDEX ix_offline_attachment_operation (tenant_id, operation_id, status),
  CONSTRAINT fk_offline_attachment_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id),
  CONSTRAINT fk_offline_attachment_operation_tenant FOREIGN KEY (tenant_id,operation_id) REFERENCES offline_sync_operation(tenant_id,id)
);

-- Source: V1__baseline.sql
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

-- Source: V1__baseline.sql
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

-- Source: V1__baseline.sql
CREATE TABLE enquiry(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  customer_id BINARY(16),
  name VARCHAR(150),
  email VARCHAR(190),
  phone VARCHAR(30),
  property_type VARCHAR(50),
  monthly_bill DECIMAL(19,2),
  required_capacity_kw DECIMAL(10,2),
  message TEXT,
  status VARCHAR(30),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  branch_id BINARY(16) NULL,
  address_id BINARY(16) NULL,
  site_address VARCHAR(500) NULL,
  city_pincode VARCHAR(160) NULL,
  KEY ix_enquiry_tenant_status (tenant_id,status),
  CONSTRAINT fk_enquiry_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id),
  KEY ix_enquiry_tenant_branch (tenant_id,branch_id),
  CONSTRAINT fk_enquiry_customer_address FOREIGN KEY (tenant_id,address_id) REFERENCES customer_address(tenant_id,id),
  KEY ix_enquiry_tenant_address (tenant_id,address_id),
  CONSTRAINT uq_enquiry_tenant_id UNIQUE (tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE sales_lead(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  enquiry_id BINARY(16),
  customer_id BINARY(16),
  stage VARCHAR(40),
  priority VARCHAR(20),
  assigned_user_id BINARY(16),
  next_follow_up TIMESTAMP NULL,
  lost_reason VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  branch_id BINARY(16) NULL,
  FOREIGN KEY(assigned_user_id) REFERENCES app_user(id),
  KEY ix_lead_tenant_stage (tenant_id,stage),
  CONSTRAINT fk_sales_lead_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id),
  KEY ix_sales_lead_tenant_branch (tenant_id,branch_id),
  CONSTRAINT uq_sales_lead_tenant_id UNIQUE (tenant_id,id),
  CONSTRAINT fk_sales_lead_enquiry_tenant FOREIGN KEY (tenant_id,enquiry_id) REFERENCES enquiry(tenant_id,id),
  CONSTRAINT fk_sales_lead_customer_tenant FOREIGN KEY (tenant_id,customer_id) REFERENCES customer_profile(tenant_id,id),
  CONSTRAINT fk_sales_lead_assignee_tenant FOREIGN KEY (tenant_id,assigned_user_id) REFERENCES app_user(tenant_id,id),
  KEY ix_sales_lead_tenant_enquiry (tenant_id,enquiry_id)
);

-- Source: V1__baseline.sql
CREATE TABLE lead_history(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  lead_id BINARY(16),
  old_stage VARCHAR(40),
  new_stage VARCHAR(40),
  actor_id BINARY(16),
  reason VARCHAR(500),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  event_type VARCHAR(30) NOT NULL DEFAULT 'STAGE',
  old_assigned_user_id BINARY(16) NULL,
  new_assigned_user_id BINARY(16) NULL,
  FOREIGN KEY(lead_id) REFERENCES sales_lead(id),
  KEY ix_lead_history_tenant_lead_created (tenant_id, lead_id, created_at),
  CONSTRAINT fk_lead_history_lead_tenant FOREIGN KEY (tenant_id,lead_id) REFERENCES sales_lead(tenant_id,id),
  CONSTRAINT fk_lead_history_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE site_survey(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  lead_id BINARY(16),
  scheduled_at TIMESTAMP NULL,
  assigned_user_id BINARY(16),
  property_type VARCHAR(50),
  roof_type VARCHAR(50),
  usable_area_sqft DECIMAL(10,2),
  shading VARCHAR(100),
  sanctioned_load_kw DECIMAL(10,2),
  notes TEXT,
  status VARCHAR(30),
  completed_at TIMESTAMP NULL,
  latitude DECIMAL(10,7) NULL,
  longitude DECIMAL(10,7) NULL,
  roof_orientation VARCHAR(40) NULL,
  monthly_electricity_bill DECIMAL(19,2) NULL,
  electrical_phase VARCHAR(30) NULL,
  meter_type VARCHAR(40) NULL,
  findings TEXT NULL,
  recommendation TEXT NULL,
  submitted_at TIMESTAMP(6) NULL,
  reviewed_by BINARY(16) NULL,
  reviewed_at TIMESTAMP(6) NULL,
  rejection_reason VARCHAR(1000) NULL,
  branch_id BINARY(16) NULL,
  FOREIGN KEY(lead_id) REFERENCES sales_lead(id),
  KEY ix_survey_tenant_status (tenant_id,status),
  KEY ix_survey_tenant_status_schedule (tenant_id, status, scheduled_at),
  CONSTRAINT fk_site_survey_reviewer FOREIGN KEY (reviewed_by) REFERENCES app_user(id),
  CONSTRAINT fk_site_survey_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id),
  KEY ix_site_survey_tenant_branch (tenant_id,branch_id),
  CONSTRAINT uq_site_survey_tenant_id UNIQUE (tenant_id,id),
  CONSTRAINT fk_site_survey_lead_tenant FOREIGN KEY (tenant_id,lead_id) REFERENCES sales_lead(tenant_id,id),
  CONSTRAINT fk_site_survey_assignee_tenant FOREIGN KEY (tenant_id,assigned_user_id) REFERENCES app_user(tenant_id,id),
  CONSTRAINT fk_site_survey_reviewer_tenant FOREIGN KEY (tenant_id,reviewed_by) REFERENCES app_user(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE quotation(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  quote_group_id BINARY(16),
  quotation_number VARCHAR(60),
  revision INT,
  customer_id BINARY(16),
  lead_id BINARY(16),
  survey_id BINARY(16),
  status VARCHAR(40),
  subtotal DECIMAL(19,2),
  discount_percent DECIMAL(5,2),
  discount_amount DECIMAL(19,2),
  tax_amount DECIMAL(19,2),
  grand_total DECIMAL(19,2),
  valid_until DATE,
  approval_required BOOLEAN,
  created_by BINARY(16),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  decision_by BINARY(16) NULL,
  decision_at TIMESTAMP NULL,
  branch_id BINARY(16) NULL,
  UNIQUE(tenant_id,quotation_number,revision),
  KEY ix_quote_tenant_status (tenant_id,status),
  CONSTRAINT fk_quotation_decision_by FOREIGN KEY (decision_by) REFERENCES app_user(id),
  CONSTRAINT fk_quotation_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id),
  KEY ix_quotation_tenant_branch (tenant_id,branch_id),
  CONSTRAINT uq_quotation_tenant_id UNIQUE (tenant_id,id),
  CONSTRAINT fk_quotation_customer_tenant FOREIGN KEY (tenant_id,customer_id) REFERENCES customer_profile(tenant_id,id),
  CONSTRAINT fk_quotation_lead_tenant FOREIGN KEY (tenant_id,lead_id) REFERENCES sales_lead(tenant_id,id),
  CONSTRAINT fk_quotation_survey_tenant FOREIGN KEY (tenant_id,survey_id) REFERENCES site_survey(tenant_id,id),
  CONSTRAINT fk_quotation_created_by_tenant FOREIGN KEY (tenant_id,created_by) REFERENCES app_user(tenant_id,id),
  CONSTRAINT fk_quotation_decision_by_tenant FOREIGN KEY (tenant_id,decision_by) REFERENCES app_user(tenant_id,id),
  KEY ix_quotation_tenant_customer (tenant_id,customer_id)
);

-- Source: V1__baseline.sql
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
  INDEX ix_survey_history_tenant_survey_time (tenant_id, survey_id, created_at),
  CONSTRAINT fk_survey_history_survey_tenant FOREIGN KEY (tenant_id,survey_id) REFERENCES site_survey(tenant_id,id),
  CONSTRAINT fk_survey_history_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id)
);

-- Source: V1__baseline.sql
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
  CONSTRAINT chk_survey_attachment_size CHECK (size_bytes > 0 AND size_bytes <= 10485760),
  CONSTRAINT fk_survey_attachment_survey_tenant FOREIGN KEY (tenant_id,survey_id) REFERENCES site_survey(tenant_id,id),
  CONSTRAINT fk_survey_attachment_uploader_tenant FOREIGN KEY (tenant_id,uploaded_by) REFERENCES app_user(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE quotation_item(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  quotation_id BINARY(16),
  product_id BINARY(16),
  package_id BINARY(16),
  description VARCHAR(255),
  quantity INT,
  unit_price DECIMAL(19,2),
  tax_percent DECIMAL(5,2),
  line_total DECIMAL(19,2),
  discount_amount DECIMAL(19,4) NULL,
  taxable_amount DECIMAL(19,4) NULL,
  tax_amount DECIMAL(19,4) NULL,
  FOREIGN KEY(quotation_id) REFERENCES quotation(id),
  CONSTRAINT fk_quotation_item_quote_tenant FOREIGN KEY (tenant_id,quotation_id) REFERENCES quotation(tenant_id,id),
  CONSTRAINT fk_quotation_item_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id),
  CONSTRAINT fk_quotation_item_package_tenant FOREIGN KEY (tenant_id,package_id) REFERENCES solar_package(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE customer_order(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  quotation_id BINARY(16) UNIQUE,
  customer_id BINARY(16),
  order_number VARCHAR(60),
  status VARCHAR(40),
  total DECIMAL(19,2),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  subtotal DECIMAL(19,4) NULL,
  discount_percent DECIMAL(7,4) NOT NULL DEFAULT 0,
  discount_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
  tax_total DECIMAL(19,4) NULL,
  branch_id BINARY(16) NULL,
  KEY ix_order_tenant_status (tenant_id,status),
  CONSTRAINT fk_customer_order_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id),
  KEY ix_customer_order_tenant_branch (tenant_id,branch_id),
  CONSTRAINT uq_customer_order_tenant_id UNIQUE (tenant_id,id),
  CONSTRAINT fk_customer_order_quote_tenant FOREIGN KEY (tenant_id,quotation_id) REFERENCES quotation(tenant_id,id),
  CONSTRAINT fk_customer_order_customer_tenant FOREIGN KEY (tenant_id,customer_id) REFERENCES customer_profile(tenant_id,id),
  KEY ix_customer_order_tenant_customer (tenant_id,customer_id)
);

-- Source: V1__baseline.sql
CREATE TABLE order_item(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  order_id BINARY(16),
  description VARCHAR(255),
  quantity INT,
  unit_price DECIMAL(19,2),
  line_total DECIMAL(19,2),
  product_id BINARY(16) NULL,
  package_id BINARY(16) NULL,
  tax_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
  discount_amount DECIMAL(19,4) NULL,
  taxable_amount DECIMAL(19,4) NULL,
  tax_amount DECIMAL(19,4) NULL,
  FOREIGN KEY(order_id) REFERENCES customer_order(id),
  KEY ix_order_item_order_product (tenant_id,order_id,product_id),
  CONSTRAINT fk_order_item_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
  CONSTRAINT fk_order_item_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id),
  CONSTRAINT fk_order_item_package_tenant FOREIGN KEY (tenant_id,package_id) REFERENCES solar_package(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE order_status_history(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  order_id BINARY(16) NOT NULL,
  old_status VARCHAR(40),
  new_status VARCHAR(40) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  actor_id BINARY(16) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  FOREIGN KEY(order_id) REFERENCES customer_order(id),
  INDEX ix_order_history_tenant_order_time(tenant_id,order_id,created_at),
  CONSTRAINT fk_order_history_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
  CONSTRAINT fk_order_history_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE inventory_reservation(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  order_id BINARY(16) NOT NULL,
  warehouse_id BINARY(16) NOT NULL,
  product_id BINARY(16) NOT NULL,
  quantity DECIMAL(19,4) NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  FOREIGN KEY(order_id) REFERENCES customer_order(id),
  FOREIGN KEY(warehouse_id) REFERENCES warehouse(id),
  FOREIGN KEY(product_id) REFERENCES product(id),
  CHECK(quantity>0),
  INDEX ix_reservation_tenant_order_status(tenant_id,order_id,status),
  CONSTRAINT fk_inventory_reservation_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
  CONSTRAINT fk_inventory_reservation_warehouse_tenant FOREIGN KEY (tenant_id,warehouse_id) REFERENCES warehouse(tenant_id,id),
  CONSTRAINT fk_inventory_reservation_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE installation_job(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  order_id BINARY(16) NOT NULL,
  customer_id BINARY(16) NOT NULL,
  site_address VARCHAR(700) NOT NULL,
  scheduled_at TIMESTAMP(6) NOT NULL,
  assigned_engineer_id BINARY(16) NOT NULL,
  team_name VARCHAR(150),
  capacity_kw DECIMAL(10,2),
  notes TEXT,
  status VARCHAR(30) NOT NULL,
  customer_signed_off BOOLEAN NOT NULL DEFAULT FALSE,
  completed_at TIMESTAMP(6) NULL,
  commissioned_at TIMESTAMP(6) NULL,
  commissioning_notes TEXT,
  reschedule_reason VARCHAR(500),
  created_at TIMESTAMP(6) NOT NULL,
  team_id BINARY(16) NULL,
  approved_survey_id BINARY(16) NULL,
  branch_id BINARY(16) NULL,
  FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  FOREIGN KEY(order_id) REFERENCES customer_order(id),
  FOREIGN KEY(customer_id) REFERENCES customer_profile(id),
  FOREIGN KEY(assigned_engineer_id) REFERENCES app_user(id),
  UNIQUE(tenant_id,order_id),
  INDEX ix_install_tenant_schedule(tenant_id,scheduled_at,status),
  CONSTRAINT fk_install_job_team FOREIGN KEY(team_id) REFERENCES installation_team(id),
  CONSTRAINT fk_installation_approved_survey FOREIGN KEY (approved_survey_id) REFERENCES site_survey(id),
  KEY ix_installation_tenant_survey (tenant_id,approved_survey_id),
  CONSTRAINT fk_installation_job_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id),
  KEY ix_installation_job_tenant_branch (tenant_id,branch_id),
  CONSTRAINT uq_installation_job_tenant_id UNIQUE (tenant_id,id),
  CONSTRAINT fk_install_job_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
  CONSTRAINT fk_install_job_customer_tenant FOREIGN KEY (tenant_id,customer_id) REFERENCES customer_profile(tenant_id,id),
  CONSTRAINT fk_install_job_engineer_tenant FOREIGN KEY (tenant_id,assigned_engineer_id) REFERENCES app_user(tenant_id,id),
  CONSTRAINT fk_install_job_team_tenant FOREIGN KEY (tenant_id,team_id) REFERENCES installation_team(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE invoice(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  order_id BINARY(16) NOT NULL,
  invoice_number VARCHAR(80) NOT NULL,
  fiscal_year VARCHAR(9) NOT NULL,
  status VARCHAR(24) NOT NULL,
  customer_name VARCHAR(150) NOT NULL,
  customer_email VARCHAR(190),
  customer_address VARCHAR(700),
  customer_gstin VARCHAR(15),
  vendor_name VARCHAR(150) NOT NULL,
  vendor_gstin VARCHAR(15),
  currency CHAR(3) NOT NULL DEFAULT 'INR',
  subtotal DECIMAL(19,4) NOT NULL,
  tax_total DECIMAL(19,4) NOT NULL,
  grand_total DECIMAL(19,4) NOT NULL,
  tax_treatment VARCHAR(20) NULL,
  due_date DATE,
  issued_at TIMESTAMP(6) NOT NULL,
  created_by BINARY(16) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  void_reason VARCHAR(500),
  cgst_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
  sgst_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
  igst_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
  branch_id BINARY(16) NULL,
  UNIQUE KEY uq_invoice_tenant_number(tenant_id,invoice_number),
  UNIQUE KEY uq_invoice_order(tenant_id,order_id),
  CONSTRAINT fk_invoice_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_invoice_order FOREIGN KEY(order_id) REFERENCES customer_order(id),
  INDEX ix_invoice_tenant_status_date(tenant_id,status,issued_at),
  CONSTRAINT fk_invoice_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id),
  KEY ix_invoice_tenant_branch (tenant_id,branch_id),
  CONSTRAINT uq_invoice_tenant_id UNIQUE (tenant_id,id),
  CONSTRAINT fk_invoice_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
  CONSTRAINT fk_invoice_created_by_tenant FOREIGN KEY (tenant_id,created_by) REFERENCES app_user(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE installation_checklist_item(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  installation_id BINARY(16) NOT NULL,
  code VARCHAR(60) NOT NULL,
  title VARCHAR(200) NOT NULL,
  mandatory BOOLEAN NOT NULL DEFAULT TRUE,
  completed BOOLEAN NOT NULL DEFAULT FALSE,
  evidence_url VARCHAR(1000),
  notes VARCHAR(2000),
  completed_by BINARY(16) NULL,
  completed_at TIMESTAMP(6) NULL,
  FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  FOREIGN KEY(installation_id) REFERENCES installation_job(id),
  FOREIGN KEY(completed_by) REFERENCES app_user(id),
  UNIQUE(installation_id,code),
  INDEX ix_checklist_tenant_installation(tenant_id,installation_id),
  CONSTRAINT fk_checklist_installation_tenant FOREIGN KEY (tenant_id,installation_id) REFERENCES installation_job(tenant_id,id),
  CONSTRAINT fk_checklist_completed_by_tenant FOREIGN KEY (tenant_id,completed_by) REFERENCES app_user(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE warranty(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  order_id BINARY(16) NOT NULL,
  installation_id BINARY(16) NOT NULL,
  customer_id BINARY(16) NOT NULL,
  product_id BINARY(16) NOT NULL,
  serial_number VARCHAR(150) NULL,
  quantity INT NOT NULL DEFAULT 1,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  terms VARCHAR(2000),
  status VARCHAR(20) NOT NULL,
  activated_at TIMESTAMP(6) NOT NULL,
  FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  FOREIGN KEY(order_id) REFERENCES customer_order(id),
  FOREIGN KEY(installation_id) REFERENCES installation_job(id),
  FOREIGN KEY(customer_id) REFERENCES customer_profile(id),
  FOREIGN KEY(product_id) REFERENCES product(id),
  UNIQUE(tenant_id,serial_number),
  INDEX ix_warranty_tenant_customer(tenant_id,customer_id,status),
  KEY ix_warranty_status_end_date (status, end_date),
  CONSTRAINT uq_warranty_tenant_id UNIQUE (tenant_id,id),
  CONSTRAINT fk_warranty_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
  CONSTRAINT fk_warranty_installation_tenant FOREIGN KEY (tenant_id,installation_id) REFERENCES installation_job(tenant_id,id),
  CONSTRAINT fk_warranty_customer_tenant FOREIGN KEY (tenant_id,customer_id) REFERENCES customer_profile(tenant_id,id),
  CONSTRAINT fk_warranty_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE invoice_item(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  invoice_id BINARY(16) NOT NULL,
  product_id BINARY(16),
  description VARCHAR(255) NOT NULL,
  hsn_code VARCHAR(40),
  quantity INT NOT NULL,
  unit_price DECIMAL(19,4) NOT NULL,
  tax_percent DECIMAL(7,4) NOT NULL,
  taxable_amount DECIMAL(19,4) NOT NULL,
  tax_amount DECIMAL(19,4) NOT NULL,
  line_total DECIMAL(19,4) NOT NULL,
  CONSTRAINT fk_invoice_item_invoice FOREIGN KEY(invoice_id) REFERENCES invoice(id),
  CONSTRAINT fk_invoice_item_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  INDEX ix_invoice_item_tenant_invoice(tenant_id,invoice_id),
  CONSTRAINT fk_invoice_item_invoice_tenant FOREIGN KEY (tenant_id,invoice_id) REFERENCES invoice(tenant_id,id),
  CONSTRAINT fk_invoice_item_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id),
  KEY ix_invoice_item_tenant_product (tenant_id,product_id)
);

-- Source: V1__baseline.sql
CREATE TABLE payment(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  invoice_id BINARY(16) NOT NULL,
  order_id BINARY(16) NOT NULL,
  amount DECIMAL(19,4) NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'INR',
  method VARCHAR(30) NOT NULL,
  source VARCHAR(20) NOT NULL,
  status VARCHAR(24) NOT NULL,
  reference VARCHAR(150),
  provider VARCHAR(60),
  provider_reference VARCHAR(150),
  paid_at TIMESTAMP(6) NOT NULL,
  recorded_by BINARY(16) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  idempotency_key VARCHAR(160) NULL,
  checkout_url VARCHAR(1000) NULL,
  CONSTRAINT fk_payment_invoice FOREIGN KEY(invoice_id) REFERENCES invoice(id),
  CONSTRAINT fk_payment_order FOREIGN KEY(order_id) REFERENCES customer_order(id),
  CONSTRAINT fk_payment_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  UNIQUE KEY uq_payment_provider_ref(provider,provider_reference),
  INDEX ix_payment_tenant_invoice(tenant_id,invoice_id,status),
  UNIQUE KEY uq_payment_tenant_idempotency (tenant_id, idempotency_key),
  CONSTRAINT uq_payment_tenant_id UNIQUE (tenant_id,id),
  CONSTRAINT fk_payment_invoice_tenant FOREIGN KEY (tenant_id,invoice_id) REFERENCES invoice(tenant_id,id),
  CONSTRAINT fk_payment_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
  CONSTRAINT fk_payment_recorded_by_tenant FOREIGN KEY (tenant_id,recorded_by) REFERENCES app_user(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE service_request(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  ticket_number VARCHAR(80) NOT NULL,
  customer_id BINARY(16) NOT NULL,
  installation_id BINARY(16),
  product_id BINARY(16),
  serial_number VARCHAR(150),
  category VARCHAR(50) NOT NULL,
  priority VARCHAR(20) NOT NULL,
  description VARCHAR(4000) NOT NULL,
  warranty_status VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL,
  assigned_engineer_id BINARY(16),
  response_due_at TIMESTAMP(6),
  resolution_due_at TIMESTAMP(6),
  first_responded_at TIMESTAMP(6),
  resolved_at TIMESTAMP(6),
  customer_confirmed_at TIMESTAMP(6),
  parts_consumed_json JSON NULL,
  closed_at TIMESTAMP(6),
  paused_seconds BIGINT NOT NULL DEFAULT 0,
  sla_paused_at TIMESTAMP(6),
  response_breached_at TIMESTAMP(6),
  resolution_breached_at TIMESTAMP(6),
  resolution_notes VARCHAR(2000),
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  branch_id BINARY(16) NULL,
  UNIQUE KEY uq_service_ticket_tenant_number(tenant_id,ticket_number),
  CONSTRAINT fk_service_request_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_service_request_customer FOREIGN KEY(customer_id) REFERENCES customer_profile(id),
  CONSTRAINT fk_service_request_installation FOREIGN KEY(installation_id) REFERENCES installation_job(id),
  CONSTRAINT fk_service_request_product FOREIGN KEY(product_id) REFERENCES product(id),
  CONSTRAINT fk_service_request_engineer FOREIGN KEY(assigned_engineer_id) REFERENCES app_user(id),
  INDEX ix_service_tenant_status_created(tenant_id,status,created_at),
  INDEX ix_service_tenant_assignee_status(tenant_id,assigned_engineer_id,status),
  CONSTRAINT fk_service_request_branch FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id),
  KEY ix_service_request_tenant_branch (tenant_id,branch_id),
  CONSTRAINT uq_service_request_tenant_id UNIQUE (tenant_id,id),
  CONSTRAINT fk_service_request_customer_tenant FOREIGN KEY (tenant_id,customer_id) REFERENCES customer_profile(tenant_id,id),
  CONSTRAINT fk_service_request_installation_tenant FOREIGN KEY (tenant_id,installation_id) REFERENCES installation_job(tenant_id,id),
  CONSTRAINT fk_service_request_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id),
  CONSTRAINT fk_service_request_engineer_tenant FOREIGN KEY (tenant_id,assigned_engineer_id) REFERENCES app_user(tenant_id,id),
  KEY ix_service_request_tenant_customer (tenant_id,customer_id)
);

-- Source: V1__baseline.sql
CREATE TABLE credit_note(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  invoice_id BINARY(16) NOT NULL,
  credit_note_number VARCHAR(100) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  amount DECIMAL(19,4) NOT NULL,
  status VARCHAR(24) NOT NULL,
  created_by BINARY(16) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  UNIQUE KEY uq_credit_note_tenant_number(tenant_id,credit_note_number),
  CONSTRAINT fk_credit_note_invoice FOREIGN KEY(invoice_id) REFERENCES invoice(id),
  CONSTRAINT fk_credit_note_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  INDEX ix_credit_note_tenant_invoice(tenant_id,invoice_id),
  CONSTRAINT fk_credit_note_invoice_tenant FOREIGN KEY (tenant_id,invoice_id) REFERENCES invoice(tenant_id,id),
  CONSTRAINT fk_credit_note_created_by_tenant FOREIGN KEY (tenant_id,created_by) REFERENCES app_user(tenant_id,id)
);

-- Source: V1__baseline.sql
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
  INDEX ix_install_schedule_history (tenant_id,installation_id,created_at),
  CONSTRAINT fk_install_schedule_job_tenant FOREIGN KEY (tenant_id,installation_id) REFERENCES installation_job(tenant_id,id),
  CONSTRAINT fk_install_schedule_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE inventory_transaction(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  warehouse_id BINARY(16) NOT NULL,
  product_id BINARY(16) NOT NULL,
  order_id BINARY(16) NULL,
  installation_id BINARY(16) NULL,
  type VARCHAR(40) NOT NULL,
  quantity_delta DECIMAL(19,4) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  actor_id BINARY(16) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  reserved_delta DECIMAL(19,4) NOT NULL DEFAULT 0,
  damaged_delta DECIMAL(19,4) NOT NULL DEFAULT 0,
  service_request_id BINARY(16) NULL,
  transaction_reference VARCHAR(50) NOT NULL,
  reversal_of_transaction_id BINARY(16) NULL,
  FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  FOREIGN KEY(warehouse_id) REFERENCES warehouse(id),
  FOREIGN KEY(product_id) REFERENCES product(id),
  FOREIGN KEY(order_id) REFERENCES customer_order(id),
  INDEX ix_inv_tx_tenant_time(tenant_id,created_at),
  INDEX ix_inv_tx_tenant_product(tenant_id,product_id),
  KEY ix_inventory_tx_service_request (tenant_id,service_request_id),
  UNIQUE KEY uq_inventory_transaction_reference (tenant_id, transaction_reference),
  UNIQUE KEY uq_inventory_transaction_reversal (tenant_id, reversal_of_transaction_id),
  CONSTRAINT fk_inventory_tx_warehouse_tenant FOREIGN KEY (tenant_id,warehouse_id) REFERENCES warehouse(tenant_id,id),
  CONSTRAINT fk_inventory_tx_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id),
  CONSTRAINT fk_inventory_tx_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
  CONSTRAINT fk_inventory_tx_installation_tenant FOREIGN KEY (tenant_id,installation_id) REFERENCES installation_job(tenant_id,id),
  CONSTRAINT fk_inventory_tx_service_tenant FOREIGN KEY (tenant_id,service_request_id) REFERENCES service_request(tenant_id,id),
  CONSTRAINT fk_inventory_tx_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE payment_refund(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  payment_id BINARY(16) NOT NULL,
  amount DECIMAL(19,4) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  status VARCHAR(24) NOT NULL,
  recorded_by BINARY(16) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_refund_payment FOREIGN KEY(payment_id) REFERENCES payment(id),
  CONSTRAINT fk_refund_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  INDEX ix_refund_tenant_payment(tenant_id,payment_id),
  CONSTRAINT fk_refund_payment_tenant FOREIGN KEY (tenant_id,payment_id) REFERENCES payment(tenant_id,id),
  CONSTRAINT fk_refund_recorded_by_tenant FOREIGN KEY (tenant_id,recorded_by) REFERENCES app_user(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE service_visit(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  service_request_id BINARY(16) NOT NULL,
  engineer_id BINARY(16) NOT NULL,
  scheduled_at TIMESTAMP(6) NOT NULL,
  started_at TIMESTAMP(6),
  completed_at TIMESTAMP(6),
  engineer_notes VARCHAR(4000),
  resolution VARCHAR(2000),
  evidence_url VARCHAR(1000),
  customer_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
  customer_confirmed_at TIMESTAMP(6),
  parts_consumed_json JSON NULL,
  CONSTRAINT fk_service_visit_request FOREIGN KEY(service_request_id) REFERENCES service_request(id),
  CONSTRAINT fk_service_visit_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_service_visit_engineer FOREIGN KEY(engineer_id) REFERENCES app_user(id),
  INDEX ix_service_visit_tenant_schedule(tenant_id,scheduled_at),
  CONSTRAINT fk_service_visit_ticket_tenant FOREIGN KEY (tenant_id,service_request_id) REFERENCES service_request(tenant_id,id),
  CONSTRAINT fk_service_visit_engineer_tenant FOREIGN KEY (tenant_id,engineer_id) REFERENCES app_user(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE service_event(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  service_request_id BINARY(16) NOT NULL,
  actor_id BINARY(16) NULL,
  event_type VARCHAR(40) NOT NULL,
  old_status VARCHAR(30),
  new_status VARCHAR(30),
  reason VARCHAR(2000),
  occurred_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_service_event_request FOREIGN KEY(service_request_id) REFERENCES service_request(id),
  CONSTRAINT fk_service_event_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  INDEX ix_service_event_tenant_ticket_time(tenant_id,service_request_id,occurred_at),
  CONSTRAINT fk_service_event_ticket_tenant FOREIGN KEY (tenant_id,service_request_id) REFERENCES service_request(tenant_id,id),
  CONSTRAINT fk_service_event_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id)
);

-- Source: V1__baseline.sql
CREATE TABLE service_attachment(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  service_request_id BINARY(16) NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  storage_key VARCHAR(120) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  size_bytes BIGINT NOT NULL,
  uploaded_by BINARY(16) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_service_attachment_ticket FOREIGN KEY(service_request_id) REFERENCES service_request(id),
  CONSTRAINT fk_service_attachment_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_service_attachment_user FOREIGN KEY(uploaded_by) REFERENCES app_user(id),
  INDEX ix_service_attachment_tenant_ticket(tenant_id,service_request_id,created_at),
  CONSTRAINT fk_service_attachment_ticket_tenant FOREIGN KEY (tenant_id,service_request_id) REFERENCES service_request(tenant_id,id),
  CONSTRAINT fk_service_attachment_uploader_tenant FOREIGN KEY (tenant_id,uploaded_by) REFERENCES app_user(tenant_id,id)
);

-- Source: V1__baseline.sql
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
  INDEX ix_warranty_claim_warranty (tenant_id,warranty_id),
  CONSTRAINT uq_warranty_claim_tenant_id UNIQUE (tenant_id,id),
  CONSTRAINT fk_warranty_claim_warranty_tenant FOREIGN KEY (tenant_id,warranty_id) REFERENCES warranty(tenant_id,id),
  CONSTRAINT fk_warranty_claim_customer_tenant FOREIGN KEY (tenant_id,customer_id) REFERENCES customer_profile(tenant_id,id),
  CONSTRAINT fk_warranty_claim_reviewer_tenant FOREIGN KEY (tenant_id,reviewed_by) REFERENCES app_user(tenant_id,id),
  KEY ix_warranty_claim_tenant_customer (tenant_id,customer_id)
);

-- Source: V1__baseline.sql
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
  INDEX ix_service_comment_ticket (tenant_id,service_request_id,created_at),
  CONSTRAINT fk_service_comment_ticket_tenant FOREIGN KEY (tenant_id,service_request_id) REFERENCES service_request(tenant_id,id),
  CONSTRAINT fk_service_comment_author_tenant FOREIGN KEY (tenant_id,author_id) REFERENCES app_user(tenant_id,id)
);

-- Source: V1__baseline.sql
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
  CONSTRAINT chk_warranty_attachment_size CHECK (size_bytes > 0 AND size_bytes <= 10485760),
  CONSTRAINT fk_warranty_attachment_claim_tenant FOREIGN KEY (tenant_id,claim_id) REFERENCES warranty_claim(tenant_id,id),
  CONSTRAINT fk_warranty_attachment_uploader_tenant FOREIGN KEY (tenant_id,uploaded_by) REFERENCES app_user(tenant_id,id)
);

-- Source: V3__phase3_order_policy_and_tenant_lifecycle.sql
CREATE TABLE order_transition_policy (
    id BINARY(16) NOT NULL PRIMARY KEY,
    tenant_id BINARY(16) NULL,
    from_status VARCHAR(40) NOT NULL,
    to_status VARCHAR(40) NOT NULL,
    required_permission VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_order_transition_policy_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT uq_order_transition_policy_tenant_edge UNIQUE (tenant_id, from_status, to_status),
    INDEX ix_order_transition_policy_tenant (tenant_id, from_status)
);

-- Source: V5__phase4_finance_provider_reconciliation.sql
CREATE TABLE payment_reconciliation_report(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  provider VARCHAR(60) NOT NULL,
  report_reference VARCHAR(160) NOT NULL,
  currency CHAR(3) NOT NULL,
  reported_total DECIMAL(19,4) NOT NULL,
  matched_total DECIMAL(19,4) NOT NULL DEFAULT 0,
  mismatch_count INT NOT NULL DEFAULT 0,
  status VARCHAR(24) NOT NULL,
  payload_hash VARCHAR(64) NOT NULL,
  created_by BINARY(16) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  processed_at TIMESTAMP(6) NULL,
  CONSTRAINT fk_payment_reconciliation_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_payment_reconciliation_created_by FOREIGN KEY(tenant_id,created_by) REFERENCES app_user(tenant_id,id),
  CONSTRAINT uq_payment_reconciliation_report UNIQUE(tenant_id,provider,report_reference),
  CONSTRAINT uq_payment_reconciliation_tenant_id UNIQUE(tenant_id,id),
  INDEX ix_payment_reconciliation_tenant_created(tenant_id,created_at)
);

-- Source: V5__phase4_finance_provider_reconciliation.sql
CREATE TABLE payment_reconciliation_line(
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  report_id BINARY(16) NOT NULL,
  provider_reference VARCHAR(150) NOT NULL,
  amount DECIMAL(19,4) NOT NULL,
  currency CHAR(3) NOT NULL,
  outcome VARCHAR(32) NOT NULL,
  payment_id BINARY(16) NULL,
  details VARCHAR(500) NULL,
  CONSTRAINT fk_payment_reconciliation_line_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_payment_reconciliation_line_report FOREIGN KEY(tenant_id,report_id) REFERENCES payment_reconciliation_report(tenant_id,id),
  CONSTRAINT fk_payment_reconciliation_line_payment FOREIGN KEY(tenant_id,payment_id) REFERENCES payment(tenant_id,id),
  INDEX ix_payment_reconciliation_line_tenant(tenant_id,provider_reference),
  INDEX ix_payment_reconciliation_line_report(tenant_id,report_id)
);

-- Source: V6__phase5_cms_content_revisions.sql
-- Phase 5 CMS content and immutable revision history.
-- Additive only; no existing production rows or historical content are backfilled.
CREATE TABLE cms_content (
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  content_type VARCHAR(24) NOT NULL,
  title VARCHAR(180) NOT NULL,
  slug VARCHAR(180) NOT NULL,
  status VARCHAR(20) NOT NULL,
  current_revision_id BINARY(16) NULL,
  published_revision_id BINARY(16) NULL,
  published_at TIMESTAMP(6) NULL,
  created_by BINARY(16) NOT NULL,
  updated_by BINARY(16) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_cms_content_tenant_id UNIQUE (tenant_id,id),
  CONSTRAINT uq_cms_content_tenant_type_slug UNIQUE (tenant_id,content_type,slug),
  CONSTRAINT fk_cms_content_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_cms_content_created_by FOREIGN KEY (tenant_id,created_by) REFERENCES app_user(tenant_id,id),
  CONSTRAINT fk_cms_content_updated_by FOREIGN KEY (tenant_id,updated_by) REFERENCES app_user(tenant_id,id),
  INDEX ix_cms_content_public (tenant_id,content_type,status,published_at),
  INDEX ix_cms_content_updated (tenant_id,updated_at)
);

-- Source: V6__phase5_cms_content_revisions.sql
CREATE TABLE cms_content_revision (
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  content_id BINARY(16) NOT NULL,
  revision_number INT NOT NULL,
  title VARCHAR(180) NOT NULL,
  body LONGTEXT NOT NULL,
  seo_title VARCHAR(180) NULL,
  seo_description VARCHAR(320) NULL,
  social_image_url VARCHAR(1024) NULL,
  created_by BINARY(16) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uq_cms_revision_tenant_id UNIQUE (tenant_id,id),
  CONSTRAINT uq_cms_revision_number UNIQUE (tenant_id,content_id,revision_number),
  CONSTRAINT fk_cms_revision_content FOREIGN KEY (tenant_id,content_id) REFERENCES cms_content(tenant_id,id),
  CONSTRAINT fk_cms_revision_created_by FOREIGN KEY (tenant_id,created_by) REFERENCES app_user(tenant_id,id),
  INDEX ix_cms_revision_content (tenant_id,content_id,revision_number)
);

-- Source: V7__phase5_user_notification_inbox.sql
-- User-facing inbox projection; separate from provider delivery attempts in notification_delivery.
CREATE TABLE user_notification (
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NULL,
  user_id BINARY(16) NOT NULL,
  event_type VARCHAR(60) NOT NULL,
  title VARCHAR(255) NOT NULL,
  body VARCHAR(4000) NOT NULL,
  dedupe_key VARCHAR(64) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  read_at TIMESTAMP(6) NULL,
  CONSTRAINT uq_user_notification_dedupe UNIQUE (dedupe_key),
  CONSTRAINT fk_user_notification_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_user_notification_user FOREIGN KEY (tenant_id,user_id) REFERENCES app_user(tenant_id,id),
  INDEX ix_user_notification_inbox (tenant_id,user_id,created_at),
  INDEX ix_user_notification_unread (tenant_id,user_id,read_at,created_at)
);

-- Source: V10__phase5_typed_platform_settings.sql
CREATE TABLE platform_setting (
  setting_key VARCHAR(60) NOT NULL PRIMARY KEY,
  value_type VARCHAR(20) NOT NULL,
  value_text VARCHAR(500) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  updated_by BINARY(16) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_platform_setting_actor FOREIGN KEY (updated_by) REFERENCES app_user(id),
  CONSTRAINT chk_platform_setting_type CHECK (value_type IN ('STRING','EMAIL','PHONE','CURRENCY','TIME_ZONE','LANGUAGE')),
  CONSTRAINT chk_platform_setting_version CHECK (version >= 0)
);

-- Source: V12__phase6_notification_outbox_preferences_provider_events.sql
CREATE TABLE notification_outbox (
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  delivery_id BINARY(16) NOT NULL,
  status VARCHAR(20) NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMP(6) NULL,
  last_error VARCHAR(1000) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uq_notification_outbox_delivery UNIQUE (delivery_id),
  CONSTRAINT fk_notification_outbox_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_notification_outbox_delivery FOREIGN KEY (delivery_id) REFERENCES notification_delivery(id),
  INDEX ix_notification_outbox_dispatch (status,next_attempt_at,created_at),
  INDEX ix_notification_outbox_tenant (tenant_id,status,created_at)
);

-- Source: V12__phase6_notification_outbox_preferences_provider_events.sql
CREATE TABLE notification_preference (
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  channel VARCHAR(20) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  consented BOOLEAN NOT NULL DEFAULT FALSE,
  consent_updated_at TIMESTAMP(6) NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uq_notification_preference UNIQUE (tenant_id,user_id,channel),
  CONSTRAINT fk_notification_preference_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_notification_preference_user FOREIGN KEY (tenant_id,user_id) REFERENCES app_user(tenant_id,id),
  INDEX ix_notification_preference_user (tenant_id,user_id)
);

-- Source: V12__phase6_notification_outbox_preferences_provider_events.sql
CREATE TABLE notification_provider_event (
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  delivery_id BINARY(16) NOT NULL,
  provider_code VARCHAR(40) NOT NULL,
  provider_event_id VARCHAR(160) NOT NULL,
  event_type VARCHAR(40) NOT NULL,
  delivery_status VARCHAR(20) NOT NULL,
  payload_hash VARCHAR(64) NOT NULL,
  received_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uq_notification_provider_event UNIQUE (provider_code,provider_event_id),
  CONSTRAINT fk_notification_provider_event_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_notification_provider_event_delivery FOREIGN KEY (delivery_id) REFERENCES notification_delivery(id),
  INDEX ix_notification_provider_event_delivery (tenant_id,delivery_id,received_at)
);



-- COLUMN, CHECK, FOREIGN KEY AND TABLE CONSTRAINT EXTENSIONS

-- Source: V2__phase3_reservation_expiry.sql
-- Phase 3: inventory reservation expiry and immutable release metadata.
-- Existing active reservations receive a fresh 24-hour grace window on upgrade.
ALTER TABLE inventory_reservation
    ADD COLUMN expires_at TIMESTAMP(6) NULL,
    ADD COLUMN pre_reservation_order_status VARCHAR(40) NULL,
    ADD COLUMN released_at TIMESTAMP(6) NULL,
    ADD COLUMN release_reason VARCHAR(500) NULL,
    ADD COLUMN released_by BINARY(16) NULL,
    ADD KEY ix_reservation_expiry (tenant_id, status, expires_at);

-- Source: V2__phase3_reservation_expiry.sql
-- Scheduler-originated order history entries have no human actor.
ALTER TABLE order_status_history
    MODIFY COLUMN actor_id BINARY(16) NULL,
    ADD COLUMN actor_type VARCHAR(20) NOT NULL DEFAULT 'USER';

-- Source: V3__phase3_order_policy_and_tenant_lifecycle.sql
-- Phase 3: tenant lifecycle and persisted, tenant-overridable order transition policy.
ALTER TABLE tenant
    ADD COLUMN verified_at TIMESTAMP(6) NULL,
    ADD COLUMN verified_by BINARY(16) NULL,
    ADD COLUMN activated_at TIMESTAMP(6) NULL,
    ADD COLUMN activated_by BINARY(16) NULL,
    ADD COLUMN suspended_at TIMESTAMP(6) NULL,
    ADD COLUMN suspended_by BINARY(16) NULL,
    ADD COLUMN closed_at TIMESTAMP(6) NULL,
    ADD COLUMN closed_by BINARY(16) NULL,
    ADD COLUMN lifecycle_reason VARCHAR(500) NULL;

-- Source: V5__phase4_finance_provider_reconciliation.sql
-- Phase 4 finance/provider lifecycle: clean-install schema extension.
-- The application is not deployed; existing event rows do not require a data backfill.
ALTER TABLE payment
    MODIFY COLUMN paid_at TIMESTAMP(6) NULL;

-- Source: V5__phase4_finance_provider_reconciliation.sql
ALTER TABLE payment_webhook_event
    ADD COLUMN tenant_id BINARY(16) NOT NULL AFTER id,
    ADD COLUMN provider_reference VARCHAR(150) NOT NULL AFTER provider,
    ADD COLUMN event_type VARCHAR(32) NOT NULL AFTER processing_status,
    ADD COLUMN provider_refund_reference VARCHAR(150) NULL AFTER event_type,
    DROP INDEX uq_payment_webhook_provider_event,
    ADD CONSTRAINT fk_payment_webhook_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
    ADD UNIQUE KEY uq_payment_webhook_tenant_provider_event(tenant_id, provider, event_id),
    ADD INDEX ix_payment_webhook_tenant_received(tenant_id, received_at);

-- Source: V5__phase4_finance_provider_reconciliation.sql
ALTER TABLE payment_refund
    ADD COLUMN provider VARCHAR(60) NULL AFTER recorded_by,
    ADD COLUMN provider_refund_reference VARCHAR(150) NULL AFTER provider,
    ADD COLUMN idempotency_key VARCHAR(160) NULL AFTER provider_refund_reference,
    ADD COLUMN processed_at TIMESTAMP(6) NULL AFTER idempotency_key,
    ADD UNIQUE KEY uq_payment_refund_tenant_idempotency(tenant_id, payment_id, idempotency_key),
    ADD UNIQUE KEY uq_payment_refund_provider_reference(tenant_id, provider, provider_refund_reference);

-- Source: V6__phase5_cms_content_revisions.sql
ALTER TABLE cms_content
  ADD CONSTRAINT fk_cms_content_current_revision
    FOREIGN KEY (tenant_id,current_revision_id) REFERENCES cms_content_revision(tenant_id,id),
  ADD CONSTRAINT fk_cms_content_published_revision
    FOREIGN KEY (tenant_id,published_revision_id) REFERENCES cms_content_revision(tenant_id,id);

-- Source: V12__phase6_notification_outbox_preferences_provider_events.sql
ALTER TABLE notification_delivery DROP CHECK chk_notification_delivery_status;

-- Source: V12__phase6_notification_outbox_preferences_provider_events.sql
ALTER TABLE notification_delivery ADD CONSTRAINT chk_notification_delivery_status CHECK (status IN ('PENDING','DELIVERED','FAILED','DEAD_LETTER','SUPPRESSED'));

-- Source: V13__phase6_provider_accepted_delivery_status.sql
-- Messaging adapter acceptance is distinct from a delivery receipt callback.
ALTER TABLE notification_delivery DROP CHECK chk_notification_delivery_status;

-- Source: V13__phase6_provider_accepted_delivery_status.sql
ALTER TABLE notification_delivery
  ADD CONSTRAINT chk_notification_delivery_status
  CHECK (status IN ('PENDING','SENT','DELIVERED','FAILED','DEAD_LETTER','SUPPRESSED'));

-- Source: V14__phase6_enforce_tenant_bound_notification_references.sql
-- Prevent an outbox/provider event from referencing a delivery belonging to another tenant.
ALTER TABLE notification_delivery
  ADD UNIQUE KEY uq_notification_delivery_tenant_id (tenant_id, id);

-- Source: V14__phase6_enforce_tenant_bound_notification_references.sql
ALTER TABLE notification_outbox
  DROP FOREIGN KEY fk_notification_outbox_delivery,
  ADD CONSTRAINT fk_notification_outbox_delivery_tenant
    FOREIGN KEY (tenant_id, delivery_id)
    REFERENCES notification_delivery (tenant_id, id);

-- Source: V14__phase6_enforce_tenant_bound_notification_references.sql
ALTER TABLE notification_provider_event
  DROP FOREIGN KEY fk_notification_provider_event_delivery,
  ADD CONSTRAINT fk_notification_provider_event_delivery_tenant
    FOREIGN KEY (tenant_id, delivery_id)
    REFERENCES notification_delivery (tenant_id, id);



-- STANDALONE INDEXES

-- Source: V9__phase5_platform_analytics_indexes.sql
-- Support bounded cross-tenant aggregate queries without returning tenant-level rows.
CREATE INDEX ix_customer_order_created_at ON customer_order(created_at);

-- Source: V9__phase5_platform_analytics_indexes.sql
CREATE INDEX ix_tenant_subscription_status ON tenant_subscription(status);

-- Source: V11__phase5_operational_analytics_indexes.sql
-- Cover the tenant-scoped date and assignee predicates used by the Phase 5 operations reports.
CREATE INDEX ix_installation_tenant_assignee_schedule ON installation_job(tenant_id,assigned_engineer_id,scheduled_at);

-- Source: V11__phase5_operational_analytics_indexes.sql
CREATE INDEX ix_service_tenant_assignee_created ON service_request(tenant_id,assigned_engineer_id,created_at);

-- Source: V11__phase5_operational_analytics_indexes.sql
CREATE INDEX ix_invoice_tenant_created ON invoice(tenant_id,created_at);

-- Source: V11__phase5_operational_analytics_indexes.sql
CREATE INDEX ix_order_tenant_created ON customer_order(tenant_id,created_at);



-- TRIGGERS

-- Source: V1__baseline.sql
CREATE TRIGGER trg_audit_event_no_update BEFORE UPDATE ON audit_event
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='audit_event is append-only';

-- Source: V1__baseline.sql
CREATE TRIGGER trg_audit_event_no_delete BEFORE DELETE ON audit_event
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='audit_event is append-only';

-- Source: V1__baseline.sql
CREATE TRIGGER trg_subscription_assignment_history_no_update
BEFORE UPDATE ON subscription_assignment_history
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='subscription_assignment_history is append-only';

-- Source: V1__baseline.sql
CREATE TRIGGER trg_subscription_assignment_history_no_delete
BEFORE DELETE ON subscription_assignment_history
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='subscription_assignment_history is append-only';

-- Source: V2__phase3_reservation_expiry.sql
CREATE TRIGGER trg_order_status_history_no_update
BEFORE UPDATE ON order_status_history
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='order_status_history is append-only';

-- Source: V2__phase3_reservation_expiry.sql
CREATE TRIGGER trg_order_status_history_no_delete
BEFORE DELETE ON order_status_history
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='order_status_history is append-only';

-- Source: V6__phase5_cms_content_revisions.sql
-- Revision snapshots are immutable even if an application path is accidentally added later.
CREATE TRIGGER trg_cms_revision_no_update
BEFORE UPDATE ON cms_content_revision
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CMS content revisions are immutable';

-- Source: V6__phase5_cms_content_revisions.sql
CREATE TRIGGER trg_cms_revision_no_delete
BEFORE DELETE ON cms_content_revision
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CMS content revisions are immutable';



-- REFERENCE DATA AND SEED / INITIALIZATION STATEMENTS (original migration order)

-- Source: V1__baseline.sql
INSERT INTO permission (id, code)
SELECT UUID_TO_BIN(UUID()), 'USER_MANAGE'
WHERE EXISTS (
    SELECT 1 FROM role WHERE name = 'VENDOR_ADMIN' AND tenant_id IS NOT NULL
)
AND NOT EXISTS (
    SELECT 1 FROM permission WHERE code = 'USER_MANAGE'
);

-- Source: V1__baseline.sql
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

-- Source: V1__baseline.sql
INSERT INTO permission (id, code)
SELECT UUID_TO_BIN(UUID()), src.code FROM (
 SELECT 'PLATFORM_MANAGE' code UNION ALL SELECT 'CATALOG_MANAGE' UNION ALL SELECT 'CUSTOMER_MANAGE'
 UNION ALL SELECT 'LEAD_MANAGE' UNION ALL SELECT 'SURVEY_MANAGE' UNION ALL SELECT 'QUOTATION_MANAGE'
 UNION ALL SELECT 'DISCOUNT_APPROVE' UNION ALL SELECT 'ORDER_CREATE' UNION ALL SELECT 'USER_VIEW'
 UNION ALL SELECT 'USER_MANAGE' UNION ALL SELECT 'INVENTORY_MANAGE' UNION ALL SELECT 'SERVICE_MANAGE'
 UNION ALL SELECT 'FINANCE_MANAGE' UNION ALL SELECT 'REPORT_VIEW'
) src LEFT JOIN permission p ON p.code=src.code WHERE p.id IS NULL;

-- Source: V1__baseline.sql
INSERT INTO role (id,tenant_id,name)
SELECT UUID_TO_BIN(UUID()),t.id,r.name FROM tenant t CROSS JOIN (
 SELECT 'VENDOR_ADMIN' name UNION ALL SELECT 'SALES_EXECUTIVE' UNION ALL SELECT 'SALES_MANAGER'
 UNION ALL SELECT 'MANAGER' UNION ALL SELECT 'INVENTORY_MANAGER' UNION ALL SELECT 'SERVICE_ENGINEER'
 UNION ALL SELECT 'ACCOUNTANT' UNION ALL SELECT 'INSTALLER' UNION ALL SELECT 'OPERATIONS_MANAGER'
) r LEFT JOIN role existing ON existing.tenant_id=t.id AND existing.name=r.name WHERE existing.id IS NULL;

-- Source: V1__baseline.sql
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p
WHERE r.tenant_id IS NOT NULL AND r.name='VENDOR_ADMIN' AND p.code IN
('CATALOG_MANAGE','CUSTOMER_MANAGE','LEAD_MANAGE','SURVEY_MANAGE','QUOTATION_MANAGE','DISCOUNT_APPROVE','ORDER_CREATE','USER_VIEW','USER_MANAGE','INVENTORY_MANAGE','SERVICE_MANAGE','FINANCE_MANAGE','REPORT_VIEW');

-- Source: V1__baseline.sql
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p
WHERE r.tenant_id IS NULL AND r.name='PLATFORM_SUPER_ADMIN' AND p.code='PLATFORM_MANAGE';

-- Source: V1__baseline.sql
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p WHERE r.tenant_id IS NOT NULL AND (
(r.name='SALES_EXECUTIVE' AND p.code IN ('CUSTOMER_MANAGE','LEAD_MANAGE','SURVEY_MANAGE','QUOTATION_MANAGE','ORDER_CREATE')) OR
(r.name IN ('SALES_MANAGER','MANAGER') AND p.code IN ('CUSTOMER_MANAGE','LEAD_MANAGE','SURVEY_MANAGE','QUOTATION_MANAGE','DISCOUNT_APPROVE','ORDER_CREATE','REPORT_VIEW')) OR
(r.name='INVENTORY_MANAGER' AND p.code IN ('CATALOG_MANAGE','INVENTORY_MANAGE','ORDER_CREATE')) OR
(r.name IN ('SERVICE_ENGINEER','INSTALLER') AND p.code IN ('SURVEY_MANAGE','SERVICE_MANAGE')) OR
(r.name='ACCOUNTANT' AND p.code IN ('FINANCE_MANAGE','REPORT_VIEW')) OR
(r.name='OPERATIONS_MANAGER' AND p.code IN ('ORDER_CREATE','SURVEY_MANAGE','INVENTORY_MANAGE','SERVICE_MANAGE','REPORT_VIEW'))
);

-- Source: V1__baseline.sql
INSERT INTO product_category(id,tenant_id,name,description,active)
SELECT UUID_TO_BIN(UUID()),t.id,c.name,'Solar catalogue category',TRUE
FROM tenant t CROSS JOIN (
 SELECT 'Solar Panels' name UNION ALL SELECT 'Inverters' UNION ALL SELECT 'Batteries'
 UNION ALL SELECT 'Mounting Structures' UNION ALL SELECT 'Cables'
 UNION ALL SELECT 'AC/DC Distribution Boxes' UNION ALL SELECT 'Solar Accessories'
) c LEFT JOIN product_category existing ON existing.tenant_id=t.id AND existing.name=c.name
WHERE existing.id IS NULL;

-- Source: V1__baseline.sql
UPDATE product p
JOIN (SELECT tenant_id, MIN(id) category_id FROM product_category GROUP BY tenant_id) c
ON c.tenant_id=p.tenant_id
SET p.category_id=c.category_id
WHERE p.category_id IS NULL;

-- Source: V1__baseline.sql
INSERT INTO permission(id,code)
SELECT UUID_TO_BIN(UUID()),'CATALOG_VIEW'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='CATALOG_VIEW');

-- Source: V1__baseline.sql
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code='CATALOG_VIEW'
WHERE r.tenant_id IS NOT NULL AND r.name IN ('SALES_EXECUTIVE','SALES_MANAGER','MANAGER');

-- Source: V1__baseline.sql
INSERT INTO permission(id,code)
SELECT UUID_TO_BIN(UUID()),'DISCOUNT_POLICY_MANAGE'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='DISCOUNT_POLICY_MANAGE');

-- Source: V1__baseline.sql
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id
FROM role r JOIN permission p ON p.code='DISCOUNT_POLICY_MANAGE'
WHERE r.tenant_id IS NOT NULL AND r.name='VENDOR_ADMIN';

-- Source: V1__baseline.sql
INSERT INTO role(id,tenant_id,name)
SELECT UUID_TO_BIN(UUID()),NULL,'PLATFORM_SUPER_ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM role WHERE tenant_id IS NULL AND name='PLATFORM_SUPER_ADMIN');

-- Source: V1__baseline.sql
INSERT INTO role(id,tenant_id,name)
SELECT UUID_TO_BIN(UUID()),NULL,'CUSTOMER'
WHERE NOT EXISTS (SELECT 1 FROM role WHERE tenant_id IS NULL AND name='CUSTOMER');

-- Source: V1__baseline.sql
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code='PLATFORM_MANAGE'
WHERE r.tenant_id IS NULL AND r.name='PLATFORM_SUPER_ADMIN';

-- Source: V1__baseline.sql
INSERT INTO role(id,tenant_id,name)
SELECT UUID_TO_BIN(UUID()),t.id,'TECHNICIAN' FROM tenant t
WHERE NOT EXISTS(SELECT 1 FROM role r WHERE r.tenant_id=t.id AND r.name='TECHNICIAN');

-- Source: V1__baseline.sql
INSERT INTO role(id,tenant_id,name)
SELECT UUID_TO_BIN(UUID()),t.id,'SERVICE_MANAGER' FROM tenant t
WHERE NOT EXISTS(SELECT 1 FROM role r WHERE r.tenant_id=t.id AND r.name='SERVICE_MANAGER');

-- Source: V1__baseline.sql
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code IN ('SURVEY_MANAGE','SERVICE_MANAGE')
WHERE r.tenant_id IS NOT NULL AND r.name IN ('TECHNICIAN','SERVICE_MANAGER');

-- Source: V1__baseline.sql
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code='REPORT_VIEW'
WHERE r.tenant_id IS NOT NULL AND r.name='SERVICE_MANAGER';

-- Source: V1__baseline.sql
INSERT INTO permission (id, code)
SELECT UUID_TO_BIN(UUID()), 'PLATFORM_MANAGE'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'PLATFORM_MANAGE');

-- Source: V1__baseline.sql
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code = 'PLATFORM_MANAGE'
WHERE r.tenant_id IS NULL
  AND r.name = 'PLATFORM_SUPER_ADMIN';

-- Source: V1__baseline.sql
UPDATE tenant SET updated_at=created_at WHERE updated_at IS NULL;

-- Source: V1__baseline.sql
INSERT INTO permission(id,code)
SELECT UUID_TO_BIN(UUID()),'PAYMENT_REFUND'
WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='PAYMENT_REFUND');

-- Source: V1__baseline.sql
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code='PAYMENT_REFUND'
WHERE r.tenant_id IS NOT NULL AND r.name IN ('VENDOR_ADMIN','ACCOUNTANT');

-- Source: V1__baseline.sql
UPDATE customer_order co
JOIN quotation q ON q.id = co.quotation_id AND q.tenant_id = co.tenant_id
SET co.subtotal = q.subtotal,
    co.discount_percent = COALESCE(q.discount_percent, 0),
    co.discount_amount = COALESCE(q.discount_amount, 0),
    co.tax_total = q.tax_amount
WHERE co.quotation_id IS NOT NULL;

-- Source: V1__baseline.sql
UPDATE inventory_transaction
SET transaction_reference = CONCAT('INV-', UPPER(REPLACE(UUID(), '-', '')))
WHERE transaction_reference IS NULL;

-- Source: V1__baseline.sql
INSERT INTO permission(id,code)
SELECT UUID_TO_BIN(UUID()),src.code FROM (
 SELECT 'BRANCH_MANAGE' code UNION ALL SELECT 'SUBSCRIPTION_MANAGE' UNION ALL SELECT 'AUDIT_VIEW'
) src LEFT JOIN permission p ON p.code=src.code WHERE p.id IS NULL;

-- Source: V1__baseline.sql
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p
WHERE r.tenant_id IS NOT NULL AND r.name='VENDOR_ADMIN' AND p.code IN ('BRANCH_MANAGE','AUDIT_VIEW');

-- Source: V1__baseline.sql
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p
WHERE r.tenant_id IS NULL AND r.name='PLATFORM_SUPER_ADMIN' AND p.code IN ('SUBSCRIPTION_MANAGE','AUDIT_VIEW');

-- Source: V1__baseline.sql
INSERT INTO permission(id,code)
SELECT UUID_TO_BIN(UUID()),'BRANDING_MANAGE'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='BRANDING_MANAGE');

-- Source: V1__baseline.sql
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p
WHERE r.tenant_id IS NOT NULL AND r.name='VENDOR_ADMIN' AND p.code='BRANDING_MANAGE';

-- Source: V1__baseline.sql
INSERT INTO permission(id,code)
SELECT UUID_TO_BIN(UUID()),'APPROVAL_WORKFLOW_MANAGE'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='APPROVAL_WORKFLOW_MANAGE');

-- Source: V1__baseline.sql
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p
WHERE r.tenant_id IS NOT NULL AND r.name='VENDOR_ADMIN' AND p.code='APPROVAL_WORKFLOW_MANAGE';

-- Source: V1__baseline.sql
INSERT INTO permission(id,code) SELECT UUID_TO_BIN(UUID()),'INTEGRATION_MANAGE'
WHERE NOT EXISTS(SELECT 1 FROM permission WHERE code='INTEGRATION_MANAGE');

-- Source: V1__baseline.sql
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p WHERE r.tenant_id IS NOT NULL AND r.name='VENDOR_ADMIN' AND p.code='INTEGRATION_MANAGE';

-- Source: V1__baseline.sql
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

-- Source: V1__baseline.sql
UPDATE tenant_subscription ts JOIN subscription_plan p ON p.id=ts.plan_id SET ts.billing_cycle=p.billing_cycle;

-- Source: V1__baseline.sql
INSERT INTO subscription_assignment_history(id,tenant_id,subscription_id,actor_id,action,previous_status,new_status,previous_plan_id,new_plan_id,details_json,created_at)
SELECT UUID_TO_BIN(UUID()),tenant_id,id,created_by,'MIGRATED',NULL,status,NULL,plan_id,
       JSON_OBJECT('source','V46 migration','externalReference',external_reference),created_at
FROM tenant_subscription;

-- Source: V1__baseline.sql
UPDATE subscription_plan SET
 max_products=CASE plan_code WHEN 'STARTER' THEN 100 WHEN 'PROFESSIONAL' THEN 1000 WHEN 'ENTERPRISE' THEN 10000 ELSE max_products END,
 max_customers=CASE plan_code WHEN 'STARTER' THEN 100 WHEN 'PROFESSIONAL' THEN 1000 WHEN 'ENTERPRISE' THEN 10000 ELSE max_customers END,
 trial_period_days=CASE plan_code WHEN 'STARTER' THEN 14 WHEN 'PROFESSIONAL' THEN 14 WHEN 'ENTERPRISE' THEN 30 ELSE trial_period_days END,
 display_order=CASE plan_code WHEN 'STARTER' THEN 1 WHEN 'PROFESSIONAL' THEN 2 WHEN 'ENTERPRISE' THEN 3 ELSE display_order END;

-- Source: V1__baseline.sql
UPDATE subscription_plan
SET max_warehouses=3,entitlements_json=JSON_SET(entitlements_json,'$.multiWarehouse',TRUE),updated_at=CURRENT_TIMESTAMP(6)
WHERE plan_code='STARTER' AND max_warehouses=1;

-- Source: V1__baseline.sql
INSERT INTO permission (id, code)
SELECT UUID_TO_BIN(UUID()), src.code
FROM (
    SELECT 'REPORT_VIEW' AS code
    UNION ALL
    SELECT 'BRANCH_MANAGE' AS code
) src
LEFT JOIN permission existing ON existing.code = src.code
WHERE existing.id IS NULL;

-- Source: V1__baseline.sql
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN ('REPORT_VIEW', 'BRANCH_MANAGE')
WHERE r.tenant_id IS NOT NULL
  AND r.name = 'VENDOR_ADMIN';

-- Source: V1__baseline.sql
UPDATE refresh_token SET family_id = UUID_TO_BIN(UUID()) WHERE family_id IS NULL;

-- Source: V1__baseline.sql
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

-- Source: V1__baseline.sql
INSERT INTO role(id, tenant_id, name)
SELECT UUID_TO_BIN(UUID()), t.id, 'CONTENT_MANAGER'
FROM tenant t
LEFT JOIN role r ON r.tenant_id=t.id AND r.name='CONTENT_MANAGER'
WHERE r.id IS NULL;

-- Source: V1__baseline.sql
INSERT INTO role(id, tenant_id, name)
SELECT UUID_TO_BIN(UUID()), NULL, 'PLATFORM_SUPPORT'
WHERE NOT EXISTS (SELECT 1 FROM role WHERE tenant_id IS NULL AND name='PLATFORM_SUPPORT');

-- Source: V1__baseline.sql
INSERT IGNORE INTO role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p
WHERE r.tenant_id IS NULL AND r.name='PLATFORM_SUPER_ADMIN'
  AND p.code IN ('PLATFORM_MANAGE','SUBSCRIPTION_MANAGE','AUDIT_VIEW','TENANT_VIEW','TENANT_UPDATE');

-- Source: V1__baseline.sql
INSERT IGNORE INTO role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p
WHERE r.tenant_id IS NOT NULL AND r.name='VENDOR_ADMIN'
  AND p.code NOT IN ('PLATFORM_MANAGE','SUBSCRIPTION_MANAGE','TENANT_UPDATE');

-- Source: V1__baseline.sql
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

-- Source: V1__baseline.sql
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

-- Source: V1__baseline.sql
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

-- Source: V1__baseline.sql
INSERT INTO role(id, tenant_id, name)
SELECT UUID_TO_BIN(UUID()), NULL, 'PLATFORM_SUPPORT'
WHERE NOT EXISTS (
    SELECT 1 FROM role WHERE tenant_id IS NULL AND name = 'PLATFORM_SUPPORT'
);

-- Source: V1__baseline.sql
INSERT IGNORE INTO role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN ('AUDIT_VIEW', 'TENANT_VIEW')
WHERE r.tenant_id IS NULL AND r.name = 'PLATFORM_SUPPORT';

-- Source: V1__baseline.sql
DELETE rp
FROM role_permission rp
JOIN role r ON r.id = rp.role_id
JOIN permission p ON p.id = rp.permission_id
WHERE r.tenant_id IS NOT NULL
  AND p.code IN ('PLATFORM_MANAGE','SUBSCRIPTION_MANAGE','TENANT_VIEW','TENANT_UPDATE');

-- Source: V1__baseline.sql
INSERT INTO permission(id, code)
SELECT UUID_TO_BIN(UUID()), src.code
FROM (SELECT 'AUDIT_VIEW' code UNION ALL SELECT 'BRANCH_MANAGE') src
LEFT JOIN permission existing ON existing.code=src.code
WHERE existing.id IS NULL;

-- Source: V1__baseline.sql
INSERT IGNORE INTO role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.code='AUDIT_VIEW'
WHERE r.tenant_id IS NULL AND r.name IN ('PLATFORM_SUPER_ADMIN', 'PLATFORM_SUPPORT');

-- Source: V1__baseline.sql
INSERT IGNORE INTO role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.code='BRANCH_MANAGE'
WHERE r.tenant_id IS NOT NULL AND r.name='VENDOR_ADMIN';

-- Source: V1__baseline.sql
INSERT INTO permission (id, code)
VALUES
    (UUID_TO_BIN(UUID()), 'INTEGRATION_MANAGE'),
    (UUID_TO_BIN(UUID()), 'APPROVAL_WORKFLOW_MANAGE')
ON DUPLICATE KEY UPDATE code = VALUES(code);

-- Source: V1__baseline.sql
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN ('INTEGRATION_MANAGE', 'APPROVAL_WORKFLOW_MANAGE')
WHERE r.tenant_id IS NOT NULL
  AND r.name = 'VENDOR_ADMIN';

-- Source: V2__phase3_reservation_expiry.sql
UPDATE inventory_reservation
SET expires_at = DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 24 HOUR)
WHERE status = 'RESERVED' AND expires_at IS NULL;

-- Source: V3__phase3_order_policy_and_tenant_lifecycle.sql
-- Existing active tenants predate onboarding verification. Preserve access and record
-- the migration as their historical verification/activation time.
UPDATE tenant
SET verified_at = COALESCE(verified_at, created_at),
    activated_at = COALESCE(activated_at, created_at)
WHERE status = 'ACTIVE';

-- Source: V3__phase3_order_policy_and_tenant_lifecycle.sql
INSERT INTO order_transition_policy
(id,tenant_id,from_status,to_status,required_permission,enabled)
VALUES
(UUID_TO_BIN(UUID()),NULL,'PENDING_VENDOR_ACCEPTANCE','CONFIRMED','ORDER_UPDATE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'PENDING_VENDOR_ACCEPTANCE','REJECTED','ORDER_UPDATE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'CONFIRMED','PAYMENT_PENDING','ORDER_UPDATE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'CONFIRMED','PAID','ORDER_UPDATE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'CONFIRMED','MATERIAL_RESERVED','INVENTORY_RESERVE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'CONFIRMED','ON_HOLD','ORDER_UPDATE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'CONFIRMED','CANCELLED','ORDER_CANCEL',TRUE),
(UUID_TO_BIN(UUID()),NULL,'PAYMENT_PENDING','PAYMENT_PARTIAL','ORDER_UPDATE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'PAYMENT_PENDING','PAID','ORDER_UPDATE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'PAYMENT_PENDING','MATERIAL_RESERVED','INVENTORY_RESERVE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'PAYMENT_PENDING','ON_HOLD','ORDER_UPDATE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'PAYMENT_PENDING','CANCELLED','ORDER_CANCEL',TRUE),
(UUID_TO_BIN(UUID()),NULL,'PAYMENT_PARTIAL','PAID','ORDER_UPDATE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'PAYMENT_PARTIAL','MATERIAL_RESERVED','INVENTORY_RESERVE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'PAYMENT_PARTIAL','ON_HOLD','ORDER_UPDATE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'PAYMENT_PARTIAL','CANCELLED','ORDER_CANCEL',TRUE),
(UUID_TO_BIN(UUID()),NULL,'PAID','MATERIAL_RESERVED','INVENTORY_RESERVE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'PAID','ON_HOLD','ORDER_UPDATE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'PAID','CANCELLED','ORDER_CANCEL',TRUE),
(UUID_TO_BIN(UUID()),NULL,'MATERIAL_RESERVED','CONFIRMED','INVENTORY_RESERVE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'MATERIAL_RESERVED','PAYMENT_PENDING','INVENTORY_RESERVE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'MATERIAL_RESERVED','PAYMENT_PARTIAL','INVENTORY_RESERVE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'MATERIAL_RESERVED','PAID','INVENTORY_RESERVE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'MATERIAL_RESERVED','INSTALLATION_SCHEDULED','INSTALLATION_SCHEDULE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'MATERIAL_RESERVED','ON_HOLD','ORDER_UPDATE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'MATERIAL_RESERVED','CANCELLED','ORDER_CANCEL',TRUE),
(UUID_TO_BIN(UUID()),NULL,'INSTALLATION_SCHEDULED','INSTALLATION_IN_PROGRESS','INSTALLATION_UPDATE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'INSTALLATION_SCHEDULED','MATERIAL_RESERVED','INSTALLATION_UPDATE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'INSTALLATION_SCHEDULED','ON_HOLD','ORDER_UPDATE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'INSTALLATION_SCHEDULED','CANCELLED','ORDER_CANCEL',TRUE),
(UUID_TO_BIN(UUID()),NULL,'INSTALLATION_IN_PROGRESS','INSTALLATION_SCHEDULED','INSTALLATION_UPDATE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'INSTALLATION_IN_PROGRESS','MATERIAL_RESERVED','INSTALLATION_UPDATE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'INSTALLATION_IN_PROGRESS','ON_HOLD','ORDER_UPDATE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'INSTALLATION_IN_PROGRESS','INSTALLED','INSTALLATION_UPDATE',TRUE),
(UUID_TO_BIN(UUID()),NULL,'INSTALLED','COMMISSIONED','INSTALLATION_COMMISSION',TRUE),
(UUID_TO_BIN(UUID()),NULL,'COMMISSIONED','CLOSED','ORDER_UPDATE',TRUE);

-- Source: V3__phase3_order_policy_and_tenant_lifecycle.sql
INSERT INTO permission (id,code)
VALUES (UUID_TO_BIN(UUID()),'ORDER_POLICY_MANAGE')
ON DUPLICATE KEY UPDATE code=VALUES(code);

-- Source: V4__phase3_order_policy_permission_grant.sql
-- Backfill the order policy editor permission for existing vendor administrators.
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code = 'ORDER_POLICY_MANAGE'
WHERE r.name = 'VENDOR_ADMIN' AND r.tenant_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- Source: V8__phase5_notification_view_permission.sql
-- Least-privilege access to each authenticated vendor user's own inbox.
INSERT INTO permission(id,code)
SELECT UUID_TO_BIN(UUID()),'NOTIFICATION_VIEW'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='NOTIFICATION_VIEW');

-- Source: V8__phase5_notification_view_permission.sql
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
