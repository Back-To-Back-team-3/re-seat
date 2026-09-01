package com.backtoback.reseat.domain.ticket.entity;

import java.time.LocalDateTime;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.order.entity.OrderItem;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.ticket.exception.InvalidTicketStateException;
import com.backtoback.reseat.domain.ticket.exception.TicketAlreadyRefundedException;
import com.backtoback.reseat.domain.ticket.exception.TicketAlreadyUsedException;
import com.backtoback.reseat.domain.ticket.exception.TicketRefundFailedException;
import com.backtoback.reseat.domain.ticket.exception.TicketRefundInProgressException;
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

    // 환불 가능 기한 (경기 시작 24시간 전까지)
    private static final long REFUND_DEADLINE_HOURS_BEFORE_GAME = 24;

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

    // 티켓 취소(환불) 사유 유형 (USER_REFUND, ADMIN_FORCE_CANCEL, PAYMENT_CANCELED)
    @Enumerated(EnumType.STRING)
    @Column(
        name = "cancel_reason",
        length = 30
    )
    private TicketCancelReason cancelReason;

    // 티켓 상세 취소 사유 (관리자 입력 상세 사유 등)
    @Column(name = "cancel_detail")
    private String cancelDetail;

    // 입장 검증용 QR 토큰
    @Column(name = "qr_token")
    private String qrToken;

    // 티켓 발급 시간
    @Column(
        name = "issued_at",
        nullable = false,
        updatable = false
    )
    private LocalDateTime issuedAt;

    // 티켓 사용(입장) 시간
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    // 관리자 강제 취소 집행 시각 (관리자 강제 취소 전용, 그 외에는 null)
    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    // 환불 요청 접수 시각 (ISSUED -> REFUND_PENDING 전이 시각, 재시도 시에도 유지)
    @Column(name = "refund_requested_at")
    private LocalDateTime refundRequestedAt;

    // 환불 확정 시각 (REFUND_PENDING -> REFUNDED 전이 시각)
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

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

    // 환불 가능 기한(경기 시작 24시간 전) 반환
    public LocalDateTime getRefundDeadline() {
        return this.game.getGameAt().minusHours(REFUND_DEADLINE_HOURS_BEFORE_GAME);
    }

    /**
     * 현재 시점에 사용자가 취소(환불)를 요청할 수 있는 상태인지 확인한다.
     * <p>ISSUED 상태이고 환불 기한이 지나지 않은 경우에만 true.</p>
     */
    public boolean isRefundable() {
        return this.status == TicketStatus.ISSUED && LocalDateTime.now().isBefore(getRefundDeadline());
    }

    // 티켓 입장(검표) 처리, ISSUED 상태의 티켓만 가능
    public void markEntered() {
        validateIssuedForTransition();
        this.status = TicketStatus.USED_ENTERED;
        this.usedAt = LocalDateTime.now();
    }

    // 경기 종료 후 미입장 자동 처리 배치용, ISSUED 상태의 티켓만 가능
    public void markNoShow() {
        validateIssuedForTransition();
        this.status = TicketStatus.USED_NO_SHOW;
    }

    /**
     * 환불(취소) 요청을 접수한다. ISSUED -> REFUND_PENDING.
     * <p>PG 호출 전에 먼저 호출해 "환불 진행 중" 상태를 기록한다.
     * 관리자 강제 취소 집행 시각(canceledAt)은 여기서 기록하지 않고,
     * 환불이 실제로 확정되는 {@link #completeRefund()}에서만 기록한다.</p>
     *
     * @param cancelReason 취소 사유 유형
     * @param cancelDetail 취소 상세 사유 (선택)
     */
    public void requestRefund(TicketCancelReason cancelReason, String cancelDetail) {
        if (cancelReason == null) {
            throw new IllegalArgumentException("cancelReason은 필수입니다.");
        }
        validateIssuedForTransition();

        this.status = TicketStatus.REFUND_PENDING;
        this.cancelReason = cancelReason;
        this.cancelDetail = cancelDetail;
        this.refundRequestedAt = LocalDateTime.now();
    }

    /**
     * PG 취소가 성공적으로 완료된 뒤 환불을 확정한다. REFUND_PENDING -> REFUNDED.
     */
    public void completeRefund() {
        if (this.status != TicketStatus.REFUND_PENDING) {
            throw new IllegalStateException("환불 대기 상태의 티켓만 환불 확정할 수 있습니다.");
        }
        this.status = TicketStatus.REFUNDED;
        this.refundedAt = LocalDateTime.now();
        // 관리자 강제 취소 집행 시각은 환불이 실제로 확정된 이 시점에만 기록한다.
        if (this.cancelReason == TicketCancelReason.ADMIN_FORCE_CANCEL) {
            this.canceledAt = LocalDateTime.now();
        }
    }

    /**
     * PG 취소가 실패한 경우 환불 실패로 전환한다. REFUND_PENDING -> REFUND_FAILED.
     * <p>좌석·주문·결제 상태는 변경하지 않는다.</p>
     */
    public void failRefund() {
        if (this.status != TicketStatus.REFUND_PENDING) {
            throw new IllegalStateException("환불 대기 상태의 티켓만 환불 실패 처리할 수 있습니다.");
        }
        this.status = TicketStatus.REFUND_FAILED;
    }

    /**
     * 환불 실패 이력을 재시도한다. REFUND_FAILED -> REFUND_PENDING.
     * <p>사용자가 직접 호출하는 재시도 API에서 쓰이므로, 대상이 아니면 일반 상태 전이 예외로 알린다.</p>
     */
    public void retryRefund() {
        if (this.status != TicketStatus.REFUND_FAILED) {
            throw new InvalidTicketStateException();
        }
        this.status = TicketStatus.REFUND_PENDING;
        this.refundRequestedAt = LocalDateTime.now();
    }

    /**
     * 결제 패키지 호환용 > 결제 전체 취소 시 함께 정리되는 다른 ISSUED 티켓을 즉시 환불 완료로 전환한다.
     * <p>{@code PaymentService#cancelRemainingIssuedTickets}가 호출하는 기존 계약을 유지하기 위한 메서드다.
     * 결제 패키지가 이 시점에는 이미 PG 전액 취소를 완료한 뒤이므로, REFUND_PENDING 단계 없이 바로 REFUNDED로 확정한다.
     * "티켓 1장 단위" 취소 흐름(사용자·관리자 취소)은
     * {@link #requestRefund(TicketCancelReason, String)} / {@link #completeRefund()}를 사용한다.</p>
     *
     * @param cancelReason 취소 사유 유형
     * @deprecated 결제 패키지가 티켓 단위 부분 취소를 지원하도록 바뀌면 이 메서드는 제거 대상이다.
     */
    @Deprecated
    public void cancel(TicketCancelReason cancelReason) {
        if (cancelReason == null) {
            throw new IllegalArgumentException("cancelReason은 필수입니다.");
        }
        validateIssuedForTransition();

        this.status = TicketStatus.REFUNDED;
        this.cancelReason = cancelReason;
        this.refundRequestedAt = LocalDateTime.now();
        this.refundedAt = LocalDateTime.now();
    }

    /**
     * ISSUED 상태에서만 가능한 동작(입장, 환불 요청)을 검증한다.
     * <p>이미 진행 중이거나 종결된 상태면 구체적인 원인을 구분해 예외를 던진다.</p>
     */
    private void validateIssuedForTransition() {
        if (this.status == TicketStatus.ISSUED) {
            return;
        }
        if (this.status == TicketStatus.REFUND_PENDING) {
            throw new TicketRefundInProgressException();
        }
        if (this.status == TicketStatus.REFUND_FAILED) {
            throw new TicketRefundFailedException();
        }
        if (this.status == TicketStatus.REFUNDED) {
            throw new TicketAlreadyRefundedException();
        }
        // USED_ENTERED, USED_NO_SHOW
        throw new TicketAlreadyUsedException();
    }
}
