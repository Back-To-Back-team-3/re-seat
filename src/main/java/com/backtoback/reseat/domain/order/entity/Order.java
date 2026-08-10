package com.backtoback.reseat.domain.order.entity;

import java.time.LocalDateTime;

import com.backtoback.reseat.domain.order.exception.InvalidOrderStatusException;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.global.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders", uniqueConstraints = {
    @UniqueConstraint(name = "uk_orders_no", columnNames = "order_no"),
    @UniqueConstraint(name = "uk_orders_reservation", columnNames = "reservation_id")
}, indexes = {
    @Index(name = "idx_orders_user_status", columnList = "user_id, status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 50)
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_orders_user"))
    private User user;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, foreignKey = @ForeignKey(name = "fk_orders_reservation"))
    private Reservation reservation;

    @Column(name = "total_amount", nullable = false)
    private int totalAmount;

    @Column(name = "payment_deadline", nullable = false)
    private LocalDateTime paymentDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    /**
     * 주문 Entity를 생성한다.
     *
     * @param orderNo     주문 번호
     * @param user        주문 사용자
     * @param reservation 주문으로 전환할 예약
     * @param totalAmount 총 주문 금액
     * @return CREATE 상태의 주문
     */
    public static Order of(
        String orderNo,
        User user,
        Reservation reservation,
        int totalAmount,
        LocalDateTime paymentDeadline) {
        Order order = new Order();
        order.orderNo = orderNo;
        order.user = user;
        order.reservation = reservation;
        order.totalAmount = totalAmount;
        order.paymentDeadline = paymentDeadline;
        order.status = OrderStatus.CREATED;
        return order;
    }

    /**
     * 주문을 취소 상태로 변경한다.
     *
     * <p>CREATED 상태의 주문만 CANCELED 상태로 변경할 수 있다.</p>
     */
    public void cancel() {

        validateCreatedStatus();
        this.status = OrderStatus.CANCELED;
    }

    /**
     * 주문을 결제 완료 상태로 변경한다.
     *
     * <p>CREATED 상태의 주문만 PAID 상태로 변경할 수 있다.</p>
     */
    public void paid() {

        validateCreatedStatus();
        this.status = OrderStatus.PAID;
    }

    /**
     * 주문을 결제 기한 만료 상태로 변경한다.
     *
     * <p>CREATED 상태의 주문만 EXPIRED 상태로 변경할 수 있다.</p>
     */
    public void expired() {

        validateCreatedStatus();
        this.status = OrderStatus.EXPIRED;
    }

    /**
     * 주문이 상태 전이 가능한 상태인지 검증한다.
     *
     * <p>CREATED 상태가 아닌 경우 예외가 발생한다.</p>
     */
    private void validateCreatedStatus() {
        if (this.status != OrderStatus.CREATED) {
            throw new InvalidOrderStatusException();
        }
    }

}
