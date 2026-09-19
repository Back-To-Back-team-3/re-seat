-- 시나리오 C(및 이후 좌석 경합이 있는 시나리오) 측정 종료 직후 실행한다.
-- game_seat_id별 활성 HOLDING 예약이 2건 이상이면 over-booking이 발생한 것이다.

SELECT rs.game_seat_id, COUNT(*) AS active_hold_count
FROM reservation_seats rs
         JOIN reservations r ON r.id = rs.reservation_id
         JOIN game_seats gs ON gs.id = rs.game_seat_id
WHERE r.game_id = $GAME_ID
  AND r.status = 'HOLDING'
  AND r.hold_expires_at > NOW()
  AND gs.status = 'HELD'
GROUP BY rs.game_seat_id
HAVING COUNT(*) > 1;