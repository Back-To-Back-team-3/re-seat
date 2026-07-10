package com.backtoback.reseat.domain.order.service;

import com.backtoback.reseat.domain.order.dto.response.OrderResponse;
import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderItem;
import com.backtoback.reseat.domain.order.repository.OrderItemRepository;
import com.backtoback.reseat.domain.order.repository.OrderRepository;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationSeat;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.reservation.exception.InvalidReservationStatusException;
import com.backtoback.reseat.domain.reservation.exception.PreReservationExpiredException;
import com.backtoback.reseat.domain.reservation.exception.ReservationAccessDeniedException;
import com.backtoback.reseat.domain.reservation.exception.ReservationAlreadyOrderedException;
import com.backtoback.reseat.domain.reservation.exception.ReservationNotFoundException;
import com.backtoback.reseat.domain.reservation.exception.ReservationSeatNotFoundException;
import com.backtoback.reseat.domain.reservation.repository.ReservationRepository;
import com.backtoback.reseat.domain.reservation.repository.ReservationSeatRepository;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.exception.UserNotFoundException;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 주문을 담당하는 서비스
 *
 * <p>HOLDING 상태의 예약을 검증하고, 예약 좌석 가격 합계로 주문과 주문 항목을 생성한다.</p>
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter ORDER_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long PAYMENT_DEADLINE_MINUTES = 8;

    /**
     * 예약 선점 정보를 기반으로 주문을 생성한다.
     *
     * @param userId 현재 사용자 ID
     * @param reservationId 주문으로 전활할 예약 ID
     * @return 생성된 주문 응답 DTO
     */
    @Transactional
    public OrderResponse createOrder(Long userId, Long reservationId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(ReservationNotFoundException::new);

        LocalDateTime now = LocalDateTime.now();

        // 주문 생성 가능 여부를 예약 정보 기준으로 검증한다.
        validateReservationInfo(reservation, userId, reservationId, now);

        List<ReservationSeat> reservationSeats = reservationSeatRepository.findByReservation_Id(reservationId);

        if (reservationSeats.isEmpty()) {
            throw new ReservationSeatNotFoundException();
        }

        String orderNo = generateOrderNo(now);

        // 주문 금액은 선점 당시 저장된 예약 좌석 가격으로 확정한다.
        int totalAmount = reservationSeats.stream()
                .mapToInt(ReservationSeat::getPrice)
                .sum();

        LocalDateTime paymentDeadline = now.plusMinutes(PAYMENT_DEADLINE_MINUTES);

        Order order = Order.of(orderNo, user, reservation, totalAmount, paymentDeadline);
        Order saveOrder = orderRepository.save(order);

        // 예약 좌석을 기준으로 주문 목록을 생성한다.
        List<OrderItem> orderItems = reservationSeats.stream()
                .map(reservationSeat ->
                    OrderItem.of(saveOrder, reservationSeat.getGameSeat(), reservationSeat.getPrice())
                ).toList();

        List<OrderItem> saveOrderItems = orderItemRepository.saveAll(orderItems);

        return OrderResponse.from(
                saveOrder,
                saveOrderItems
        );
    }

    /**
     * 주문 생성 전 예약 정보를 검증한다.
     *
     * <p>예약 소유자, 예약 상태, 선점 만료 여부, 중복 주문 여부를 확인한다.</p>
     *
     * @param reservation 주문으로 전환할 예약
     * @param userId 현재 사용자 ID
     * @param reservationId 예약 ID
     * @param now 현재 시간
     */
    private void validateReservationInfo(Reservation reservation, Long userId, Long reservationId, LocalDateTime now) {
         if (!reservation.getUser().getId().equals(userId)) {
             throw new ReservationAccessDeniedException();
         }

         if (!reservation.getStatus().equals(ReservationStatus.HOLDING)) {
             throw new InvalidReservationStatusException();
         }

        if (!reservation.getHoldExpiresAt().isAfter(now)) {
            throw new PreReservationExpiredException();
        }

        // 하나의 예약은 하나의 주문으로만 전환할 수 있다.
        if (orderRepository.existsByReservation_Id(reservationId)) {
            throw new ReservationAlreadyOrderedException();
        }
    }

    /**
     * 주문 번호를 생성한다.
     *
     * <p>현재 단계에서는 날짜와 랜덤 문자열을 조합해 주문 번호를 생성한다.
     * 추후 Redis INCR 기반 채번 방식으로 변경한다.</p>
     *
     * @param now 주문 생성 시간
     * @return 주문 번호
     */
    private String generateOrderNo(LocalDateTime now) {
        String date = now.format(ORDER_DATE_FORMATTER);
        String random = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase();

        return "ORD-" + date + "-" + random;
    }
}
