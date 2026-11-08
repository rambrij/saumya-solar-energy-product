ALTER TABLE payment
    ADD COLUMN idempotency_key VARCHAR(160) NULL,
    ADD COLUMN checkout_url VARCHAR(1000) NULL;

CREATE UNIQUE INDEX uq_payment_tenant_idempotency
    ON payment(tenant_id, idempotency_key);
