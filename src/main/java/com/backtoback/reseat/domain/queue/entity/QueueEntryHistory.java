package com.backtoback.reseat.domain.queue.entity;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.user.entity.User;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "queue_entry_histories",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_queue_entry_histories_queue_key", columnNames = "queue_key") // Redis 대기열 키 중복 방지
        },
        indexes = {
                @Index(name = "idx_queue_entry_histories_game_user", columnList = "game_id, user_id"),  // 사용자 대기열 상태 조회
                @Index(name = "idx_queue_entry_histories_game_status", columnList = "game_id, status")  // 경기별 대기열 상태 조회
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QueueEntryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "game_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_queue_entry_histories_game")
    )
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_queue_entry_histories_user")
    )
    private User user;

    @Column(name = "queue_key", nullable = false, length = 100)
    private String queueKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private QueueEntryHistoryStatus status;

    @Column(name = "entered_at", updatable = false, nullable = false)
    private LocalDateTime enteredAt;

    @Column(name = "admitted_at")
    private LocalDateTime admittedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    public static QueueEntryHistory of(Game game, User user, String queueKey, LocalDateTime enteredAt) {
        QueueEntryHistory queueEntryHistory = new QueueEntryHistory();
        queueEntryHistory.game = game;
        queueEntryHistory.user = user;
        queueEntryHistory.queueKey = queueKey;
        queueEntryHistory.status = QueueEntryHistoryStatus.WAITING;
        queueEntryHistory.enteredAt = enteredAt;
        return queueEntryHistory;
    }
}
