ALTER TABLE payment_cancels
    ADD COLUMN pg_idempotency_key VARCHAR(200) NULL,
    ADD CONSTRAINT uk_payment_cancels_pg_idempotency_key UNIQUE (pg_idempotency_key);
