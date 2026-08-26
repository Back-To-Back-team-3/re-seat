-- V4__init_seat_zone_seat.sql
-- 좌석 구역 및 물리 좌석 스키마 + 시드
-- 구장1(서울종합운동장 야구장, stadium_id=1) 기준
-- 구역 10개(INFIELD 6 + OUTFIELD 4), 각 5행(A~E) × 10열(1~10) = 500석
-- base_price는 화~목 성인가 기준 정가만 저장.
-- 요일·시기·연령 할인은 이후 PricePolicy에서 계산 예정.
-- ERD 정합성: seats.stadium_id == seat_zones.stadium_id (전부 1로 통일)

-- 1) 좌석 구역 스키마
CREATE TABLE seat_zones
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    stadium_id BIGINT       NOT NULL,
    name       VARCHAR(100) NOT NULL,
    grade      VARCHAR(20)  NOT NULL,
    base_price INT          NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_seat_zones_stadium FOREIGN KEY (stadium_id) REFERENCES stadiums (id),
    CONSTRAINT uk_seat_zones_stadium_name UNIQUE (stadium_id, name)
);

CREATE INDEX idx_seat_zones_stadium ON seat_zones (stadium_id);

-- 2) 물리 좌석 스키마
CREATE TABLE seats
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    stadium_id  BIGINT      NOT NULL,
    zone_id     BIGINT      NOT NULL,
    seat_block  VARCHAR(20) NOT NULL,
    seat_row    VARCHAR(20) NOT NULL,
    seat_number VARCHAR(20) NOT NULL,
    x_position  INT,
    y_position  INT,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_seats_stadium FOREIGN KEY (stadium_id) REFERENCES stadiums (id),
    CONSTRAINT fk_seats_zone FOREIGN KEY (zone_id) REFERENCES seat_zones (id),
    CONSTRAINT uk_seats_location UNIQUE (stadium_id, zone_id, seat_block, seat_row, seat_number)
);

CREATE INDEX idx_seats_zone_status ON seats (zone_id, status);

-- 3) 좌석 구역 10개 시드
INSERT INTO seat_zones (stadium_id, name, grade, base_price)
VALUES (1, '101', 'INFIELD', 18000),
       (1, '102', 'INFIELD', 18000),
       (1, '103', 'INFIELD', 18000),
       (1, '301', 'INFIELD', 18000),
       (1, '302', 'INFIELD', 18000),
       (1, '303', 'INFIELD', 18000),
       (1, '201', 'OUTFIELD', 16000),
       (1, '202', 'OUTFIELD', 16000),
       (1, '203', 'OUTFIELD', 16000),
       (1, '204', 'OUTFIELD', 16000);

-- 4) 좌석 500석 시드: 구역 10 × 행 5(A~E) × 열 10(1~10)
-- H2/MySQL 크로스 플랫폼 호환을 위해 명확한 SELECT 조인문으로 데이터 세팅
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
WHERE z.stadium_id = 1;
