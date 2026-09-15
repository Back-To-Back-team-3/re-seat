-- V36__seed_seats_all_stadiums.sql
-- 잠실 외 8개 구장 물리 좌석 500석씩 시드 (V4와 동일 구조)

INSERT INTO seats (stadium_id, zone_id, seat_block, seat_row, seat_number, status)
SELECT z.stadium_id,
       z.id     AS zone_id,
       z.name   AS seat_block,
       r.seat_row,
       c.seat_number,
       'ACTIVE' AS status
FROM seat_zones z
         CROSS JOIN (SELECT 'A' AS seat_row
                     UNION ALL
                     SELECT 'B'
                     UNION ALL
                     SELECT 'C'
                     UNION ALL
                     SELECT 'D'
                     UNION ALL
                     SELECT 'E') r
         CROSS JOIN (SELECT '1' AS seat_number
                     UNION ALL
                     SELECT '2'
                     UNION ALL
                     SELECT '3'
                     UNION ALL
                     SELECT '4'
                     UNION ALL
                     SELECT '5'
                     UNION ALL
                     SELECT '6'
                     UNION ALL
                     SELECT '7'
                     UNION ALL
                     SELECT '8'
                     UNION ALL
                     SELECT '9'
                     UNION ALL
                     SELECT '10') c
WHERE z.stadium_id BETWEEN 2 AND 9;