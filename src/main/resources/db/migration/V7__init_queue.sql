-- V7__init_queue.sql
-- 대기열 이력 스키마
-- 실제 순번 처리는 Redis, DB엔 기록·상태만 저장

CREATE TABLE queue_entry_histories (
                                       id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       game_id       BIGINT       NOT NULL,
                                       user_id       BIGINT       NOT NULL,
                                       queue_key     VARCHAR(100) NOT NULL,
                                       status        VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
                                       entered_at    TIMESTAMP    NOT NULL,
                                       admitted_at   TIMESTAMP,
                                       canceled_at   TIMESTAMP,
                                       CONSTRAINT fk_queue_entry_histories_game       FOREIGN KEY (game_id) REFERENCES games(id),
                                       CONSTRAINT fk_queue_entry_histories_user       FOREIGN KEY (user_id) REFERENCES users(id),
                                       CONSTRAINT uk_queue_entry_histories_queue_key  UNIQUE (queue_key)
);

CREATE INDEX idx_queue_entry_histories_game_user   ON queue_entry_histories (game_id, user_id);
CREATE INDEX idx_queue_entry_histories_game_status ON queue_entry_histories (game_id, status);

CREATE TABLE admission_tokens (
                                  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                                  game_id       BIGINT       NOT NULL,
                                  user_id       BIGINT       NOT NULL,
                                  token         VARCHAR(255) NOT NULL,
                                  status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
                                  issued_at     TIMESTAMP    NOT NULL,
                                  expires_at    TIMESTAMP    NOT NULL,
                                  used_at       TIMESTAMP,
                                  CONSTRAINT fk_admission_tokens_game    FOREIGN KEY (game_id) REFERENCES games(id),
                                  CONSTRAINT fk_admission_tokens_user    FOREIGN KEY (user_id) REFERENCES users(id),
                                  CONSTRAINT uk_admission_tokens_token   UNIQUE (token)
);

CREATE INDEX idx_admission_tokens_game_user      ON admission_tokens (game_id, user_id);
CREATE INDEX idx_admission_tokens_status_expires ON admission_tokens (status, expires_at);

