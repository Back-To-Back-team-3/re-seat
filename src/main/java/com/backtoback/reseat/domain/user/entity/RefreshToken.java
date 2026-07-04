package com.backtoback.reseat.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "refresh_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //기존 유저 엔티티와 일대다 연관관계 매핑
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_value", nullable = false, unique = true)
    private String tokenValue;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt; // DB용 절대 만료 시각

    @Builder
    public RefreshToken(User user, String tokenValue, LocalDateTime expiredAt) {
        this.user = user;
        this.tokenValue = tokenValue;
        this.expiredAt = expiredAt;
    }
}
