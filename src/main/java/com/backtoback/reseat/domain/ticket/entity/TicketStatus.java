package com.backtoback.reseat.domain.ticket.entity;

public enum TicketStatus {
    ISSUED, // 티켓 발급 완료
    USED, // 티켓 사용 완료(입장 완료)
    CANCELED // 티켓 취소 완료
}
