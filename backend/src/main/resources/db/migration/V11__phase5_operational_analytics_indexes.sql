-- Cover the tenant-scoped date and assignee predicates used by the Phase 5 operations reports.
CREATE INDEX ix_installation_tenant_assignee_schedule ON installation_job(tenant_id,assigned_engineer_id,scheduled_at);
CREATE INDEX ix_service_tenant_assignee_created ON service_request(tenant_id,assigned_engineer_id,created_at);
CREATE INDEX ix_invoice_tenant_created ON invoice(tenant_id,created_at);
CREATE INDEX ix_order_tenant_created ON customer_order(tenant_id,created_at);
