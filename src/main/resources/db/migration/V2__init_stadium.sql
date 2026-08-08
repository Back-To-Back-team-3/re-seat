-- V2__init_stadium.sql
-- 구장 스키마 및 시드
-- 이후 teams(V3), seat_zones/seats(V4), games(V5)가 FK로 참조

CREATE TABLE stadiums
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    address        VARCHAR(255) NOT NULL,
    total_capacity INT          NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_stadiums_name UNIQUE (name)
);

INSERT INTO stadiums (name, address, total_capacity)
VALUES ('서울종합운동장 야구장', '서울 송파구', 23750),
       ('고척스카이돔', '서울 구로구', 16000),
       ('인천SSG랜더스필드', '인천 미추홀구', 23000),
       ('수원KT위즈파크', '경기 수원시', 18700),
       ('대구삼성라이온즈파크', '대구 수성구', 24000),
       ('창원NC파크', '경남 창원시', 17861),
       ('사직야구장', '부산 동래구', 22990),
       ('광주-기아 챔피언스필드', '광주 북구', 20500),
       ('대전 한화생명 볼파크', '대전 중구', 20000);
