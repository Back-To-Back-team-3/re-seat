package com.backtoback.reseat.domain.reservation.entity;

import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "reservation_seats",
        indexes = {
                @Index(name = "idx_reservation_seats_reservation", columnList = "reservation_id")
        }
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_reservation_seats_reservation"))
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_seat_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_reservation_seats_game_seat"))
    private GameSeat gameSeat;

    // 선점 시점 가격 스냅샷. GameSeat.price가 나중에 바뀌어도 예약 시점 가격 유지 (2차 재판매 대비).
    @Column(name = "price", nullable = false)
    private int price;

    // reservation_seats는 ERD상 created_at만 있고 updated_at은 없음 → BaseTimeEntity 상속 대신 이 필드만.
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private ReservationSeat(Reservation reservation, GameSeat gameSeat, int price) {
        this.reservation = reservation;
        this.gameSeat = gameSeat;
        this.price = price;
    }
}
