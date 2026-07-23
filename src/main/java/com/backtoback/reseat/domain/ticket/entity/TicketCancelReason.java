package com.backtoback.reseat.domain.ticket.entity;

public enum TicketCancelReason {
    USER_REFUND, // 사용자 직접 취소
    PAYMENT_CANCELED, // 결제 취소로 티켓 취소
    ADMIN_FORCE_CANCEL // 관리자 강제 취소
}
