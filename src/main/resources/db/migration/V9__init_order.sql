-- V9__init_order.sql
-- 주문 및 주문 항목 스키마
-- reservation → order (1:1), reservation_seats → order_items (1:1)
-- total_amount는 아동 할인·배송료·수수료를 포함한 최종 결제 금액

CREATE TABLE orders
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no       VARCHAR(50) NOT NULL,
    user_id        BIGINT      NOT NULL,
    reservation_id BIGINT      NOT NULL,
    total_amount   INT         NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_orders_no UNIQUE (order_no),
    CONSTRAINT uk_orders_reservation UNIQUE (reservation_id),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_orders_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id)
);

CREATE INDEX idx_orders_user_status ON orders (user_id, status);

CREATE TABLE order_items
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id     BIGINT    NOT NULL,
    game_seat_id BIGINT    NOT NULL,
    price        INT       NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_items_game_seat FOREIGN KEY (game_seat_id) REFERENCES game_seats (id)
);

CREATE INDEX idx_order_items_order ON order_items (order_id);
