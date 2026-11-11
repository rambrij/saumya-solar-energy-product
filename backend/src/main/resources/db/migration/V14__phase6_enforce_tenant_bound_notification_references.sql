-- Prevent an outbox/provider event from referencing a delivery belonging to another tenant.
ALTER TABLE notification_delivery
  ADD UNIQUE KEY uq_notification_delivery_tenant_id (tenant_id, id);

ALTER TABLE notification_outbox
  DROP FOREIGN KEY fk_notification_outbox_delivery,
  ADD CONSTRAINT fk_notification_outbox_delivery_tenant
    FOREIGN KEY (tenant_id, delivery_id)
    REFERENCES notification_delivery (tenant_id, id);

ALTER TABLE notification_provider_event
  DROP FOREIGN KEY fk_notification_provider_event_delivery,
  ADD CONSTRAINT fk_notification_provider_event_delivery_tenant
    FOREIGN KEY (tenant_id, delivery_id)
    REFERENCES notification_delivery (tenant_id, id);
