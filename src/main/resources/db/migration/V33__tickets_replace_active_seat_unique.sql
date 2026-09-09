-- V33__tickets_replace_active_seat_unique.sql
-- 환불 완료(REFUNDED)된 좌석의 재발급이 UNIQUE 제약에 걸리는 문제를 우회한다.
-- MySQL은 partial unique index를 지원하지 않으므로 generated column으로 우회한다.
-- REFUND_PENDING · REFUND_FAILED 구간은 game_seats.status가 아직 SOLD이므로
-- active_seat_key를 NULL로 만들지 않는다 (해당 좌석은 여전히 점유 중).

ALTER TABLE tickets
DROP INDEX uk_tickets_game_seat;

ALTER TABLE tickets
    ADD COLUMN active_seat_key BIGINT
        GENERATED ALWAYS AS (IF(status = 'REFUNDED', NULL, game_seat_id)) STORED
        AFTER game_seat_id;

ALTER TABLE tickets
    ADD CONSTRAINT uk_tickets_active_seat UNIQUE (active_seat_key);