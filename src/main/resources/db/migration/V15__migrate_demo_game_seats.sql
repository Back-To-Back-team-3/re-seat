
-- 1. 테스트 코드 정상 동작(경기 1에 재고 없음 기대)을 위해 경기 1의 재고 시드를 삭제합니다.
DELETE FROM game_seats WHERE game_id = 1;

-- 2. 대신 로컬 데모 및 포스트맨 테스트를 위해 경기 2(SSG 랜더스 vs KIA 타이거즈)에 좌석 재고를 생성합니다.
INSERT INTO game_seats (
    game_id,
    seat_id,
    price,
    status,
    version
)
SELECT
    g.id,
    s.id,
    z.base_price,
    'AVAILABLE',
    0
FROM games g
JOIN seats s
  ON s.stadium_id = g.stadium_id
JOIN seat_zones z
  ON z.id = s.zone_id
WHERE g.id = 2
  AND s.status = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1
    FROM game_seats gs
    WHERE gs.game_id = g.id
      AND gs.seat_id = s.id
);
