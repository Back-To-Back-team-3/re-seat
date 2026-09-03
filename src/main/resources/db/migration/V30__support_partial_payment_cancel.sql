-- Toss 부분 취소 요청의 중복 처리를 방지할 PG 멱등키를 취소 이력에 추가한다.
ALTER TABLE payment_cancels
    ADD COLUMN pg_idempotency_key VARCHAR(200) NULL,
    ADD CONSTRAINT uk_payment_cancels_pg_idempotency_key UNIQUE (pg_idempotency_key);

-- 복구 작업이 부분 취소 이력을 참조하고 복구 대상별 중복 방지 키를 저장할 수 있게 한다.
ALTER TABLE payment_recovery_tasks
    ADD COLUMN payment_cancel_id BIGINT NULL AFTER payment_id,
    ADD COLUMN recovery_key VARCHAR(100) NULL AFTER type;

-- 기존 승인 복구 작업에는 복구 유형과 결제 ID를 조합한 키를 부여한다.
UPDATE payment_recovery_tasks
SET recovery_key = CONCAT(type, ':', payment_id)
WHERE recovery_key IS NULL;

-- 복구 키를 필수·고유 값으로 전환하고 부분 취소 이력과의 참조 관계를 설정한다.
ALTER TABLE payment_recovery_tasks
    MODIFY COLUMN recovery_key VARCHAR(100) NOT NULL,
    DROP INDEX uk_payment_recovery_tasks_payment_type,
    ADD CONSTRAINT uk_payment_recovery_tasks_recovery_key UNIQUE (recovery_key),
    ADD CONSTRAINT fk_payment_recovery_tasks_payment_cancel
        FOREIGN KEY (payment_cancel_id) REFERENCES payment_cancels (id);

-- 사용자와 결제 상태를 함께 조건으로 조회하는 쿼리를 지원한다.
CREATE INDEX idx_payments_user_status
    ON payments (user_id, status);
