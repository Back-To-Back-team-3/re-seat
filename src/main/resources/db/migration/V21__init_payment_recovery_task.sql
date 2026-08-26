CREATE TABLE payment_recovery_tasks
(
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id            BIGINT      NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count         INT         NOT NULL DEFAULT 0,
    next_retry_at         TIMESTAMP NULL,
    processing_started_at TIMESTAMP NULL,
    last_error            VARCHAR(500) NULL,
    completed_at          TIMESTAMP NULL,
    created_at            TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_payment_recovery_tasks_payment UNIQUE (payment_id),
    CONSTRAINT fk_payment_recovery_tasks_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
);

CREATE INDEX idx_payment_recovery_tasks_status_retry
    ON payment_recovery_tasks (status, next_retry_at);
