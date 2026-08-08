package com.backtoback.reseat.domain.seatinventory.entity;

import java.time.LocalDateTime;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.seatinventory.exception.InvalidStateTransitionException;
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

    /**
     * 경기별 판매 가격 (원).
     * <p>
     * PricePolicy.calculate() 결과가 들어감
     * 구역 성인 기본가(요일 반영) × 시기 배수까지만 반영되어 있다.
     */
    @Column(name = "price", nullable = false)
    private int price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GameSeatStatus status;

    /**
     * 낙관적 락 버전.
     *
     * <p>생성 시 Hibernate가 0으로 자동 세팅한다. 빌더에서 다루지 않는다.
     * 실제 활용은 C-4 좌석 선점(HOLD) 동시성 제어에서 이뤄진다.
     */
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

    /**
     * AVAILABLE → HELD (선점).
     * <p>
     * 선점 만료 시각(hold_expires_at)을 상태 전이와 원자적으로 함께 세팅한다.
     * 한쪽만 바뀌면 정합이 깨지므로 반드시 같은 메서드에서 처리한다.
     *
     * @throws InvalidStateTransitionException AVAILABLE이 아닌 상태에서 호출 시
     */
    public void hold(LocalDateTime holdExpiresAt) {
        if (this.status != GameSeatStatus.AVAILABLE) {
            throw new InvalidStateTransitionException();
        }
        this.status = GameSeatStatus.HELD;
        this.holdExpiresAt = holdExpiresAt;
    }

    /**
     * HELD → AVAILABLE (선점 해제 / TTL 만료).
     * <p>
     * 기존 available()과 달리 출발 상태(HELD)를 검증한다.
     * 만료 시각을 함께 초기화해 정합을 유지한다.
     *
     * @throws InvalidStateTransitionException HELD가 아닌 상태에서 호출 시
     */
    public void release() {
        if (this.status != GameSeatStatus.HELD) {
            throw new InvalidStateTransitionException();
        }
        this.status = GameSeatStatus.AVAILABLE;
        this.holdExpiresAt = null;
    }

    /**
     * HELD → SOLD (결제 성공).
     * <p>
     * 판매 확정 시 만료 시각은 더 이상 의미가 없으므로 null로 초기화한다.
     * sold_at은 현재 시각으로 기록한다.
     *
     * @throws InvalidStateTransitionException HELD가 아닌 상태에서 호출 시
     */
    public void sell() {
        if (this.status != GameSeatStatus.HELD) {
            throw new InvalidStateTransitionException();
        }
        this.status = GameSeatStatus.SOLD;
        this.holdExpiresAt = null;
        this.soldAt = LocalDateTime.now();
    }

    /**
     * 좌석 판매 상태 전이. 상태 전이 검증(AVAILABLE→HELD 등)은 C-3에서 도메인에 추가.
     */
    public void updateStatus(GameSeatStatus status) {
        this.status = status;
    }

    /**
     * 선점 만료 시각 세팅. 해제 시 null로 초기화.
     */
    public void updateHoldExpiresAt(LocalDateTime holdExpiresAt) {
        this.holdExpiresAt = holdExpiresAt;
    }
}
