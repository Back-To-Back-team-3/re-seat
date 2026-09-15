-- test-concurrency 프로파일(application.yaml)이 참조하는 동시성 테스트 전용 스키마.
-- MYSQL_DATABASE 환경변수는 reseat 하나만 생성하므로, 두 번째 스키마는 init 스크립트로 만든다.
CREATE DATABASE IF NOT EXISTS reseat_concurrency_test
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;