package com.backtoback.reseat.domain.ticket.entity;

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

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "tickets",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_tickets_no", columnNames = "ticket_no"),
        @UniqueConstraint(name = "uk_tickets_order_item", columnNames = "order_item_id"),
        @UniqueConstraint(name = "uk_tickets_game_seat", columnNames = "game_seat_id"),
        @UniqueConstraint(name = "uk_tickets_qr_token", columnNames = "qr_token")
    },
    indexes = {
        @Index(name = "idx_tickets_user_status", columnList = "user_id, status"),
        @Index(name = "idx_tickets_game", columnList = "game_id")
    }
)
public class Ticket extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_no", nullable = false, length = 50)
    private String ticketNo; // 외부 노출 티켓 번호 (예: TKT-20260711-000001)

    // 현재 티켓 소유자 (재판매 완료 시 새로운 소유자로 변경 가능)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "user_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_tickets_user")
    )
    private User user;

    // 주문 항목 1개당 티켓 1장
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "order_item_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_tickets_order_item")
    )
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "game_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_tickets_game")
    )
    private Game game;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "game_seat_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_tickets_game_seat")
    )
    private GameSeat gameSeat;

    // 티켓 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TicketStatus status;

    // 취소 유형
    // USER_REFUND / ADMIN_FORCE_CANCEL / PAYMENT_CANCELED
    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason", length = 30)
    private TicketCancelReason cancelReason;


    // 취소 상세 사유
    // 관리자 권한으로 취소
    @Column(name = "cancel_detail", length = 255)
    private String cancelDetail;

    // 입장 검표용 QR 토큰
    @Column(name = "qr_token", nullable = false, length = 255)
    private String qrToken;

    // 티켓 발급 시간
    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    // 티켓 사용 시간
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    // 티켓 취소 시간
    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    // 티켓 발급 팩토리 메서드
    // 결제 승인 완료 후 주문 항목 기준으로 티켓 생성
    public static Ticket issue(
        String ticketNo,
        User user,
        OrderItem orderItem,
        GameSeat gameSeat,
        String qrToken
    ) {
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

    // 티켓 검표 처리
    // ISSUED 상태 티켓만 USED로 변경
    public void markUsed() {
        validateUsable();
        this.status = TicketStatus.USED;
        this.usedAt = LocalDateTime.now();
    }

    // 일반 사용자 취소
    // 경기 시작 24시간 전까지만 취소 가능
    public void cancel(TicketCancelReason cancelReason) {
        validateCancelReason(cancelReason);
        validateCancelableByUser();

        this.status = TicketStatus.CANCELED;
        this.cancelReason = cancelReason;
        this.canceledAt = LocalDateTime.now();
    }

    // 관리자 강제 취소 (경기 직전이어도 가능)
    // 단, 이미 사용되었거나 이미 취소된 티켓은 취소할 수 없다.
    public void cancelByAdmin(String detail) {
        validateCancelableByAdmin();

        this.status = TicketStatus.CANCELED;
        this.cancelReason = TicketCancelReason.ADMIN_FORCE_CANCEL;
        this.cancelDetail = (detail != null && !detail.isBlank()) ? detail : "관리자 직권 취소";
        this.canceledAt = LocalDateTime.now();
    }

    // 재판매 완료 시 소유자 변경
    // 현재 소유자와 동일한 사용자로 변경 불가능
    public void changeOwner(User newOwner) {
        if (newOwner == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "newOwner는 필수입니다.");
        }

        validateTransferable();

        if (Objects.equals(this.user.getId(), newOwner.getId())) {
            throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "현재 소유자와 동일한 사용자로 변경할 수 없습니다."
            );
        }

        this.user = newOwner;
    }

    // 일반 사용자 기준 취소 마감 시간 경과 여부
    // 경기 시작 24시간 전 이후면 true
    public boolean isCancelDeadlinePassed() {
        return this.game.getGameAt().minusHours(24).isBefore(LocalDateTime.now());
    }

    // 일반 사용자 취소 가능 여부 검증
    // USED 불가 / CANCELED 불가 / 경기 시작 24시간 전 이후 불가
    // ISSUED 상태만 가능
    private void validateCancelableByUser() {
        if (this.status == TicketStatus.USED) {
            throw new BusinessException(ErrorCode.TICKET_ALREADY_USED);
        }

        if (this.status == TicketStatus.CANCELED) {
            throw new BusinessException(ErrorCode.TICKET_ALREADY_CANCELED);
        }

        if (isCancelDeadlinePassed()) {
            throw new BusinessException(ErrorCode.TICKET_CANCEL_DEADLINE_PASSED);
        }

        if (this.status != TicketStatus.ISSUED) {
            throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "취소 가능한 티켓 상태가 아닙니다. current=" + this.status
            );
        }
    }

    // 관리자 취소 가능 여부 검증
    // USED 불가 / CANCELED 불가
    // ISSUED 상태만 가능
    private void validateCancelableByAdmin() {
        if (this.status == TicketStatus.USED) {
            throw new BusinessException(ErrorCode.TICKET_ALREADY_USED);
        }

        if (this.status == TicketStatus.CANCELED) {
            throw new BusinessException(ErrorCode.TICKET_ALREADY_CANCELED);
        }

        if (this.status != TicketStatus.ISSUED) {
            throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "관리자 취소 가능한 티켓 상태가 아닙니다. current=" + this.status
            );
        }
    }

    // 검표 가능 여부 검증
    private void validateUsable() {
        if (this.status == TicketStatus.CANCELED) {
            throw new BusinessException(ErrorCode.TICKET_ALREADY_CANCELED);
        }

        if (this.status == TicketStatus.USED) {
            throw new BusinessException(ErrorCode.TICKET_ALREADY_USED);
        }

        if (this.status != TicketStatus.ISSUED) {
            throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "사용 처리 가능한 티켓 상태가 아닙니다. current=" + this.status
            );
        }
    }

    // 소유권 이전 가능 여부 검증
    // 재판매 완료 시 ISSUED 상태의 티켓만 소유자 변경 가능
    private void validateTransferable() {
        if (this.status == TicketStatus.CANCELED) {
            throw new BusinessException(ErrorCode.TICKET_ALREADY_CANCELED);
        }

        if (this.status == TicketStatus.USED) {
            throw new BusinessException(ErrorCode.TICKET_ALREADY_USED);
        }

        if (this.status != TicketStatus.ISSUED) {
            throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "소유권 변경 가능한 티켓 상태가 아닙니다. current=" + this.status
            );
        }
    }

    // 취소 사유 필수값 검증
    private void validateCancelReason(TicketCancelReason cancelReason) {
        if (cancelReason == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "cancelReason은 필수입니다.");
        }
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
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "ticketNo는 필수입니다.");
        }

        if (qrToken == null || qrToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "qrToken은 필수입니다.");
        }

        if (user == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "user는 필수입니다.");
        }

        if (orderItem == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "orderItem은 필수입니다.");
        }

        if (gameSeat == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "gameSeat는 필수입니다.");
        }
    }
}
