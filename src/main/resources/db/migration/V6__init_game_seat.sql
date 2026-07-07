-- V6__init_game_seat.sql
-- 경기별 좌석 재고 스키마
-- 시드는 넣지 않는다. 실제 재고는 관리자가 경기별로 오픈 API 호출 시 생성.
-- (POST /api/v1/admin/games/{gameId}/seats — 이후에 구현)

CREATE TABLE game_seats (
                            id                BIGINT AUTO_INCREMENT PRIMARY KEY,
                            game_id           BIGINT       NOT NULL,
                            seat_id           BIGINT       NOT NULL,
                            price             INT          NOT NULL,
                            status            VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
                            version           BIGINT       NOT NULL DEFAULT 0,
                            hold_expires_at   DATETIME,
                            sold_at           DATETIME,
                            created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            CONSTRAINT fk_game_seats_game        FOREIGN KEY (game_id) REFERENCES games(id),
                            CONSTRAINT fk_game_seats_seat        FOREIGN KEY (seat_id) REFERENCES seats(id),
                            CONSTRAINT uk_game_seats_game_seat   UNIQUE (game_id, seat_id)
);

CREATE INDEX idx_game_seats_game_status_expires ON game_seats (game_id, status, hold_expires_at);
CREATE INDEX idx_game_seats_hold_expires        ON game_seats (status, hold_expires_at);
