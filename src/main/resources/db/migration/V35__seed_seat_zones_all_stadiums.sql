-- V35__seed_seat_zones_all_stadiums.sql
-- 잠실(stadium_id=1) 외 8개 구장 좌석 구역 시드
-- V4 구조를 stadium_id=2~9에 동일 적용

INSERT INTO seat_zones (stadium_id, name, grade, base_price)
SELECT s.id, z.name, z.grade, z.base_price
FROM stadiums s
         CROSS JOIN (SELECT '101' AS name, 'INFIELD' AS grade, 18000 AS base_price
                     UNION ALL
                     SELECT '102', 'INFIELD', 18000
                     UNION ALL
                     SELECT '103', 'INFIELD', 18000
                     UNION ALL
                     SELECT '301', 'INFIELD', 18000
                     UNION ALL
                     SELECT '302', 'INFIELD', 18000
                     UNION ALL
                     SELECT '303', 'INFIELD', 18000
                     UNION ALL
                     SELECT '201', 'OUTFIELD', 16000
                     UNION ALL
                     SELECT '202', 'OUTFIELD', 16000
                     UNION ALL
                     SELECT '203', 'OUTFIELD', 16000
                     UNION ALL
                     SELECT '204', 'OUTFIELD', 16000) z
WHERE s.id BETWEEN 2 AND 9;