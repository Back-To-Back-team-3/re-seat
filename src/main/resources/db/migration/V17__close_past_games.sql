-- V17__close_past_games.sql
-- booking_close_at이 경과한 과거 경기(7월)를 CLOSED로 정리한다.

UPDATE games
SET booking_status = 'CLOSED'
WHERE booking_close_at < '2026-08-01 00:00:00';
