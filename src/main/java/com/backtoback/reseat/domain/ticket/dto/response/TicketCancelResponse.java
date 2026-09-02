package com.backtoback.reseat.domain.ticket.dto.response;

import java.time.LocalDateTime;

import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;

import lombok.Builder;
import lombok.Getter;

/**
 * 티켓 취소 "접수" 응답.
 * <p>실제 PG 취소는 결제 쪽 스케줄러가 비동기로 처리하므로,
 * 이 응답 시점에는 아직 환불이 완료됐는지 알 수 없다.
 * 최종 결과(REFUNDED/REFUND_FAILED)는 티켓 상세·목록 조회로 확인해야 한다.</p>
 */
@Getter
@Builder
public class TicketCancelResponse {

    private Long ticketId;
    private TicketStatus ticketStatus; // 접수 시점엔 항상 REFUND_PENDING
    private LocalDateTime refundRequestedAt;

    public static TicketCancelResponse of(Ticket ticket) {
        return TicketCancelResponse
            .builder()
            .ticketId(ticket.getId())
            .ticketStatus(ticket.getStatus())
            .refundRequestedAt(ticket.getRefundRequestedAt())
            .build();
    }
}
