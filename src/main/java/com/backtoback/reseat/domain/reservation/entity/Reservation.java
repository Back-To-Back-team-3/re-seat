package com.backtoback.reseat.domain.reservation.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.reservation.exception.InvalidReservationStatusException;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.global.common.BaseEntity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "reservations", uniqueConstraints = {
	@UniqueConstraint(name = "uk_reservations_no", columnNames = "reservation_no")
}, indexes = {
	@Index(name = "idx_reservations_user_status", columnList = "user_id, status"),
	@Index(name = "idx_reservations_status_expires", columnList = "status, hold_expires_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends BaseEntity {

	// 예약에 묶인 좌석 목록
	// Reservation.java 필드 선언부 — 컬렉션 초기화 보장 + cascade로 자식 저장
	@OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
	private final List<ReservationSeat> reservationSeats = new ArrayList<>();
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	// 외부 노출용 식별자 (내부 PK id는 순차 노출 위험). 형식: RSV-yyyyMMdd-{랜덤6} (생성은 C-4)
	@Column(name = "reservation_no", nullable = false, length = 50)
	private String reservationNo;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reservations_user"))
	private User user;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "game_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reservations_game"))
	private Game game;
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private ReservationStatus status;
	// 예약 단위 선점 만료. GameSeat.holdExpiresAt(좌석 단위)와는 별개. NOT NULL.
	@Column(name = "hold_expires_at", nullable = false)
	private LocalDateTime holdExpiresAt;

	@Builder
	private Reservation(String reservationNo, User user, Game game,
		ReservationStatus status, LocalDateTime holdExpiresAt) {
		this.reservationNo = reservationNo;
		this.user = user;
		this.game = game;
		this.status = (status != null) ? status : ReservationStatus.HOLDING;
		this.holdExpiresAt = holdExpiresAt;
	}

	/**
	 * HOLDING → CONFIRMED (결제 성공).
	 *
	 * @throws InvalidReservationStatusException HOLDING이 아닌 상태에서 호출 시
	 */
	public void confirm() {
		requireHolding();
		this.status = ReservationStatus.CONFIRMED;
	}

	/**
	 * HOLDING → CANCELED (선점 해제 / 주문·결제 취소).
	 *
	 * @throws InvalidReservationStatusException HOLDING이 아닌 상태에서 호출 시
	 */
	public void cancel() {
		requireHolding();
		this.status = ReservationStatus.CANCELED;
	}

	/**
	 * HOLDING → EXPIRED (TTL 만료).
	 * 만료 스케줄러(HoldExpiryScheduler)가 호출한다.
	 *
	 * @throws InvalidReservationStatusException HOLDING이 아닌 상태에서 호출 시
	 */
	public void expire() {
		requireHolding();
		this.status = ReservationStatus.EXPIRED;
	}

	/**
	 * 세 전이의 공통 가드.
	 * 현재 상태가 HOLDING이 아니면 전이를 차단한다.
	 */
	private void requireHolding() {
		if (this.status != ReservationStatus.HOLDING) {
			throw new InvalidReservationStatusException();
		}
	}

	/**
	 * 예약 상태 전이. 상태 전이 검증은 C-3에서 추가.
	 */
	public void updateStatus(ReservationStatus status) {
		this.status = status;
	}

	/**
	 * 연관관계 편의 메서드. ReservationSeat을 이 예약에 연결한다.
	 */
	public void addReservationSeat(ReservationSeat seat) {
		this.reservationSeats.add(seat);
		seat.assignReservation(this); // ReservationSeat 쪽 역방향 세팅
	}
}
