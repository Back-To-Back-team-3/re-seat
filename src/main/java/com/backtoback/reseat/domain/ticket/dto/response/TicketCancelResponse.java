package com.backtoback.reseat.domain.ticket.dto.response;

import java.util.List;

import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.backtoback.reseat.domain.payment.dto.response.PaymentCancelResponse;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TicketCancelResponse {

    private Long ticketId;
    private TicketStatus ticketStatus;
    private boolean refunded; // 환불(REFUNDED)까지 완료됐는지 여부
    private Integer refundAmount; // 이번 취소로 환불된 금액 (결제 패키지가 아직 티켓 단위가 아니라면 주문 전체 금액일 수 있음)
    private Long gameSeatId;
    private GameSeatStatus seatStatus;
    private Long paymentId;
    private PaymentStatus paymentStatus;
    private Integer canceledAmount;
    private Integer remainingAmount;
    private OrderStatus orderStatus; // 취소 반영 후 주문 상태
    private List<RemainingTicket> remainingTickets; // 같은 주문에 남아 있는 다른 티켓들의 현재 상태

    /**
     * 티켓 취소(환불) 결과를 응답 DTO로 조합한다.
     *
     * @param ticket 환불이 확정된(REFUNDED) 티켓
     * @param paymentResponse 이번 취소에 대한 결제 처리 결과
     * @param orderStatus 취소 반영 후 주문 상태
     * @param remainingTickets 같은 주문에 남은 다른 티켓들의 상태 요약
     */
    public static TicketCancelResponse of(
        Ticket ticket,
        PaymentCancelResponse paymentResponse,
        OrderStatus orderStatus,
        List<RemainingTicket> remainingTickets
    ) {
        return TicketCancelResponse
            .builder()
            .ticketId(ticket.getId())
            .ticketStatus(ticket.getStatus())
            .refunded(ticket.getStatus() == TicketStatus.REFUNDED)
            .refundAmount(paymentResponse.getRefundAmount())
            .gameSeatId(ticket.getGameSeat().getId())
            .seatStatus(ticket.getGameSeat().getStatus())
            .paymentId(paymentResponse.getPaymentId())
            .paymentStatus(paymentResponse.getPaymentStatus())
            .canceledAmount(paymentResponse.getCanceledAmount())
            .remainingAmount(paymentResponse.getRemainingAmount())
            .orderStatus(orderStatus)
            .remainingTickets(remainingTickets)
            .build();
    }

    public record RemainingTicket(Long ticketId, TicketStatus status) {
    }
}
