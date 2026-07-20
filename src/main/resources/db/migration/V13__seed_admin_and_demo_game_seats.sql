
-- 경기 1의 구장에 등록된 활성 좌석으로 판매 재고를 생성한다.
-- 경기 1은 수요일·비성수기 경기라 구역 기본가를 그대로 적용한다.
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
WHERE g.id = 1
  AND s.status = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1
    FROM game_seats gs
    WHERE gs.game_id = g.id
      AND gs.seat_id = s.id
);
