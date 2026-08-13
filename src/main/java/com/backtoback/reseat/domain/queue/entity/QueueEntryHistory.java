package com.backtoback.reseat.domain.queue.entity;

import java.time.LocalDateTime;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.queue.exception.QueueInvalidStatusException;
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

/**
 * 경기별 사용자 대기열의 처리 상태와 상태 전환 시간을 저장하는 Entity
 */
@Entity
@Table(
    name = "queue_entry_histories",
    uniqueConstraints = {
        // 동일 경기와 사용자의 DB 대기 이력 중복 생성 방지
        @UniqueConstraint(
            name = "uk_queue_entry_histories_queue_key",
            columnNames = "queue_key"
        )
    },
    indexes = {
        // 경기와 사용자 기준 이력 조회
        @Index(
            name = "idx_queue_entry_histories_game_user",
            columnList = "game_id, user_id"
        ),
        // 경기별 상태 기준 이력 조회
        @Index(
            name = "idx_queue_entry_histories_game_status",
            columnList = "game_id, status"
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QueueEntryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "game_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_queue_entry_histories_game")
    )
    private Game game;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "user_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_queue_entry_histories_user")
    )
    private User user;

    @Column(
        name = "queue_key",
        nullable = false,
        length = 100
    )
    private String queueKey;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 20
    )
    private QueueEntryHistoryStatus status;

    @Column(
        name = "entered_at",
        nullable = false
    )
    private LocalDateTime enteredAt;

    @Column(name = "admitted_at")
    private LocalDateTime admittedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    /**
     * 경기와 사용자의 최초 대기열 진입 이력을 생성한다.
     *
     * @param game 대기열 진입 대상 경기
     * @param user 대기열에 진입한 사용자
     * @param queueKey 경기와 사용자를 조합한 고유 대기열 식별값
     * @param enteredAt DB 대기열 진입 이력 생성 시간
     * @return WAITING 상태로 생성된 대기열 진입 이력
     */
    public static QueueEntryHistory of(Game game, User user, String queueKey, LocalDateTime enteredAt) {
        QueueEntryHistory queueEntryHistory = new QueueEntryHistory();
        queueEntryHistory.game = game;
        queueEntryHistory.user = user;
        queueEntryHistory.queueKey = queueKey;
        queueEntryHistory.status = QueueEntryHistoryStatus.WAITING;
        queueEntryHistory.enteredAt = enteredAt;
        return queueEntryHistory;
    }

    /**
     * 대기 중이거나 입장 허용된 사용자의 상태를 취소로 변경한다.
     * <p>입장 허용 상태의 취소는 발급된 활성 입장 토큰을 함께 회수할 때 사용한다.</p>
     *
     * @param canceledAt 대기열 취소 시간
     */
    public void cancel(LocalDateTime canceledAt) {

        if (this.status != QueueEntryHistoryStatus.WAITING && this.status != QueueEntryHistoryStatus.ADMITTED) {
            throw new QueueInvalidStatusException("대기 중 혹은 입장 허용된 상태만 취소할 수 있습니다.");
        }

        this.status = QueueEntryHistoryStatus.CANCELED;
        this.canceledAt = canceledAt;
    }

    /**
     * 대기 중인 사용자의 상태를 입장 허용으로 변경한다.
     *
     * @param admittedAt 입장 허용 시간
     */
    public void admit(LocalDateTime admittedAt) {
        if (this.status != QueueEntryHistoryStatus.WAITING) {
            throw new QueueInvalidStatusException("대기 중인 상태만 입장 허용할 수 있습니다.");
        }

        this.status = QueueEntryHistoryStatus.ADMITTED;
        this.admittedAt = admittedAt;
    }

    /**
     * 취소된 사용자의 상태를 대기 중으로 변경한다.
     * <p>CANCELED 상태만 재진입할 수 있으며, 재진입 시간을 갱신하고
     * 기존 입장 허용시간과 취소 시간을 초기화한다.</p>
     *
     * @param enteredAt 대기열 재진입 시간
     */
    public void reenter(LocalDateTime enteredAt) {
        if (this.status != QueueEntryHistoryStatus.CANCELED) {
            throw new QueueInvalidStatusException("취소된 상태만 대기열에 재진입할 수 있습니다.");
        }

        this.status = QueueEntryHistoryStatus.WAITING;
        this.enteredAt = enteredAt;
        this.admittedAt = null;
        this.canceledAt = null;
    }
}
