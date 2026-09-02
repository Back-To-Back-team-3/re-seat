ALTER TABLE payment_cancels
    ADD COLUMN pg_idempotency_key VARCHAR(200) NULL,
    ADD CONSTRAINT uk_payment_cancels_pg_idempotency_key UNIQUE (pg_idempotency_key);

ALTER TABLE payment_recovery_tasks
    ADD COLUMN payment_cancel_id BIGINT NULL AFTER payment_id,
    ADD COLUMN recovery_key VARCHAR(100) NULL AFTER type;

UPDATE payment_recovery_tasks
SET recovery_key = CONCAT(type, ':', payment_id)
WHERE recovery_key IS NULL;

ALTER TABLE payment_recovery_tasks
    MODIFY COLUMN recovery_key VARCHAR(100) NOT NULL,
    DROP INDEX uk_payment_recovery_tasks_payment_type,
    ADD CONSTRAINT uk_payment_recovery_tasks_recovery_key UNIQUE (recovery_key),
    ADD CONSTRAINT fk_payment_recovery_tasks_payment_cancel
        FOREIGN KEY (payment_cancel_id) REFERENCES payment_cancels (id);
