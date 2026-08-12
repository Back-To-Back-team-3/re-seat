package com.backtoback.reseat.domain.ticket.entity;

import java.time.LocalDateTime;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.order.entity.OrderItem;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.global.common.BaseEntity;
import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

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

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "tickets",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_tickets_no",
            columnNames = "ticket_no"
        ),
        @UniqueConstraint(
            name = "uk_tickets_order_item",
            columnNames = "order_item_id"
        ),
        @UniqueConstraint(
            name = "uk_tickets_game_seat",
            columnNames = "game_seat_id"
        ),
        @UniqueConstraint(
            name = "uk_tickets_qr_token",
            columnNames = "qr_token"
        )
	},
    indexes = {
        @Index(
            name = "idx_tickets_user_status",
            columnList = "user_id, status"
        ),
        @Index(
            name = "idx_tickets_game",
            columnList = "game_id"
        )
	}
)
public class Ticket extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 외부 노출용 티켓 번호
	@Column(
	    name = "ticket_no",
	    nullable = false,
	    length = 50
	)
	private String ticketNo;

	// 현재 티켓 소유자
	@ManyToOne(
	    fetch = FetchType.LAZY,
	    optional = false
	)
	@JoinColumn(
	    name = "user_id",
	    nullable = false,
	    foreignKey = @ForeignKey(name = "fk_tickets_user")
	)
	private User user;

	// orderItem 1개당 1장의 티켓만 발급
	@OneToOne(
	    fetch = FetchType.LAZY,
	    optional = false
	)
	@JoinColumn(
	    name = "order_item_id",
	    nullable = false,
	    foreignKey = @ForeignKey(name = "fk_tickets_order_item")
	)
	private OrderItem orderItem;

	// 경기
	@ManyToOne(
	    fetch = FetchType.LAZY,
	    optional = false
	)
	@JoinColumn(
	    name = "game_id",
	    nullable = false,
	    foreignKey = @ForeignKey(name = "fk_tickets_game")
	)
	private Game game;

	// 좌석
	@OneToOne(
	    fetch = FetchType.LAZY,
	    optional = false
	)
	@JoinColumn(
	    name = "game_seat_id",
	    nullable = false,
	    foreignKey = @ForeignKey(name = "fk_tickets_game_seat")
	)
	private GameSeat gameSeat;

	// 티켓 상태
	@Enumerated(EnumType.STRING)
	@Column(
	    name = "status",
	    nullable = false,
	    length = 20
	)
	private TicketStatus status;

	// 티켓 취소 사유 유형 (USER_REFUND, ADMIN_FORCE_CANCEL, PAYMENT_CANCELED)
	@Enumerated(EnumType.STRING)
	@Column(
	    name = "cancel_reason",
	    length = 30
	)
	private TicketCancelReason cancelReason;

	// 티켓 상세 취소 사유 (관리자 입력 상세 사유 등)
	@Column(
	    name = "cancel_detail",
	    length = 255
	)
	private String cancelDetail;

	// 입장 검증용 QR 토큰
	@Column(
	    name = "qr_token",
	    length = 255
	)
	private String qrToken;

	// 티켓 발급 시간
	@Column(
	    name = "issued_at",
	    nullable = false,
	    updatable = false
	)
	private LocalDateTime issuedAt;

	// 티켓 사용 시간
	@Column(name = "used_at")
	private LocalDateTime usedAt;

	// 티켓 취소 시간
	@Column(name = "canceled_at")
	private LocalDateTime canceledAt;

	public static Ticket issue(String ticketNo, User user, OrderItem orderItem, GameSeat gameSeat, String qrToken) {
		validateIssueParams(ticketNo, user, orderItem, gameSeat, qrToken);

		Ticket ticket = new Ticket();
		ticket.ticketNo = ticketNo;
		ticket.user = user;
		ticket.orderItem = orderItem;
		ticket.gameSeat = gameSeat;
		ticket.game = gameSeat.getGame();
		ticket.status = TicketStatus.ISSUED;
		ticket.qrToken = qrToken;
		ticket.issuedAt = LocalDateTime.now();
		return ticket;
	}

	// 티켓 발급 시 필수 파라미터 검증
	private static void validateIssueParams(
	    String ticketNo,
	    User user,
	    OrderItem orderItem,
	    GameSeat gameSeat,
	    String qrToken
	) {
		if (ticketNo == null || ticketNo.isBlank()) {
			throw new IllegalArgumentException("ticketNo는 필수입니다.");
		}
		if (qrToken == null || qrToken.isBlank()) {
			throw new IllegalArgumentException("qrToken은 필수입니다.");
		}
		if (user == null) {
			throw new IllegalArgumentException("user는 필수입니다.");
		}
		if (orderItem == null) {
			throw new IllegalArgumentException("orderItem은 필수입니다.");
		}
		if (gameSeat == null) {
			throw new IllegalArgumentException("gameSeat는 필수입니다.");
		}
	}

	// 티켓 사용 처리, ISSUED 상태의 티켓만 사용 가능
	public void markUsed() {
		validateStatus(TicketStatus.ISSUED);
		this.status = TicketStatus.USED;
		this.usedAt = LocalDateTime.now();
	}

	// 관리자 전용 직권 취소 처리
	// @param detail 관리자가 입력한 취소 상세 사유 (예: "매크로 부정 예매 탐지")

	// 티켓 취소 처리, ISSUED 상태의 티켓만 취소 가능
	public void cancel(TicketCancelReason cancelReason) {
		validateStatus(TicketStatus.ISSUED);

		if (cancelReason == null) {
			throw new IllegalArgumentException("cancelReason은 필수입니다.");
		}

		this.status = TicketStatus.CANCELED;
		this.cancelReason = cancelReason;
		this.canceledAt = LocalDateTime.now();
	}

	public void cancelByAdmin(String detail) {
		validateStatus(TicketStatus.ISSUED);
		this.status = TicketStatus.CANCELED;
		this.cancelReason = TicketCancelReason.ADMIN_FORCE_CANCEL;
		this.cancelDetail = (detail != null && !detail.isBlank()) ? detail : "관리자 직권 취소";
		this.canceledAt = LocalDateTime.now();
	}

	// 재판매 완료 시 소유자 변경
	public void changeOwner(User newOwner) {
		if (newOwner == null) {
			throw new IllegalArgumentException("newOwner는 필수입니다.");
		}
		validateStatus(TicketStatus.ISSUED);
		if (java.util.Objects.equals(this.user.getId(), newOwner.getId())) {
			throw new IllegalArgumentException("현재 소유자와 동일한 사용자로 변경할 수 없습니다.");
		}

		this.user = newOwner;
	}

	// 현재 티켓 상태가 기대 상태와 같은지 검증
	private void validateStatus(TicketStatus expected) {
		if (this.status == TicketStatus.CANCELED) {
			throw new BusinessException(ErrorCode.TICKET_ALREADY_CANCELED);
		}
		if (this.status == TicketStatus.USED) {
			throw new BusinessException(ErrorCode.TICKET_ALREADY_USED);
		}
		if (this.status != expected) {
			throw new BusinessException(
			    ErrorCode.INVALID_REQUEST,
			    "티켓 상태가 올바르지 않습니다. expected=" + expected + ", current=" + this.status
			);
		}
	}
}
