-- users 테이블의 password, phone 컬럼 NULL 허용으로 수정
ALTER TABLE users MODIFY COLUMN password VARCHAR (255) NULL;
ALTER TABLE users MODIFY COLUMN phone VARCHAR (20) NULL;

-- 소셜 로그인 구분을 위한 provider 및 provider_id 컬럼 추가
ALTER TABLE users
    ADD COLUMN provider VARCHAR(50) NULL;
ALTER TABLE users
    ADD COLUMN provider_id VARCHAR(255) NULL;

-- 소셜 로그인 계정 고유 식별을 위해 provider, provider_id 복합 유니크 제약 추가
ALTER TABLE users
    ADD CONSTRAINT uk_users_provider_provider_id UNIQUE (provider, provider_id);

-- 중복된 기존 리프레시 토큰이 있는 경우, 최신 토큰(id가 가장 큰 것)만 남기고 삭제
DELETE
FROM refresh_token
WHERE id NOT IN (SELECT max_id
                 FROM (SELECT MAX(id) AS max_id
                       FROM refresh_token
                       GROUP BY user_id) tmp);

-- 사용자당 1개의 Refresh Token만 보장하기 위해 user_id 유니크 제약 추가
ALTER TABLE refresh_token
    ADD CONSTRAINT uk_refresh_token_user UNIQUE (user_id);
