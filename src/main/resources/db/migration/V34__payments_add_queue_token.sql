ALTER TABLE payments
    ADD COLUMN queue_token VARCHAR(255) NULL AFTER pg_payment_key;
