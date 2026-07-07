-- V1__init_user.sql
-- 사용자 및 인증 관련 스키마

CREATE TABLE users (
                       id              BIGINT AUTO_INCREMENT PRIMARY KEY,
                       email           VARCHAR(255) NOT NULL,
                       password        VARCHAR(255) NOT NULL,
                       name            VARCHAR(50)  NOT NULL,
                       nickname        VARCHAR(50)  NULL,
                       phone           VARCHAR(20)  NOT NULL,
                       role            VARCHAR(20)  NOT NULL DEFAULT 'USER',
                       status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
                       ci              VARCHAR(255) NULL,
                       is_verified     BOOLEAN      NOT NULL DEFAULT FALSE,
                       real_name       VARCHAR(50)  NULL,
                       created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                       CONSTRAINT uk_users_email  UNIQUE (email),
                       CONSTRAINT uk_users_phone  UNIQUE (phone),
                       CONSTRAINT uk_users_ci     UNIQUE (ci)                  
);

CREATE TABLE refresh_token (
                               id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                               user_id       BIGINT       NOT NULL,
                               token_value   VARCHAR(255) NOT NULL UNIQUE,
                               expired_at    TIMESTAMP    NOT NULL,

                               CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users(id)
);
