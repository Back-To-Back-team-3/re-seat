-- 결제수단은 Toss 승인 응답으로 확정하므로 READY 단계에서는 NULL을 허용한다.
ALTER TABLE payments
    MODIFY COLUMN method VARCHAR(20) NULL DEFAULT NULL;

-- 기존 기본값으로 저장된 미승인 결제의 결제수단을 미확정 상태로 보정한다.
UPDATE payments
SET method = NULL
WHERE status = 'READY'
  AND method = 'MOCK';
