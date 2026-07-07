package com.backtoback.reseat.domain.order.entity;

import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "order_items",
        indexes = {
                @Index(name = "idx_order_items_order", columnList = "order_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "order_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_items_order")
    )
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "game_seat_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_items_game_seat")
    )
    private GameSeat gameSeat;

    @Column(nullable = false)
    private int price;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    public static OrderItem of(Order order, GameSeat gameSeat, int price) {
        OrderItem orderItem = new OrderItem();
        orderItem.order = order;
        orderItem.gameSeat = gameSeat;
        orderItem.price = price;
        orderItem.createdAt = LocalDateTime.now();
        return orderItem;
    }
}
