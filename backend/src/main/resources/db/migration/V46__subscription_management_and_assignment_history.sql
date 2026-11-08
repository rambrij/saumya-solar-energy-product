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
