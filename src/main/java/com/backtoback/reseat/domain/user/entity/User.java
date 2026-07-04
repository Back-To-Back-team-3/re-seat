//회원 테이블 메인 엔티티

package com.backtoback.reseat.domain.user.entity;

import com.backtoback.reseat.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_email", columnNames = "email"),       // 이메일 중복 방지
               // @UniqueConstraint(name = "uk_users_nickname", columnNames = "nickname"), // 닉네임 중복 방지
                @UniqueConstraint(name = "uk_users_phone", columnNames = "phone")        // 전화번호 중복 방지
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = true)
    private String nickname;

    @Column(nullable = false, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    private String ci;


    @Column(nullable = false)
    private boolean isVerified = false;

    private String realName;

    @Builder
    public User(Long id, String email, String password, String name, String phone,
                String ci, boolean isVerified, String realName, UserRole role, UserStatus status) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.name = name;
        this.nickname = name;
        this.phone = phone;
        this.ci = ci;
        this.isVerified = isVerified;
        this.realName = realName;
        this.role = role != null ? role : UserRole.USER;
        this.status = status != null ? status : UserStatus.ACTIVE;
    }

    public void completeVerification(String ci, String realName) {
        if (this.isVerified) {
            throw new IllegalStateException("이미 본인인증이 완료된 회원입니다.");
        }
        this.ci = ci;
        this.realName = realName;
        this.isVerified = true;
    }
}
