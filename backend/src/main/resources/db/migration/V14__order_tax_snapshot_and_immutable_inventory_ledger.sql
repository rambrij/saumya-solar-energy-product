-- Release 3 hardening: preserve tax on direct product orders and make the stock ledger immutable.
ALTER TABLE order_item
    ADD COLUMN tax_percent DECIMAL(5,2) NOT NULL DEFAULT 0;

CREATE TRIGGER trg_inventory_transaction_no_update
BEFORE UPDATE ON inventory_transaction
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Inventory transactions are immutable';

CREATE TRIGGER trg_inventory_transaction_no_delete
BEFORE DELETE ON inventory_transaction
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Inventory transactions are immutable';
