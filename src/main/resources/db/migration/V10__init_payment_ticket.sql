-- V10__init_payment_ticket.sql
-- 결제 및 티켓 스키마
-- payment는 idempotency_key로 멱등 보장 (B7 대응)
-- ticket은 결제 완료 후 발급, order_item과 1:1 매칭

CREATE TABLE payments (
                          id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
                          payment_no            VARCHAR(50)  NOT NULL,
                          order_id              BIGINT       NULL,
                          resale_order_id       BIGINT       NULL,
                          user_id               BIGINT       NOT NULL,
                          amount                INT          NOT NULL,
                          method                VARCHAR(20)  NOT NULL DEFAULT 'MOCK',
                          status                VARCHAR(20)  NOT NULL DEFAULT 'READY',
                          idempotency_key       VARCHAR(100) NOT NULL,
                          approved_at           TIMESTAMP    NULL,
                          payment_key           VARCHAR(200) NULL,
                          fail_reason           VARCHAR(255) NULL,
                          created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                          CONSTRAINT uk_payments_no                UNIQUE (payment_no),
                          CONSTRAINT uk_payments_idempotency_key   UNIQUE (idempotency_key),
                          CONSTRAINT fk_payments_order             FOREIGN KEY (order_id) REFERENCES orders(id),
                          CONSTRAINT fk_payments_user              FOREIGN KEY (user_id)  REFERENCES users(id)
);

CREATE INDEX idx_payments_order    ON payments (order_id);
CREATE INDEX idx_payments_user     ON payments (user_id);
CREATE INDEX idx_payments_status   ON payments (status);

CREATE TABLE tickets (
                         id                BIGINT AUTO_INCREMENT PRIMARY KEY,
                         ticket_no         VARCHAR(50)  NOT NULL,
                         user_id           BIGINT       NOT NULL,
                         order_item_id     BIGINT       NOT NULL,
                         game_id           BIGINT       NOT NULL,
                         game_seat_id      BIGINT       NOT NULL,
                         status            VARCHAR(20)  NOT NULL DEFAULT 'ISSUED',
                         qr_token          VARCHAR(255) NULL,
                         issued_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                         CONSTRAINT uk_tickets_no             UNIQUE (ticket_no),
                         CONSTRAINT uk_tickets_order_item     UNIQUE (order_item_id),
                         CONSTRAINT uk_tickets_game_seat      UNIQUE (game_seat_id),
                         CONSTRAINT uk_tickets_qr_token       UNIQUE (qr_token),
                         CONSTRAINT fk_tickets_user           FOREIGN KEY (user_id)       REFERENCES users(id),
                         CONSTRAINT fk_tickets_order_item     FOREIGN KEY (order_item_id) REFERENCES order_items(id),
                         CONSTRAINT fk_tickets_game           FOREIGN KEY (game_id)       REFERENCES games(id),
                         CONSTRAINT fk_tickets_game_seat      FOREIGN KEY (game_seat_id)  REFERENCES game_seats(id)
);

CREATE INDEX idx_tickets_user   ON tickets (user_id);
CREATE INDEX idx_tickets_status ON tickets (status);
