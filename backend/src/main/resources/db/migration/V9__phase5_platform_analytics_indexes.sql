-- Support bounded cross-tenant aggregate queries without returning tenant-level rows.
CREATE INDEX ix_customer_order_created_at ON customer_order(created_at);
CREATE INDEX ix_tenant_subscription_status ON tenant_subscription(status);
