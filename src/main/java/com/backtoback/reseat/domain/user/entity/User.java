//회원 테이블 메인 엔티티

package com.backtoback.reseat.domain.user.entity;

import com.backtoback.reseat.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "ci", nullable = true)
    private String ci;

    @Column(name="is_verified", nullable = false)
    private boolean isVerified = false;

    @Column(name="real_name")
    private String realName;

    //JPA 엔티티가 Persist 되기 전 기본값 및 시간 자동 세팅
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if(this.role == null){
            this.role = UserRole.USER;
        }
        if(this.status == null){
            this.status = UserStatus.ACTIVE;
        }
    }

    //정보 수정 시 수정 일 업데이트 로직
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    public User(String email, String password, String name, String nickname, String phone,
                UserRole role, UserStatus status) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.nickname = nickname;
        this.phone = phone;
        this.role = role != null ? role : UserRole.USER;
        this.status = status != null ? status : UserStatus.ACTIVE;
    }

    public void completeVerification(String ci, String realName){
        if(this.isVerified){
            throw new IllegalStateException("이미 본인인증이 완료된 회원입니다");
        }
        this.ci = ci;
        this.realName = realName;
        this.isVerified = true;
    }
}
