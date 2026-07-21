ALTER TABLE payments
    ADD CONSTRAINT uk_payments_order UNIQUE (order_id);
