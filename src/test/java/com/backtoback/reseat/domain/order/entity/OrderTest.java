package com.backtoback.reseat.domain.order.entity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.backtoback.reseat.domain.order.exception.InvalidOrderStatusException;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.user.entity.User;

@DisplayName("Order 상태 전이")
public class OrderTest {

	private static final String ORDER_NO = "ORD-TEST-000001";
	private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 7, 24, 2, 0);
	private static final LocalDateTime PAYMENT_DEADLINE = CREATED_AT.plusMinutes(8);
	private static final int TOTAL_AMOUNT = 34_000;

	private Order createdOrder() {
		return Order.of(
			ORDER_NO,
			mock(User.class),
			mock(Reservation.class),
			TOTAL_AMOUNT,
			PAYMENT_DEADLINE);
	}

	@Test
	@DisplayName("CREATED 주문을 결제 완료 처리하면 PAID 상태가 된다.")
	void paid_changesStatusToPaid() {

		Order order = createdOrder();

		order.paid();

		assertThat(order.getStatus())
			.isEqualTo(OrderStatus.PAID);
	}

	@Test
	@DisplayName("CREATED 주문을 결제 기한 만료 처리하면 EXPIRED 상태가 된다.")
	void expired_changesStatusToExpired() {

		Order order = createdOrder();

		order.expired();

		assertThat(order.getStatus())
			.isEqualTo(OrderStatus.EXPIRED);
	}

	@Test
	@DisplayName("CREATED 주문을 취소 처리하면 CANCELED 상태가 된다.")
	void cancel_changesStatusToCanceled() {

		Order order = createdOrder();

		order.cancel();

		assertThat(order.getStatus())
			.isEqualTo(OrderStatus.CANCELED);
	}

	@Test
	@DisplayName("CANCELED 주문을 결제 완료 처리하면 예외가 발생한다.")
	void paid_throwsExceptionWhenStatusIsNotCreated() {

		Order order = createdOrder();
		order.cancel();

		assertThatThrownBy(order::paid)
			.isInstanceOf(InvalidOrderStatusException.class);
		assertThat(order.getStatus())
			.isEqualTo(OrderStatus.CANCELED);
	}

	@Test
	@DisplayName("PAID 주문을 결제 기한 만료 처리하면 예외가 발생한다.")
	void expired_throwsExceptionWhenStatusIsNotCreated() {

		Order order = createdOrder();
		order.paid();

		assertThatThrownBy(order::expired)
			.isInstanceOf(InvalidOrderStatusException.class);
		assertThat(order.getStatus())
			.isEqualTo(OrderStatus.PAID);
	}

	@Test
	@DisplayName("EXPIRED 주문을 취소 처리하면 예외가 발생한다.")
	void cancel_throwsExceptionWhenStatusIsNotCreated() {

		Order order = createdOrder();
		order.expired();

		assertThatThrownBy(order::cancel)
			.isInstanceOf(InvalidOrderStatusException.class);
		assertThat(order.getStatus())
			.isEqualTo(OrderStatus.EXPIRED);
	}
}
