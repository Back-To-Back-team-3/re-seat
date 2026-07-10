package com.backtoback.reseat.domain.seatinventory.entity;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.stadium.entity.Seat;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "game_seats",
        uniqueConstraints = {
                // over-booking 최후 물리 방어선: 한 경기에 같은 좌석 재고는 하나만
                @UniqueConstraint(name = "uk_game_seats_game_seat", columnNames = {"game_id", "seat_id"})
        },
        indexes = {
                @Index(name = "idx_game_seats_game_status_expires", columnList = "game_id, status, hold_expires_at"),
                @Index(name = "idx_game_seats_hold_expires", columnList = "status, hold_expires_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameSeat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_game_seats_game"))
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_game_seats_seat"))
    private Seat seat;

    // PricePolicy.calculate() 결과가 들어감 (C-2)
    @Column(name = "price", nullable = false)
    private int price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GameSeatStatus status;

    // 낙관적 락. Long/Integer만 가능(int/long은 null 표현 불가). C-4 락 전략 비교에서 사용.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // HELD 상태에서만 세팅. AVAILABLE/SOLD에서는 null.
    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt;

    // SOLD 전이 시점 기록.
    @Column(name = "sold_at")
    private LocalDateTime soldAt;

    @Builder
    private GameSeat(Game game, Seat seat, int price, GameSeatStatus status) {
        this.game = game;
        this.seat = seat;
        this.price = price;
        this.status = (status != null) ? status : GameSeatStatus.AVAILABLE;
    }

    /**
     * 경기 좌석을 예매 가능 상태로 되돌린다.
     *
     * <p>선점 해제 또는 주문 취소 시 좌석 상태를 AVAILABLE로 변경하고 선점 만료 시간을 초기화 한다.</p>
     */
    public void available() {

        this.status = GameSeatStatus.AVAILABLE;
        this.holdExpiresAt = null;
    }
}
