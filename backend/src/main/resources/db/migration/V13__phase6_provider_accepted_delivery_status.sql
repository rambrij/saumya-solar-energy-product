-- Messaging adapter acceptance is distinct from a delivery receipt callback.
ALTER TABLE notification_delivery DROP CHECK chk_notification_delivery_status;
ALTER TABLE notification_delivery
  ADD CONSTRAINT chk_notification_delivery_status
  CHECK (status IN ('PENDING','SENT','DELIVERED','FAILED','DEAD_LETTER','SUPPRESSED'));
