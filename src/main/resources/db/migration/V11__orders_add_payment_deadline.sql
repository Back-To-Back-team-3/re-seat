ALTER TABLE orders
    ADD COLUMN payment_deadline DATETIME NOT NULL AFTER total_amount;
