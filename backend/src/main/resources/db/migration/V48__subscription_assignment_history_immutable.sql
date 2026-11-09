-- Subscription assignment history is immutable; corrections are recorded as new events.
CREATE TRIGGER trg_subscription_assignment_history_no_update
BEFORE UPDATE ON subscription_assignment_history
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='subscription_assignment_history is append-only';
CREATE TRIGGER trg_subscription_assignment_history_no_delete
BEFORE DELETE ON subscription_assignment_history
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='subscription_assignment_history is append-only';
