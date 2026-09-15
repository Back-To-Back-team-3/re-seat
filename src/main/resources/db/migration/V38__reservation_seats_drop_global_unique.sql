-- V38__reservation_seats_drop_global_unique.sql
-- 취소·만료된 예약의 좌석 이력까지 포함해 영구 재선점을 막던 전역 UNIQUE(game_seat_id)를 제거하고,
-- 예약 내 동일 좌석 중복 삽입 방지 + FK 인덱스 요구를 동시에 충족하는
-- 복합 UNIQUE(game_seat_id, reservation_id)로 대체한다.

ALTER TABLE reservation_seats
    ADD UNIQUE INDEX uk_reservation_seats_game_seat_reservation (game_seat_id, reservation_id);

ALTER TABLE reservation_seats
DROP INDEX uk_reservation_seats_game_seat;