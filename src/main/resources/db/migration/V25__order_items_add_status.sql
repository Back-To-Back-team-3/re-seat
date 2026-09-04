-- 기존 주문 항목은 환불되지 않은 상태이므로 ACTIVE로 보정한다.
ALTER TABLE order_items
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' AFTER price;
