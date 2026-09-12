-- V37__add_unique_stadium_game_at_to_games.sql
-- 동일 구장·동일 일시 중복 경기 등록 방지

ALTER TABLE games
    ADD CONSTRAINT uk_games_stadium_game_at UNIQUE (stadium_id, game_at);