ALTER TABLE payment_recovery_tasks
    ADD COLUMN type VARCHAR(40) NULL AFTER payment_id;

UPDATE payment_recovery_tasks
SET type = 'CONFIRM_UNKNOWN'
WHERE type IS NULL;

ALTER TABLE payment_recovery_tasks
    MODIFY COLUMN type VARCHAR(40) NOT NULL,
    DROP INDEX uk_payment_recovery_tasks_payment,
    ADD CONSTRAINT uk_payment_recovery_tasks_payment_type UNIQUE (payment_id, type);
