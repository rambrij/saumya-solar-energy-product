-- Preserve the accepted quotation's immutable financial calculation on the order.
ALTER TABLE customer_order
    ADD COLUMN subtotal DECIMAL(19,4) NULL,
    ADD COLUMN discount_percent DECIMAL(7,4) NOT NULL DEFAULT 0,
    ADD COLUMN discount_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN tax_total DECIMAL(19,4) NULL;

UPDATE customer_order co
JOIN quotation q ON q.id = co.quotation_id AND q.tenant_id = co.tenant_id
SET co.subtotal = q.subtotal,
    co.discount_percent = COALESCE(q.discount_percent, 0),
    co.discount_amount = COALESCE(q.discount_amount, 0),
    co.tax_total = q.tax_amount
WHERE co.quotation_id IS NOT NULL;

-- Legacy direct-product orders do not have a quotation. Their existing total is retained;
-- subtotal/tax remain nullable so the invoicing service can use their line snapshots.
