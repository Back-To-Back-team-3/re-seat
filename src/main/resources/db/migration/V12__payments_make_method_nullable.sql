-- H2, MySQL 모두 지원하도록 새로운 컬럼을 만들고 기존 컬럼을 지운 다음, 새 컬럼의 이름을 변경하는 식으로 우회한다.
-- 결제수단은 Toss 승인 응답으로 확정하므로 READY 단계에서는 NULL을 허용한다.
ALTER TABLE payments
    ADD COLUMN method_new VARCHAR(20) NULL;

-- 기존 기본값으로 저장된 미승인 결제의 결제수단을 미확정 상태로 보정한다.
UPDATE payments
SET method_new = CASE
                     WHEN status = 'READY' AND method = 'MOCK' THEN NULL
                     ELSE method
    END;

ALTER TABLE payments
DROP
COLUMN method;

ALTER TABLE payments
    RENAME COLUMN method_new TO method;
