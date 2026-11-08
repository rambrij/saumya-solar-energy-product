-- Starter includes a small multi-warehouse allowance so existing small-vendor operations remain usable.
-- Only normalize the original seeded default; do not overwrite plans already customized by an administrator.
UPDATE subscription_plan
SET max_warehouses=3,updated_at=CURRENT_TIMESTAMP(6)
WHERE plan_code='STARTER' AND max_warehouses=1;
