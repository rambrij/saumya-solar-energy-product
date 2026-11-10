-- Enforce notification idempotency across concurrent application instances.
-- MySQL permits multiple NULL tenant IDs, preserving platform-scoped MFA delivery rows.
CREATE UNIQUE INDEX uq_notification_delivery_tenant_idempotency ON notification_delivery(tenant_id, idempotency_key);
