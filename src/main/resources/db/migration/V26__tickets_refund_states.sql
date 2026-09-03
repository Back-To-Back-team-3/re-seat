-- 부분 취소(환불) 파이프라인 지원을 위해 티켓 상태를 6종으로 세분화한다.
-- ISSUED / REFUND_PENDING / REFUND_FAILED / REFUNDED / USED_ENTERED / USED_NO_SHOW
-- Ticket.status는 EnumType.STRING이라, DB에 예전 값(USED, CANCELED)이 남아 있으면
-- 애플리케이션 enum과 매핑되지 않아 조회 시 변환 오류가 발생한다. 배포 전에 반드시 백필한다.

-- 1) 환불 파이프라인에 필요한 시각 컬럼을 먼저 추가한다.
ALTER TABLE tickets
    ADD COLUMN refund_requested_at DATETIME(6) NULL AFTER canceled_at;

ALTER TABLE tickets
    ADD COLUMN refunded_at DATETIME(6) NULL AFTER refund_requested_at;

-- 2) 기존 상태값을 새 상태로 백필한다. (USED -> USED_ENTERED, CANCELED -> REFUNDED)
UPDATE tickets SET status = 'USED_ENTERED' WHERE status = 'USED';

UPDATE tickets
SET status = 'REFUNDED',
    refunded_at = canceled_at,
    refund_requested_at = canceled_at
WHERE status = 'CANCELED';

-- 3) canceled_at은 이제 "관리자 강제 취소 집행 시각" 전용이다. 관리자 강제 취소가 아니면 비운다.
UPDATE tickets
SET canceled_at = NULL
WHERE status = 'REFUNDED' AND (cancel_reason IS NULL OR cancel_reason <> 'ADMIN_FORCE_CANCEL');