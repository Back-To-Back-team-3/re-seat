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
        name = "admission_tokens",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_admission_tokens_token", columnNames = "token")
        },
        indexes = {
                @Index(name = "idx_admission_tokens_game_user", columnList = "game_id, user_id"),
                @Index(name = "idx_admission_tokens_status_expires", columnList = "status, expires_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdmissionToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "game_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_admission_tokens_game")
    )
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_admission_tokens_user")
    )
    private User user;

    @Column(name = "token", nullable = false, length = 255)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AdmissionTokenStatus status;

    @Column(name = "issued_at", updatable = false, nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    public static AdmissionToken of(Game game, User user, String token, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        AdmissionToken admissionToken = new AdmissionToken();
        admissionToken.game = game;
        admissionToken.user = user;
        admissionToken.token = token;
        admissionToken.status = AdmissionTokenStatus.ACTIVE;
        admissionToken.issuedAt = issuedAt;
        admissionToken.expiresAt = expiresAt;
        return admissionToken;
    }
}
