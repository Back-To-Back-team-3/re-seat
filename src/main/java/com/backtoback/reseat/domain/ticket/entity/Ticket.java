package com.backtoback.reseat.domain.ticket.entity;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.order.entity.OrderItem;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.global.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 결제 성공 후 좌석별로 발급되는 티켓 엔티티
@Getter
@Entity
@Table(
    name = "tickets",
    indexes = {
        // 내 티켓 조회/상태별 필터에 쓰는 인덱스
        @Index(name = "idx_tickets_user_status", columnList = "user_id, status"),
        // 경기별 티켓 조회 인덱스
        @Index(name = "idx_tickets_game", columnList = "game_id")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ticket extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // PK, AUTO_INCREMENT

    @Column(name = "ticket_no", nullable = false, unique = true, length = 50)
    private String ticketNo; // 티켓 번호, 사용자에게 보여줄 외부용 ID

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "user_id", // 현재 티켓 소유자
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_tickets_user")
    )
    private User user;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "order_item_id", // 주문 항목, 주문 항목에서 티켓 하나만 발급되도록 보장
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_tickets_order_item")
    )
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "game_id", // 경기
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_tickets_game")
    )
    private Game game;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "game_seat_id", // 경기 좌석
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_tickets_game_seat")
    )
    private GameSeat gameSeat;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TicketStatus status; // 티켓 상태

    @Column(name = "qr_token", unique = true, length = 255)
    private String qrToken; // QR 토큰, 입장 검증에 사용하는 토큰 값

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt; // 발급 시각

    @Column(name = "used_at")
    private LocalDateTime usedAt; // 사용 시각(입장 완료)

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt; // 취소 시각

    public static Ticket issue(
        String ticketNo, // 외부 노출용 티켓 번호
        User user, // 현재 소유자
        OrderItem orderItem, // 티켓을 생성한 주문 항목
        GameSeat gameSeat, // 어떤 경기 좌석인지
        String qrToken // QR 토큰 값 (API 쪽에서 생성)
    ) {
        Ticket ticket = new Ticket();
        ticket.ticketNo = ticketNo;
        ticket.user = user;
        ticket.orderItem = orderItem;
        ticket.gameSeat = gameSeat;
        ticket.game = gameSeat.getGame(); // 정합성 보장
        ticket.status = TicketStatus.ISSUED;
        ticket.qrToken = qrToken;
        ticket.issuedAt = LocalDateTime.now();
        return ticket;
    }

    public void markUsed() {
        if (this.status == TicketStatus.CANCELED) {
            throw new IllegalStateException("취소된 티켓은 사용할 수 없습니다.");
        }
        this.status = TicketStatus.USED; // 입장 완료
        this.usedAt = LocalDateTime.now(); // 현재 시각 기록
    }

    public void cancel() {
        if (this.status == TicketStatus.USED) {
            throw new IllegalStateException("이미 사용된 티켓은 취소할 수 없습니다.");
        }
        this.status = TicketStatus.CANCELED; // 취소
        this.canceledAt = LocalDateTime.now(); // 현재 시각 기록
    }

    // 재판매 완료 시 소유자 변경
    // tickets.user_id를 구매자로 변경
    // ticket_transfer_histories 도메인에서 별도 이력 기록 예정
    public void changeOwner(User newOwner) {
        this.user = newOwner;
    }
}
