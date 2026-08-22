package com.backtoback.reseat.domain.order.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.order.dto.response.OrderCancelResponse;
import com.backtoback.reseat.domain.order.dto.response.OrderResponse;
import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderItem;
import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.backtoback.reseat.domain.order.exception.InvalidOrderStatusException;
import com.backtoback.reseat.domain.order.exception.OrderAccessDeniedException;
import com.backtoback.reseat.domain.order.exception.OrderExpiredException;
import com.backtoback.reseat.domain.order.exception.OrderNotFoundException;
import com.backtoback.reseat.domain.order.repository.OrderItemRepository;
import com.backtoback.reseat.domain.order.repository.OrderRepository;
import com.backtoback.reseat.domain.order.repository.OrderReservationRepository;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationSeat;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.reservation.exception.InvalidReservationStatusException;
import com.backtoback.reseat.domain.reservation.exception.PreReservationExpiredException;
import com.backtoback.reseat.domain.reservation.exception.ReservationAccessDeniedException;
import com.backtoback.reseat.domain.reservation.exception.ReservationAlreadyOrderedException;
import com.backtoback.reseat.domain.reservation.exception.ReservationNotFoundException;
import com.backtoback.reseat.domain.reservation.exception.ReservationSeatNotFoundException;
import com.backtoback.reseat.domain.reservation.repository.ReservationSeatRepository;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.exception.UserNotFoundException;
import com.backtoback.reseat.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 주문을 담당하는 서비스
 * <p>예약 기반 주문 생성, 주문 조회, 결제 전 주문 취소 흐름을 처리한다.</p>
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter ORDER_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long PAYMENT_DEADLINE_MINUTES = 8;
    private static final long MAX_HOLD_DURATION_MINUTES = 18;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderReservationRepository orderReservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final UserRepository userRepository;

    /**
     * 예약 선점 정보를 기반으로 주문을 생성한다.
     * <p>주문 생성 대상 Reservation을 비관적 락으로 조회한다.
     * 잠금 회득 후 주문 생성 기능 여부를 다시 검증한다.</p>
     * <p>주문 생성 후 Reservation과 주문에 포함된
     * 모든 GameSeat의 선점 만료 시간을 결제 기한에 맞춰 동일하게 연장한다.</p>
     *
     * @param userId 현재 사용자 ID
     * @param reservationId 주문으로 전활할 예약 ID
     * @return 생성된 주문 응답 DTO
     */
    @Transactional
    public OrderResponse createOrder(Long userId, Long reservationId) {

        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        // 만료 스케줄러와의 경합을 막기 위해 주문 생성 대상으로 한 예약 행을 비관적 락으로 조회한다.
        Reservation reservation
            = orderReservationRepository
                .findByIdWithPessimisticWriteLock(reservationId)
                .orElseThrow(ReservationNotFoundException::new);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime paymentDeadline = now.plusMinutes(PAYMENT_DEADLINE_MINUTES);

        // 주문 생성 가능 여부를 예약 정보 기준으로 검증한다.
        validateReservationInfo(reservation, userId, reservationId, now);

        // 기존 만료 시간과 결제 기한 중 늦는 값을 사용하되 최초 선점 시간부터 18분을 넘지 않도록 한다.
        LocalDateTime extendedHoldExpiresAt = calculateExtendedHoldExpiresAt(reservation, paymentDeadline);

        List<ReservationSeat> reservationSeats = reservationSeatRepository.findByReservation_Id(reservationId);

        if (reservationSeats.isEmpty()) {
            throw new ReservationSeatNotFoundException();
        }

        String orderNo = generateOrderNo(now);

        // 주문 금액은 선점 당시 저장된 예약 좌석 가격으로 확정한다.
        int totalAmount = reservationSeats.stream().mapToInt(ReservationSeat::getPrice).sum();

        Order order = Order.of(orderNo, user, reservation, totalAmount, paymentDeadline);
        Order saveOrder = orderRepository.save(order);

        // 예약 좌석을 기준으로 주문 목록을 생성한다.
        List<OrderItem> orderItems
            = reservationSeats
                .stream()
                .map(
                    reservationSeat -> OrderItem
                        .of(saveOrder, reservationSeat.getGameSeat(), reservationSeat.getPrice())
                )
                .toList();

        List<OrderItem> saveOrderItems = orderItemRepository.saveAll(orderItems);

        // Reservation과 모든 GameSeat에 동일한 만료 시간을 반영해 선점 상태 정합성을 유지한다.
        extendHoldExpiresAt(reservationId, reservationSeats, extendedHoldExpiresAt);

        return OrderResponse.from(saveOrder, saveOrderItems, extendedHoldExpiresAt);
    }

    /**
     * 주문 ID로 주문 상세 정보를 조회한다.
     *
     * @param userId 현재 사용자 ID
     * @param orderId 조회할 주문 ID
     * @return 주문 상세 응답 DTO
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long userId, Long orderId) {

        Order order = findOrderById(orderId);
        Reservation reservation = order.getReservation();

        if (!order.getUser().getId().equals(userId)) {
            throw new OrderAccessDeniedException();
        }

        List<OrderItem> orderItems = orderItemRepository.findByOrder_Id(orderId);

        return OrderResponse.from(order, orderItems, reservation.getHoldExpiresAt());
    }

    /**
     * 결제 전 주문을 취소한다.
     * <p>CREATE 상태 주문만 취소할 수 있으며, 주문 취소와 함께 예약을 취소하고 선점 좌석을 해제한다.</p>
     *
     * @param userId 현재 사용자 ID
     * @param orderId 취소할 주문 ID
     * @return 주문 취소 응답 DTO
     */
    @Transactional
    public OrderCancelResponse cancelOrder(Long userId, Long orderId) {

        Order order = findOrderById(orderId);

        if (!order.getUser().getId().equals(userId)) {
            throw new OrderAccessDeniedException();
        }

        Reservation reservation = order.getReservation();

        // 주문 생성의 근거가 된 예약도 취소해 선점 상태 정합성을 맞춘다.
        order.cancel();
        reservation.cancel();

        List<OrderItem> orderItems = orderItemRepository.findByOrder_Id(orderId);

        // 주문 항목의 경기 좌석을 다시 예매 가능 상태로 되돌린다.
        orderItems.forEach(orderItem -> {
            GameSeat gameSeat = orderItem.getGameSeat();
            gameSeat.available();
        });

        return OrderCancelResponse.from(order);
    }

    /**
     * 결제 취소 시 주문, 예약과 좌석의 상태를 변경한다.
     * <p>주문과 예약은 CANCELED, 주문에 포함된 모든 경기 좌석은 AVAILABLE 상태로 변경한다.</p>
     *
     * @param orderId 취소 처리할 주문 ID
     */
    @Transactional
    public void cancelPaidOrder(Long orderId) {

        Order order = findOrderById(orderId);

        // 주문 · 예약을 취소 상태로 변경한다.
        order.cancelAfterPayment();
        order.getReservation().cancelConfirmed();

        // 주문 항목의 경기 좌석을 다시 예매 가능 상태로 되돌린다.
        List<OrderItem> orderItems = orderItemRepository.findByOrder_Id(orderId);

        orderItems.forEach(orderItem -> {
            GameSeat gameSeat = orderItem.getGameSeat();
            gameSeat.available();
        });
    }

    /**
     * 결제 승인 성공 주문과 연결된 예약 · 좌석을 결제 완료 상태로 변경한다.
     * <p>CREATED 상태와 결제 기한을 조건으로 주문을 원자적으로 PAID 상태로 전이하고,
     * 연결된 Reservation과 GameSeat를 각각 CONFIRMED, SOLD 상태로 변경한다.</p>
     *
     * @param orderId 결제 완료 처리할 주문 ID
     */
    @Transactional
    public void completeOrder(Long orderId) {

        Order order = findOrderById(orderId);
        LocalDateTime now = LocalDateTime.now();

        if (!order.getPaymentDeadline().isAfter(now)) {
            throw new OrderExpiredException();
        }

        // 주문 상태와 결제 기한을 함께 검증해 만료 처리와의 경합을 막는다.
        int orderCount = orderRepository.completeCreatedOrder(orderId, now, OrderStatus.CREATED, OrderStatus.PAID);

        if (orderCount == 0) {
            throw new InvalidOrderStatusException();
        } else if (orderCount == 1) {
            // 벌크 UPDATE가 우회한 영속성 컨텍스트의 Order 상태를 DB와 맞춘다.
            order.paid();

            // 결제 완료가 확정된 예약과 좌석을 함께 확정한다.
            Reservation reservation = order.getReservation();
            reservation.confirm();

            orderItemRepository.findByOrder_Id(orderId).forEach(orderItem -> {
                GameSeat gameSeat = orderItem.getGameSeat();
                gameSeat.sell();
            });
        }
    }

    /**
     * 결제 기한이 만료된 주문을 만료 상태로 변경한다.
     * <p>CREATED 상태와 결제 기한을 조건으로 주문을 원자적으로 EXPIRED 상태로 전이한다.</p>
     *
     * @param orderId 만료 처리할 주문 ID
     */
    @Transactional
    public void expireOrder(Long orderId) {

        Order order = findOrderById(orderId);
        LocalDateTime now = LocalDateTime.now();

        // 주문 상태와 결제 기한을 함께 검증해 결제 완료 처리와의 경합을 막는다.
        int orderCount = orderRepository.expireCreatedOrder(orderId, now, OrderStatus.CREATED, OrderStatus.EXPIRED);

        if (orderCount == 0) {
            throw new InvalidOrderStatusException();
        } else if (orderCount == 1) {
            // 벌크 UPDATE가 우회한 영속성 컨텍스트의 Order 상태를 DB와 맞춘다.
            order.expired();
        }
    }

    /**
     * 종결 결제 실패 주문을 취소 상태로 변경한다.
     * <p>재시도 가능한 결제 실패에는 호출하지 않고, CREATED 상태의 주문을 CANCELED 상태로 변경한다.</p>
     *
     * @param orderId 취소 처리할 주문 ID
     */
    @Transactional
    public void failOrder(Long orderId) {

        Order order = findOrderById(orderId);
        order.cancel();
    }

    /**
     * 주문 생성 전 예약 정보를 검증한다.
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
     * <p>현재 단계에서는 날짜와 랜덤 문자열을 조합해 주문 번호를 생성한다.
     * 추후 Redis INCR 기반 채번 방식으로 변경한다.</p>
     *
     * @param now 주문 생성 시간
     * @return 주문 번호
     */
    private String generateOrderNo(LocalDateTime now) {
        String date = now.format(ORDER_DATE_FORMATTER);
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();

        return "ORD-" + date + "-" + random;
    }

    /**
     * 주문 ID로 주문을 조회한다.
     *
     * @param orderId 조회할 주문 ID
     * @return 조회된 주문
     */
    private Order findOrderById(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(OrderNotFoundException::new);
    }

    /**
     * 주문 생성 시 적용할 선점 만료 시간을 계산한다.
     * <p>기존 선점 만료 시간과 결제 기한 중 늦은 시간을 선택하고,
     * 최초 선점 시간부터 최대 18분을 넘기지 않도록 제한한다.</p>
     *
     * @param reservation 주문 생성 대상 예약
     * @param paymentDeadline 주문 결제 기한
     * @return 최종 선점 만료 시간
     */
    private LocalDateTime calculateExtendedHoldExpiresAt(Reservation reservation, LocalDateTime paymentDeadline) {

        // 기존 만료 시간과 결제 기한 중 늦은 값을 연장 후보로 선택한다.
        LocalDateTime laterHoldExpiresAt
            = reservation.getHoldExpiresAt().isAfter(paymentDeadline) ? reservation.getHoldExpiresAt()
                : paymentDeadline;

        // 최초 선점 시간부터 18분을 최대 만료 시간으로 계산한다.
        LocalDateTime maxHoldExpiresAt = reservation.getCreatedAt().plusMinutes(MAX_HOLD_DURATION_MINUTES);

        // 결제 기한까지 선점을 유지할 수 없으면 주문 생성을 막는다.
        if (paymentDeadline.isAfter(maxHoldExpiresAt)) {
            throw new PreReservationExpiredException();
        }

        // 연장 후보가 최대 만료 시간을 넘으면 18분 상한으로 제한한다.
        return laterHoldExpiresAt.isBefore(maxHoldExpiresAt) ? laterHoldExpiresAt : maxHoldExpiresAt;
    }

    /**
     * Reservation과 주문에 포함된 모든 GameSeat의 선점 만료 시간을 연장한다.
     * <p>Reservation과 GameSeat에 동일한 만료 시간을 적용하여 정합성을 보장한다.</p>
     *
     * @param reservationId 연장할 예약 ID
     * @param reservationSeats 예약에 포함된 좌석 목록
     * @param extendedHoldExpiresAt 최종 선점 만료 시간
     */
    private void extendHoldExpiresAt(
        Long reservationId,
        List<ReservationSeat> reservationSeats,
        LocalDateTime extendedHoldExpiresAt
    ) {

        // 잠금 조회한 Reservation의 선점 만료 시간을 계산 결과로 갱신한다.
        orderReservationRepository.updateHoldExpiresAtById(reservationId, extendedHoldExpiresAt);

        // 예약에 포함된 모든 GameSeat에도 동일한 만료 시간을 적용한다.
        reservationSeats.forEach(reservationSeat -> {
            GameSeat gameSeat = reservationSeat.getGameSeat();
            gameSeat.updateHoldExpiresAt(extendedHoldExpiresAt);
        });
    }
}
