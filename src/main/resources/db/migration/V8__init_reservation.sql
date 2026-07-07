-- V8__init_reservation.sql
-- 좌석 선점(HOLD) 도메인 스키마
-- reservation → reservation_seats (1:N)
-- 실제 상태 전이는 이후에 구현. 여기선 스키마만 확정.

CREATE TABLE reservations (
                              id                BIGINT AUTO_INCREMENT PRIMARY KEY,
                              reservation_no    VARCHAR(50)  NOT NULL,
                              user_id           BIGINT       NOT NULL,
                              game_id           BIGINT       NOT NULL,
                              status            VARCHAR(20)  NOT NULL DEFAULT 'HOLDING',
                              hold_expires_at   DATETIME     NOT NULL,
                              created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              CONSTRAINT uk_reservations_no        UNIQUE (reservation_no),
                              CONSTRAINT fk_reservations_user      FOREIGN KEY (user_id) REFERENCES users(id),
                              CONSTRAINT fk_reservations_game      FOREIGN KEY (game_id) REFERENCES games(id)
);

CREATE INDEX idx_reservations_user            ON reservations (user_id);
CREATE INDEX idx_reservations_game_status     ON reservations (game_id, status);
CREATE INDEX idx_reservations_hold_expires    ON reservations (status, hold_expires_at);

CREATE TABLE reservation_seats (
                                   id                BIGINT AUTO_INCREMENT PRIMARY KEY,
                                   reservation_id    BIGINT       NOT NULL,
                                   game_seat_id      BIGINT       NOT NULL,
                                   price             INT          NOT NULL,
                                   created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   CONSTRAINT fk_reservation_seats_reservation  FOREIGN KEY (reservation_id) REFERENCES reservations(id),
                                   CONSTRAINT fk_reservation_seats_game_seat    FOREIGN KEY (game_seat_id)   REFERENCES game_seats(id),
                                   CONSTRAINT uk_reservation_seats_game_seat    UNIQUE (game_seat_id)
);

CREATE INDEX idx_reservation_seats_reservation ON reservation_seats (reservation_id);
