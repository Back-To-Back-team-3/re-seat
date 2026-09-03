package com.backtoback.reseat.domain.ticket.entity;

public enum TicketStatus {
    ISSUED, // 발급 완료 (미사용, 미환불 상태)
    REFUND_PENDING, // 환불 요청 접수 (PG 취소 결과 대기 중)
    REFUND_FAILED, // PG 취소 실패 (재시도 또는 관리자 처리 대상)
    REFUNDED, // 환불(취소) 완료
    USED_ENTERED, // 입장 완료 (QR 검표 성공)
    USED_NO_SHOW // 경기 종료 후 미입장으로 자동 사용 완료 처리
}
