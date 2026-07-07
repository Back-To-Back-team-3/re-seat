-- V3__init_team.sql
-- 팀 스키마 및 시드
-- home_stadium_id는 V2의 stadiums.id 참조

CREATE TABLE teams (
                       id                BIGINT AUTO_INCREMENT PRIMARY KEY,
                       name              VARCHAR(100) NOT NULL,
                       home_stadium_id   BIGINT       NOT NULL,
                       status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
                       created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                       CONSTRAINT uk_teams_name          UNIQUE (name),
                       CONSTRAINT fk_teams_home_stadium  FOREIGN KEY (home_stadium_id) REFERENCES stadiums(id)
);

INSERT INTO teams (name, home_stadium_id) VALUES
                                              ('두산 베어스',       1),
                                              ('LG 트윈스',         1),
                                              ('키움 히어로즈',     2),
                                              ('SSG 랜더스',        3),
                                              ('KT 위즈',           4),
                                              ('삼성 라이온즈',     5),
                                              ('NC 다이노스',       6),
                                              ('롯데 자이언츠',     7),
                                              ('KIA 타이거즈',      8),
                                              ('한화 이글스',       9);
