-- Each original inventory ledger row may have at most one compensating entry per tenant.
ALTER TABLE inventory_transaction
    ADD COLUMN reversal_of_transaction_id BINARY(16) NULL;

CREATE UNIQUE INDEX uq_inventory_transaction_reversal
    ON inventory_transaction (tenant_id, reversal_of_transaction_id);
