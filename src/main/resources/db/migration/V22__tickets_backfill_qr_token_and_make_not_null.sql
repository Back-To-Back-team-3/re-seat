-- 1) 기존 qr_token 이 NULL 인 티켓에 기본값 채우기
--    ticket_no 는 이미 UNIQUE 이므로 이를 기반으로 생성해도 안전하다.
UPDATE tickets
SET qr_token = CONCAT('MIGRATE-', ticket_no)
WHERE qr_token IS NULL;

-- 2) qr_token 컬럼을 NOT NULL 로 변경
--    (MySQL 기준. 사용하는 DB에 맞춰 MODIFY/MALTER 문법만 맞춰주면 된다.)
ALTER TABLE tickets
    MODIFY COLUMN qr_token VARCHAR (255) NOT NULL;
