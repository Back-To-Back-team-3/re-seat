package com.backtoback.reseat.domain.order.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.backtoback.reseat.domain.order.repository.OrderGameSeatRepository;
import com.backtoback.reseat.domain.order.repository.OrderRepository;
import com.backtoback.reseat.domain.order.repository.OrderReservationRepository;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;

import lombok.RequiredArgsConstructor;

/**
 * 결제 기한이 지난 주문과 연결된 예약 · 좌석 만료 처리 서비스.
 * <p>Order, Reservation, GameSeat 상태 변경을 하나의 트랜잭션으로 처리한다.</p>
 * <p>UPDATE 순서는 Order → Reservation → GameSeat으로 유지한다.
 * 후속 UPDATE는 EXPIRED 주문을 기준으로 대상을 조회하므로 순서를 변경하지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
public class OrderExpiryService {

    private final OrderRepository orderRepository;
    private final OrderReservationRepository orderReservationRepository;
    private final OrderGameSeatRepository orderGameSeatRepository;

    /**
     * 기준 시간까지 결제되지 않은 주문과 연결된 선점을 만료 처리한다.
     *
     * @param now 만료 판정 기준 시간
     * @return 만료 처리된 주문, 예약과 경기 좌석 수
     */
    @Transactional
    public OrderExpiryResult expireOrders(LocalDateTime now) {

        // Reservation과 GameSeat가 EXPIRED 주문을 기준으로 조회되므로 주문을 먼저 만료 처리한다.
        int expiredOrders = orderRepository.expireCreatedOrders(now, OrderStatus.CREATED, OrderStatus.EXPIRED);
        int expiredReservations
            = orderReservationRepository
                .expireReservationsByExpiredOrders(
                    now,
                    ReservationStatus.HOLDING,
                    OrderStatus.EXPIRED,
                    ReservationStatus.EXPIRED
                );
        int releasedSeats
            = orderGameSeatRepository
                .releaseGameSeatsByExpiredOrders(
                    now,
                    GameSeatStatus.HELD,
                    OrderStatus.EXPIRED,
                    GameSeatStatus.AVAILABLE
                );

        return new OrderExpiryResult(expiredOrders, expiredReservations, releasedSeats);
    }

    /**
     * 주문 만료 처리 결과.
     *
     * @param expiredOrders EXPIRED로 전이된 주문 수
     * @param expiredReservations EXPIRED로 전이된 예약 수
     * @param releasedSeats AVAILABLE로 반환된 경기 좌석 수
     */
    public record OrderExpiryResult(int expiredOrders, int expiredReservations, int releasedSeats) {

        public int total() {
            return expiredOrders + expiredReservations + releasedSeats;
        }
    }
}
