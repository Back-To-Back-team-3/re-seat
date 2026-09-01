-- V26__stadiums_add_coordinates.sql
-- 구장 좌표 컬럼 추가 (T3-14)

ALTER TABLE stadiums
    ADD COLUMN latitude  DECIMAL(10, 7) NULL COMMENT '구장 위도' AFTER address,
    ADD COLUMN longitude DECIMAL(11, 7) NULL COMMENT '구장 경도' AFTER latitude;