ALTER TABLE service_sla_policy ADD COLUMN working_days_json JSON NULL;
ALTER TABLE tenant ADD COLUMN service_reopen_days INT NULL;
