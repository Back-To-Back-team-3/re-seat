package com.backtoback.reseat.domain.ticket.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketCancelReason;
import com.backtoback.reseat.domain.ticket.exception.TicketNotFoundException;
import com.backtoback.reseat.domain.ticket.repository.TicketRepository;

import lombok.RequiredArgsConstructor;

/**
 * 티켓 환불 상태(REFUND_PENDING / REFUND_FAILED) 기록을 호출부 트랜잭션과 분리해서 즉시 커밋하기 위한 지원 빈.
 * <p>PG 호출 전 REFUND_PENDING 기록과, PG 호출 실패 시 REFUND_FAILED 기록은 호출부({@link TicketService})의
 * 트랜잭션이 나중에 예외로 롤백되더라도 반드시 살아남아야 한다.
 * "환불 진행 중" 추적과 "환불 실패 티켓" 재시도·관리자 처리가 실제로 가능하다.</p>
 * <p>{@code TicketService}에서 같은 빈 안의 self-invocation으로 {@code @Transactional(REQUIRES_NEW)}를
 * 걸면 프록시가 적용되지 않아 무시되므로, 반드시 별도 빈으로 분리해서 호출해야 한다.</p>
 */
@Service
@RequiredArgsConstructor
public class TicketRefundTransactionService {

    private final TicketRepository ticketRepository;

    /**
     * PG 취소 호출 전 "환불 진행 중" 상태를 별도 트랜잭션으로 즉시 커밋한다.
     * <p>호출부 트랜잭션이 나중에 롤백돼도 이 기록은 살아남는다.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void beginRefund(Long ticketId, TicketCancelReason cancelReason, String cancelDetail) {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(TicketNotFoundException::new);
        ticket.requestRefund(cancelReason, cancelDetail);
    }

    /**
     * PG 취소 호출이 실패했을 때 "환불 실패" 상태를 별도 트랜잭션으로 즉시 커밋한다.
     * <p>호출부에서 예외를 다시 던져 자신의 트랜잭션을 롤백하더라도 이 기록은 살아남는다.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(TicketNotFoundException::new);
        ticket.failRefund();
    }
}
