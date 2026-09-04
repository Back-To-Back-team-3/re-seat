CREATE TABLE payment_cancels
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id         BIGINT       NOT NULL,
    ticket_id          BIGINT       NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reason             VARCHAR(200) NOT NULL,
    pg_transaction_key VARCHAR(200) NULL,
    failure_reason     VARCHAR(500) NULL,
    completed_at       TIMESTAMP NULL,
    failed_at          TIMESTAMP NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_payment_cancels_ticket UNIQUE (ticket_id),
    CONSTRAINT fk_payment_cancels_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_payment_cancels_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id)
);

CREATE INDEX idx_payment_cancels_payment_status
    ON payment_cancels (payment_id, status);
