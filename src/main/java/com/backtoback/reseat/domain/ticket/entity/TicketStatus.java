package com.backtoback.reseat.domain.ticket.entity;

public enum TicketStatus {
    ISSUED, // 결제 승인 후 티켓 발급
    USED, // 입장 검증 성공 > 사용 완료
    CANCELED // 취소
}
