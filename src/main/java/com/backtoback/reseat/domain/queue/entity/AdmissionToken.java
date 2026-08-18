package com.backtoback.reseat.domain.queue.entity;

import java.time.LocalDateTime;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.queue.exception.QueueInvalidStatusException;
import com.backtoback.reseat.domain.queue.exception.QueueTokenAlreadyUsedException;
import com.backtoback.reseat.domain.queue.exception.QueueTokenBrowsingExpiredException;
import com.backtoback.reseat.domain.queue.exception.QueueTokenExpiredException;
import com.backtoback.reseat.domain.queue.exception.QueueTokenRevokedException;
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
 * 대기열을 통과한 사용자에게 발급한 입장 토큰과 유효 기간, 최초 좌석 탐색 상태를 저장하는 Entity
 */
@Entity
@Table(
    name = "admission_tokens",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_admission_tokens_token",
            columnNames = "token"
        )
    },
    indexes = {
        @Index(
            name = "idx_admission_tokens_game_user",
            columnList = "game_id, user_id"
        ),
        @Index(
            name = "idx_admission_tokens_status_expires",
            columnList = "status, expires_at"
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdmissionToken {

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
        foreignKey = @ForeignKey(name = "fk_admission_tokens_game")
    )
    private Game game;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "user_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_admission_tokens_user")
    )
    private User user;

    @Column(
        name = "token",
        nullable = false
    )
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 20
    )
    private AdmissionTokenStatus status;

    @Column(
        name = "issued_at",
        updatable = false,
        nullable = false
    )
    private LocalDateTime issuedAt;

    @Column(
        name = "expires_at",
        nullable = false
    )
    private LocalDateTime expiresAt;

    @Column(
        name = "seat_browsing_expires_at",
        nullable = false
    )
    private LocalDateTime seatBrowsingExpiresAt;

    @Column(name = "seat_browsing_completed_at")
    private LocalDateTime seatBrowsingCompletedAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /**
     * 대기열을 통과한 사용자에게 발급할 활성 입장 토큰을 생성한다.
     *
     * @param game 입장 대상 경기
     * @param user 입장 토큰을 발급받을 사용자
     * @param token 고유 Queue-Token 값
     * @param issuedAt 토큰 발급 시간
     * @param expiresAt 토큰 만료 시간
     * @param seatBrowsingExpiresAt 최초 좌석 탐색 만료 시간
     * @return ACTIVE 상태로 생성된 입장 토큰
     */
    public static AdmissionToken of(
        Game game,
        User user,
        String token,
        LocalDateTime issuedAt,
        LocalDateTime expiresAt,
        LocalDateTime seatBrowsingExpiresAt
    ) {
        AdmissionToken admissionToken = new AdmissionToken();
        admissionToken.game = game;
        admissionToken.user = user;
        admissionToken.token = token;
        admissionToken.status = AdmissionTokenStatus.ACTIVE;
        admissionToken.issuedAt = issuedAt;
        admissionToken.expiresAt = expiresAt;
        admissionToken.seatBrowsingExpiresAt = seatBrowsingExpiresAt;
        return admissionToken;
    }

    /**
     * 유효한 활성 입장 토큰을 사용 완료 상태로 전환한다.
     *
     * @param currentTime 토큰을 사용한 시간
     */
    public void use(LocalDateTime currentTime) {

        validateUsableAt(currentTime);

        this.status = AdmissionTokenStatus.USED;
        this.usedAt = currentTime;
    }

    /**
     * 만료 시간에 도달한 활성 입장 토큰을 만료 상태로 전환한다.
     *
     * @param currentTime 만료 여부를 판단할 시간
     */
    public void expire(LocalDateTime currentTime) {

        validateActiveStatus();

        if (!isExpiredAt(currentTime)) {
            throw new QueueInvalidStatusException("아직 만료되지 않은 입장 토큰 입니다.");
        }

        this.status = AdmissionTokenStatus.EXPIRED;
    }

    /**
     * 최초 좌석 선점 전 탐색 시간에 도달한 활성 입장 토큰을 탐색 만료 상태로 전환한다.
     *
     * @param currentTime 탐색 만료 여부를 판단할 시간
     */
    public void expireBrowsing(LocalDateTime currentTime) {

        validateActiveStatus();

        if (!isSeatBrowsingExpiredAt(currentTime)) {
            throw new QueueInvalidStatusException("아직 좌석 탐색 시간이 만료되지 않은 입장 토큰 입니다.");
        }

        this.status = AdmissionTokenStatus.BROWSING_EXPIRED;
    }

    /**
     * 최초 좌석 선점 완료 시간을 한 번만 기록한다.
     * <p>최초 선점 이후에는 좌석을 다시 선점해도 기존 완료 시간을 유지한다.</p>
     *
     * @param currentTime 최초 좌석 선점 완료 시간
     */
    public void completeSeatBrowsing(LocalDateTime currentTime) {

        if (this.seatBrowsingCompletedAt != null) {
            return;
        }

        validateActiveStatus();

        if (isExpiredAt(currentTime)) {
            throw new QueueTokenExpiredException();
        }

        if (isSeatBrowsingExpiredAt(currentTime)) {
            throw new QueueTokenBrowsingExpiredException();
        }

        this.seatBrowsingCompletedAt = currentTime;
    }

    /**
     * 활성 입장 토큰을 취소 상태로 전환한다.
     * <p>명시적 대기 취소 또는 연결 종료 유예시간 만료로
     * 토큰을 더 이상 사용할 수 없도록 변경한다.</p>
     */
    public void revoke() {

        validateActiveStatus();

        this.status = AdmissionTokenStatus.REVOKED;
    }

    /**
     * 기준 시간에 토큰이 만료되었는지 확인한다.
     * 만료 시간과 기준 시간이 같거나, 만료 시간이 더 이전이면 만료로 판단한다.
     *
     * @param currentTime 만료 여부를 판단할 시간
     * @return 토큰이 만료되었으면 true, 아직 유효하면 false
     */
    public boolean isExpiredAt(LocalDateTime currentTime) {

        return !this.expiresAt.isAfter(currentTime);
    }

    /**
     * 최초 좌석 선점 전 탐색 시간이 만료되었는지 확인한다.
     *
     * @param currentTime 탐색 만료 여부를 판단할 시간
     * @return 최초 선점 전 탐색 시간이 만료되었으면 true, 아직 유효하면 false
     */
    public boolean isSeatBrowsingExpiredAt(LocalDateTime currentTime) {

        return this.seatBrowsingCompletedAt == null && !this.seatBrowsingExpiresAt.isAfter(currentTime);
    }

    /**
     * 토큰이 사용 가능한 ACTIVE 상태인지 확인한다.
     */
    private void validateActiveStatus() {

        if (this.status == AdmissionTokenStatus.REVOKED) {
            throw new QueueTokenRevokedException();
        }

        if (this.status == AdmissionTokenStatus.USED) {
            throw new QueueTokenAlreadyUsedException();
        }

        if (this.status == AdmissionTokenStatus.EXPIRED) {
            throw new QueueTokenExpiredException();
        }

        if (this.status == AdmissionTokenStatus.BROWSING_EXPIRED) {
            throw new QueueTokenBrowsingExpiredException();
        }

        if (this.status != AdmissionTokenStatus.ACTIVE) {
            throw new QueueInvalidStatusException();
        }
    }

    /**
     * 토큰 상태와 만료 시간을 기준으로 해당 시간에 사용할 수 있는지 검증한다.
     *
     * @param currentTime 토큰 사용 가능 여부를 판단할 시간
     */
    public void validateUsableAt(LocalDateTime currentTime) {

        validateActiveStatus();

        if (isExpiredAt(currentTime)) {
            throw new QueueTokenExpiredException();
        }
    }
}
