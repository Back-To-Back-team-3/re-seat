package com.backtoback.reseat.domain.ticket.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderItem;
import com.backtoback.reseat.domain.order.repository.OrderItemRepository;
import com.backtoback.reseat.domain.payment.service.PaymentService;
import com.backtoback.reseat.domain.ticket.admin.dto.response.AdminTicketCancelResponse;
import com.backtoback.reseat.domain.ticket.dto.response.TicketCancelResponse;
import com.backtoback.reseat.domain.ticket.dto.response.TicketDetailResponse;
import com.backtoback.reseat.domain.ticket.dto.response.TicketListResponse;
import com.backtoback.reseat.domain.ticket.dto.response.TicketVerifyResponse;
import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketCancelReason;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.backtoback.reseat.domain.ticket.exception.TicketAccessDeniedException;
import com.backtoback.reseat.domain.ticket.exception.TicketCancelDeadlinePassedException;
import com.backtoback.reseat.domain.ticket.exception.TicketNotFoundException;
import com.backtoback.reseat.domain.ticket.repository.TicketRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentService paymentService;

    @Transactional
    public List<Ticket> issue(Order order) {
        List<OrderItem> orderItems = orderItemRepository.findByOrder_Id(order.getId());

        return orderItems
            .stream()
            .filter(orderItem -> !ticketRepository.existsByOrderItemId(orderItem.getId()))
            .map(orderItem -> {
                String ticketNo = generateTicketNo(order.getCreatedAt());
                String qrToken = generateQrToken();

                Ticket ticket = Ticket.issue(ticketNo, order.getUser(), orderItem, orderItem.getGameSeat(), qrToken);

                return ticketRepository.save(ticket);
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<TicketListResponse> getMyTickets(Long userId, TicketStatus status, Pageable pageable) {
        Page<Ticket> tickets
            = (status == null) ? ticketRepository.findByUserId(userId, pageable)
                : ticketRepository.findByUserIdAndStatus(userId, status, pageable);

        return tickets.map(TicketListResponse::from);
    }

    @Transactional(readOnly = true)
    public TicketDetailResponse getTicket(Long userId, Long ticketId) {
        Ticket ticket = ticketRepository.findDetailById(ticketId).orElseThrow(TicketNotFoundException::new);

        if (!ticket.getUser().getId().equals(userId)) {
            throw new TicketAccessDeniedException();
        }

        return TicketDetailResponse.from(ticket);
    }

    /**
     * 사용자 본인의 티켓 1장을 취소(환불) **접수**한다.
     * <p>진입점이자 검증 주체로서 소유자·상태(ISSUED)·환불 기한을 검증한 뒤 REFUND_PENDING으로 전이하고,
     * {@link PaymentService#requestTicketPaymentCancel(Ticket, String)}로 취소 작업을 등록한다.</p>
     * <p>이 등록 호출은 Toss를 직접 부르지 않고 결제 취소 이력(PaymentCancel)과 복구 작업만 저장하므로,
     * 이 메서드 전체가 하나의 단순한 트랜잭션으로 안전하게 처리된다.
     * 실제 PG 취소와 그 결과에 따른 최종 확정(REFUNDED)/실패(REFUND_FAILED) 처리는 결제 쪽 스케줄러가
     * {@link #completeTicketRefund(Long)} / {@link #failTicketRefund(Long)}를 호출해 비동기로 반영한다.</p>
     * <p>그래서 이 메서드의 응답은 "환불 완료"가 아니라 "환불 접수"를 의미한다.
     * 최종 결과는 티켓 상세/목록 조회로 나중에 확인해야 한다.</p>
     *
     * @param userId 현재 사용자 ID
     * @param ticketId 취소할 티켓 ID
     * @return 취소 접수 결과 (티켓 상태는 REFUND_PENDING)
     */
    @Transactional
    public TicketCancelResponse cancelTicket(Long userId, Long ticketId) {
        Ticket ticket = ticketRepository.findDetailById(ticketId).orElseThrow(TicketNotFoundException::new);

        if (!ticket.getUser().getId().equals(userId)) {
            throw new TicketAccessDeniedException();
        }

        // 환불 기한은 ISSUED 상태일 때만 의미가 있다. 그 외 상태는 requestRefund()가 구체적인 예외로 구분해 던진다.
        if (ticket.getStatus() == TicketStatus.ISSUED && !ticket.isRefundable()) {
            throw new TicketCancelDeadlinePassedException();
        }

        ticket.requestRefund(TicketCancelReason.USER_REFUND, null);
        paymentService.requestTicketPaymentCancel(ticket, "사용자 티켓 취소");

        return TicketCancelResponse.of(ticket);
    }

    /**
     * 관리자 강제 취소 **접수** 전용. 소유자·환불 기한 검증 없이 지정한 티켓을 환불 파이프라인에 태운다.
     * <p>확정/실패 처리는 {@link #cancelTicket(Long, Long)}와 동일하게 결제 쪽 스케줄러가 비동기로 반영한다.</p>
     *
     * @param ticketId 강제 취소할 티켓 ID
     * @param detail 관리자가 입력한 취소 상세 사유
     * @return 관리자 강제 취소 접수 결과
     */
    @Transactional
    public AdminTicketCancelResponse cancelTicketByAdmin(Long ticketId, String detail) {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(TicketNotFoundException::new);

        ticket.requestRefund(TicketCancelReason.ADMIN_FORCE_CANCEL, detail);

        String reason = (detail != null && !detail.isBlank()) ? detail : "관리자 직권 취소";
        paymentService.requestTicketPaymentCancel(ticket, reason);

        return AdminTicketCancelResponse.from(ticket);
    }

    /**
     * REFUND_FAILED 상태의 티켓 취소를 재시도(재접수)한다.
     * <p>새 이력을 만들지 않고 같은 티켓을 다시 REFUND_PENDING으로 되돌린 뒤
     * {@link PaymentService#requestTicketPaymentCancel(Ticket, String)}를 다시 호출한다.
     * REFUND_FAILED가 아닌 티켓에 호출하면
     * {@link com.backtoback.reseat.domain.ticket.exception.InvalidTicketStateException}이 발생한다.</p>
     *
     * @param userId 현재 사용자 ID
     * @param ticketId 재시도할 티켓 ID
     * @return 취소 재접수 결과
     */
    @Transactional
    public TicketCancelResponse retryCancelTicket(Long userId, Long ticketId) {
        Ticket ticket = ticketRepository.findDetailById(ticketId).orElseThrow(TicketNotFoundException::new);

        if (!ticket.getUser().getId().equals(userId)) {
            throw new TicketAccessDeniedException();
        }

        ticket.retryRefund();
        paymentService.requestTicketPaymentCancel(ticket, "사용자 티켓 취소 재시도");

        return TicketCancelResponse.of(ticket);
    }

    /**
     * 결제 쪽 복구 스케줄러가 PG 부분 취소 완료를 확인한 뒤 호출한다.
     * 티켓을 REFUNDED로 확정한다.
     *
     * @param ticketId 환불이 확정된 티켓 ID
     */
    @Transactional
    public void completeTicketRefund(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(TicketNotFoundException::new);
        if (ticket.getStatus() == TicketStatus.REFUNDED) {
            // 스케줄러 재시도로 인한 중복 호출 - 이미 처리된 결과이므로 조용히 종료한다.
            return;
        }
        ticket.completeRefund();
    }

    /**
     * 결제 쪽 복구 스케줄러가 PG 부분 취소 실패(재시도 불가 판정)를 확인한 뒤 호출한다
     * 티켓을 REFUND_FAILED로 되돌린다.
     *
     * @param ticketId 환불이 실패한 티켓 ID
     */
    @Transactional
    public void failTicketRefund(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(TicketNotFoundException::new);
        if (ticket.getStatus() == TicketStatus.REFUND_FAILED) {
            // 스케줄러 재시도로 인한 중복 호출 - 이미 처리된 결과이므로 조용히 종료한다.
            return;
        }
        ticket.failRefund();
    }

    @Transactional
    public TicketVerifyResponse verify(Long gameId, String qrToken) {
        Ticket ticket
            = ticketRepository.findByQrTokenAndGameId(qrToken, gameId).orElseThrow(TicketNotFoundException::new);

        ticket.markEntered();

        return TicketVerifyResponse.from(ticket);
    }

    private String generateTicketNo(LocalDateTime baseTime) {
        String datePart = baseTime.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String ticketNo
            = "TKT-" + datePart + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();

        int retry = 0;
        while (ticketRepository.existsByTicketNo(ticketNo) && retry < 3) {
            ticketNo
                = "TKT-" + datePart + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
            retry++;
        }

        return ticketNo;
    }

    private String generateQrToken() {
        String qrToken = UUID.randomUUID().toString();
        int retry = 0;

        while (ticketRepository.existsByQrToken(qrToken) && retry < 3) {
            qrToken = UUID.randomUUID().toString();
            retry++;
        }

        return qrToken;
    }
}
