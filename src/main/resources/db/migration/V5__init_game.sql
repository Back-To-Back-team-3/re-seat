-- V5__init_game.sql

CREATE TABLE games (
                       id                BIGINT AUTO_INCREMENT PRIMARY KEY,
                       home_team_id      BIGINT       NOT NULL,
                       away_team_id      BIGINT       NOT NULL,
                       stadium_id        BIGINT       NOT NULL,
                       game_at           DATETIME     NOT NULL,
                       booking_open_at   DATETIME     NOT NULL,
                       booking_close_at  DATETIME     NOT NULL,
                       booking_status    VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
                       title             VARCHAR(255),
                       created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                       CONSTRAINT fk_games_home_team FOREIGN KEY (home_team_id) REFERENCES teams(id),
                       CONSTRAINT fk_games_away_team FOREIGN KEY (away_team_id) REFERENCES teams(id),
                       CONSTRAINT fk_games_stadium   FOREIGN KEY (stadium_id)   REFERENCES stadiums(id)
);

-- games 시드 110건 (games.csv 변환)
INSERT INTO games (home_team_id, away_team_id, stadium_id, game_at, booking_open_at, booking_close_at, booking_status, title) VALUES
                                                                                                                                  (2, 4, 1, '2026-04-05 18:30:00', '2026-03-22 10:00:00', '2026-04-05 18:00:00', 'CLOSED', 'LG vs SSG'),
                                                                                                                                  -- ... (109건 계속)
                                                                                                                                  (10, 1, 9, '2026-10-30 14:00:00', '2026-10-16 10:00:00', '2026-10-30 13:30:00', 'SCHEDULED', '한화 vs 두산');
