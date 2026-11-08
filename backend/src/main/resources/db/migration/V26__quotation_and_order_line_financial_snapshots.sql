-- Store the exact rounded per-line values used by the accepted quotation.
-- Existing historical rows remain NULL and are handled by the legacy invoice fallback.
ALTER TABLE quotation_item
    ADD COLUMN discount_amount DECIMAL(19,4) NULL,
    ADD COLUMN taxable_amount DECIMAL(19,4) NULL,
    ADD COLUMN tax_amount DECIMAL(19,4) NULL;

ALTER TABLE order_item
    ADD COLUMN discount_amount DECIMAL(19,4) NULL,
    ADD COLUMN taxable_amount DECIMAL(19,4) NULL,
    ADD COLUMN tax_amount DECIMAL(19,4) NULL;
