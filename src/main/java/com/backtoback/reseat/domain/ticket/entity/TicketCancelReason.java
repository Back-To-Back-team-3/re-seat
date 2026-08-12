package com.backtoback.reseat.domain.ticket.entity;

public enum TicketCancelReason {
	USER_REFUND, // 사용자 환불 요청
	ADMIN_FORCE_CANCEL, // 관리자 강제 취소
	PAYMENT_CANCELED // 결제 취소, 환불 처리 취소
}
