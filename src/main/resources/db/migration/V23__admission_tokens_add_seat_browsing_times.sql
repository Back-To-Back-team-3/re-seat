-- 1. admission_tokens에 seat_browsing_expires_at 추가
-- 처음에는 기존 행을 보정해야 하므로 NULL 허용
ALTER TABLE admission_tokens
    ADD COLUMN seat_browsing_expires_at DATETIME NULL;

-- 2. admission_tokens에 seat_browsing_completed_at 추가
-- 최초 선점 전에는 값이 없으므로 계속해서 NULL 허용
ALTER TABLE admission_tokens
    ADD COLUMN seat_browsing_completed_at DATETIME NULL;

-- 3. 기존 데이터의 seat_browsing_expires_at 보정
-- 기존 토큰은 발급시간부터 3분 후를 최초 좌석 탐색 만료시간으로 설정
UPDATE admission_tokens
SET seat_browsing_expires_at = ADDTIME(issued_at, '00:03:00')
WHERE seat_browsing_expires_at IS NULL;

-- 4. 보정 후 seat_browsing_expires_at을 NOT NULL로 변경
ALTER TABLE admission_tokens
    MODIFY COLUMN seat_browsing_expires_at DATETIME NOT NULL;
