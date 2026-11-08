ALTER TABLE inventory_transaction
    ADD COLUMN transaction_reference VARCHAR(50) NULL;

UPDATE inventory_transaction
SET transaction_reference = CONCAT('INV-', UPPER(REPLACE(UUID(), '-', '')))
WHERE transaction_reference IS NULL;

ALTER TABLE inventory_transaction
    MODIFY COLUMN transaction_reference VARCHAR(50) NOT NULL,
    ADD UNIQUE INDEX uq_inventory_transaction_reference (tenant_id, transaction_reference);
