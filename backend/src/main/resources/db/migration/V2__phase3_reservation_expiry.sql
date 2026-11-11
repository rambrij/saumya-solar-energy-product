-- Phase 3: inventory reservation expiry and immutable release metadata.
-- Existing active reservations receive a fresh 24-hour grace window on upgrade.
ALTER TABLE inventory_reservation
    ADD COLUMN expires_at TIMESTAMP(6) NULL,
    ADD COLUMN pre_reservation_order_status VARCHAR(40) NULL,
    ADD COLUMN released_at TIMESTAMP(6) NULL,
    ADD COLUMN release_reason VARCHAR(500) NULL,
    ADD COLUMN released_by BINARY(16) NULL,
    ADD KEY ix_reservation_expiry (tenant_id, status, expires_at);

UPDATE inventory_reservation
SET expires_at = DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 24 HOUR)
WHERE status = 'RESERVED' AND expires_at IS NULL;

-- Scheduler-originated order history entries have no human actor.
ALTER TABLE order_status_history
    MODIFY COLUMN actor_id BINARY(16) NULL,
    ADD COLUMN actor_type VARCHAR(20) NOT NULL DEFAULT 'USER';

CREATE TRIGGER trg_order_status_history_no_update
BEFORE UPDATE ON order_status_history
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='order_status_history is append-only';

CREATE TRIGGER trg_order_status_history_no_delete
BEFORE DELETE ON order_status_history
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='order_status_history is append-only';
