package com.backtoback.reseat.domain.ticket.admin.service;

import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import com.backtoback.reseat.domain.payment.repository.PaymentRepository;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.ticket.admin.dto.request.AdminTicketCancelRequest;
import com.backtoback.reseat.domain.ticket.admin.dto.response.AdminTicketCancelResponse;
import com.backtoback.reseat.domain.ticket.admin.dto.response.AdminUserTicketResponse;
import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.backtoback.reseat.domain.ticket.repository.TicketRepository;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 전용 티켓 관련 서비스.
 *
 * - 특정 사용자의 티켓 소유 목록 조회
 * - 특정 티켓에 대한 관리자 직권 강제 취소 기능 제공
 *
 * 일반 사용자 티켓 API와는 분리해서 관리자가 사용하는 기능만 모아둔다.
 */
@Service
@RequiredArgsConstructor
public class AdminTicketService {

    // 티켓 조회 및 저장용 JPA Repository
    private final TicketRepository ticketRepository;

    // 사용자 존재 여부 검증용 Repository
    private final UserRepository userRepository;

    // 결제 내역 조회 및 상태 변경용 Repository
    private final PaymentRepository paymentRepository;

    /**
     * 관리자 전용: 특정 사용자의 티켓 소유 목록 조회.
     * 기능 개요:
     * - 관리자가 특정 userId에 대해 현재 어떤 티켓을 가지고 있는지 조회하는 기능
     * - 상태(status) 필터가 있으면 해당 상태만 조회, 없으면 전체 상태 조회
     * - 응답은 AdminUserTicketResponse 형태로 변환하여 Page 형태로 제공
     *
     * @param userId   조회 대상 사용자 ID
     * @param status   필터링할 티켓 상태 (ISSUED / USED / CANCELED 등). null이면 전체 조회.
     * @param pageable 페이징 정보 (page, size, sort)
     * @return 관리자용 사용자 티켓 목록 응답 Page
     */
    @Transactional(readOnly = true)
    public Page<AdminUserTicketResponse> getUserTickets(Long userId, TicketStatus status, Pageable pageable) {
        // 1. 회원 존재 여부 검증
        //    - userId가 실제로 존재하지 않으면 USER_NOT_FOUND 비즈니스 예외를 던진다.
        //    - API 명세에도 USER_NOTFOUND(404) 에러코드가 정의되어 있으므로 그에 맞춘다.
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 2. 티켓 조회
        //    - TicketRepository의 커스텀 메서드를 사용해 userId + status 조건으로 조회
        //    - 조회 결과 Page<Ticket>을 AdminUserTicketResponse로 매핑하여 반환
        return ticketRepository.findAllByUserIdAndStatusWithDetails(userId, status, pageable)
            .map(AdminUserTicketResponse::from);
    }

    /**
     * 관리자 전용: 특정 티켓에 대한 직권 강제 취소.
     * 기능 개요:
     * - 관리자가 비정상적인 상황(부정 사용, 시스템 오류 등)에서 티켓을 강제로 취소하는 기능
     * - 티켓 상태를 CANCELED로 변경하고, 취소 사유 및 취소 시각을 기록
     * - 좌석 재고(game_seats)를 AVAILABLE 상태로 되돌려 다시 판매 가능하도록 만든다
     * - 연관된 결제가 APPROVED 상태라면 결제도 CANCELED로 변경한다
     *
     * @param ticketId 취소 대상 티켓 ID
     * @param request  관리자 취소 요청 DTO (취소 상세 사유 포함)
     * @return 관리자용 티켓 취소 응답 DTO
     */
    @Transactional
    public AdminTicketCancelResponse cancelTicketByAdmin(Long ticketId, AdminTicketCancelRequest request) {
        // 1. 티켓 존재 여부 조회
        //    - ticketId로 티켓을 조회하고 없으면 TICKET_NOT_FOUND 비즈니스 예외를 던진다.
        //    - API 명세에서도 TICKETNOTFOUND(404)가 정의되어 있으므로 동일하게 맞춘다.
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));

        // 2. 관리자 직권 취소 도메인 로직 실행
        //    - Ticket 엔티티 내부의 cancelByAdmin(String reason) 메서드를 호출해
        //      티켓 상태를 CANCELED로 변경하고, 관리자 취소 사유 및 취소 시각을 기록한다.
        //    - 여기서는 DTO가 record이므로 request.reason()으로 값에 접근한다.
        ticket.cancelByAdmin(request.reason());

        // 3. 좌석 재고(GameSeat) 원복 처리
        //    - 티켓은 특정 게임 좌석(GameSeat)에 연결되어 있다.
        //    - 티켓을 취소하면 해당 좌석을 다시 AVAILABLE 상태로 되돌려야 재판매가 가능하다.
        //    - GameSeat.available()은 상태 검증 없이 status를 AVAILABLE로 바꾸고
        //      holdExpiresAt를 null로 초기화한다.
        GameSeat gameSeat = ticket.getGameSeat();
        if (gameSeat != null) {
            // SOLD → AVAILABLE이나 HELD → AVAILABLE을 모두 허용해야 하므로
            // 상태 검증이 있는 release() 대신 available()을 사용한다.
            gameSeat.available();
        }

        // 4. 연관된 결제 내역 취소 처리
        //    - 티켓은 OrderItem을 통해 Order와 연결되어 있고,
        //      결제(Payment)는 Order를 기준으로 한 건만 존재하도록 설계되어 있다.
        //    - 관리자 티켓 취소 시, 해당 주문에 대해 승인(APPROVED) 상태의 결제가 있으면
        //      그 결제 역시 취소(CANCELED) 상태로 변경한다.
        if (ticket.getOrderItem() != null && ticket.getOrderItem().getOrder() != null) {
            Long orderId = ticket.getOrderItem().getOrder().getId();

            if (orderId != null) {
                // orderId + APPROVED 조건으로 결제를 찾고, 존재하면 결제 취소 도메인 로직 실행
                paymentRepository.findByOrder_IdAndStatus(orderId, PaymentStatus.APPROVED)
                    .ifPresent(Payment::cancel);
            }
        }

        // 5. 관리자용 응답 DTO 생성 및 반환
        //    - 티켓 엔티티의 최종 상태를 기반으로 AdminTicketCancelResponse를 생성하여 반환한다.
        return AdminTicketCancelResponse.from(ticket);
    }
}
