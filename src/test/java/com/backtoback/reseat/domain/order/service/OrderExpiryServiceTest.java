package com.backtoback.reseat.domain.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.backtoback.reseat.domain.order.repository.OrderGameSeatRepository;
import com.backtoback.reseat.domain.order.repository.OrderRepository;
import com.backtoback.reseat.domain.order.repository.OrderReservationRepository;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderExpiryService")
public class OrderExpiryServiceTest {

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private OrderReservationRepository orderReservationRepository;

	@Mock
	private OrderGameSeatRepository orderGameSeatRepository;

	@InjectMocks
	private OrderExpiryService orderExpiryService;

	@Test
	@DisplayName("만료 처리된 주문 · 예약 · 좌석 수를 결과로 반환한다. ")
	void expireOrders_returnsProcessedCounts() {

		// given
		LocalDateTime now = LocalDateTime.now();

		given(orderRepository.expireCreatedOrders(eq(now), eq(OrderStatus.CREATED), eq(OrderStatus.EXPIRED)))
		    .willReturn(1);

		given(
		    orderReservationRepository
		        .expireReservationsByExpiredOrders(
		            eq(now),
		            eq(ReservationStatus.HOLDING),
		            eq(OrderStatus.EXPIRED),
		            eq(ReservationStatus.EXPIRED)
		        )
		).willReturn(1);

		given(
		    orderGameSeatRepository
		        .releaseGameSeatsByExpiredOrders(
		            eq(now),
		            eq(GameSeatStatus.HELD),
		            eq(OrderStatus.EXPIRED),
		            eq(GameSeatStatus.AVAILABLE)
		        )
		).willReturn(2);

		// when
		OrderExpiryService.OrderExpiryResult result = orderExpiryService.expireOrders(now);

		// then
		assertThat(result.expiredOrders()).isEqualTo(1);
		assertThat(result.expiredReservations()).isEqualTo(1);
		assertThat(result.releasedSeats()).isEqualTo(2);
		assertThat(result.total()).isEqualTo(4);
	}

	@Test
	@DisplayName("주문, 예약, 좌석 순서로 만료 처리한다.")
	void expireOrders_callsRepositoriesInOrder() {

		// given
		LocalDateTime now = LocalDateTime.now();
		InOrder inOrder = inOrder(orderRepository, orderReservationRepository, orderGameSeatRepository);

		// when
		orderExpiryService.expireOrders(now);

		// then
		inOrder.verify(orderRepository).expireCreatedOrders(now, OrderStatus.CREATED, OrderStatus.EXPIRED);
		inOrder
		    .verify(orderReservationRepository)
		    .expireReservationsByExpiredOrders(
		        now,
		        ReservationStatus.HOLDING,
		        OrderStatus.EXPIRED,
		        ReservationStatus.EXPIRED
		    );
		inOrder
		    .verify(orderGameSeatRepository)
		    .releaseGameSeatsByExpiredOrders(now, GameSeatStatus.HELD, OrderStatus.EXPIRED, GameSeatStatus.AVAILABLE);
	}

	@Test
	@DisplayName("주문 만료 처리 중 예외가 발생하면 예약과 좌석 처리를 실행하지 않는다.")
	void expireOrders_stopsProcessingWhenOrderUpdateFails() {

		// given
		LocalDateTime now = LocalDateTime.now();
		RuntimeException exception = new RuntimeException("주문 만료 처리 실패");

		given(orderRepository.expireCreatedOrders(eq(now), eq(OrderStatus.CREATED), eq(OrderStatus.EXPIRED)))
		    .willThrow(exception);

		// when & then
		assertThatThrownBy(() -> orderExpiryService.expireOrders(now)).isSameAs(exception);

		// then
		verifyNoInteractions(orderReservationRepository, orderGameSeatRepository);
	}
}
