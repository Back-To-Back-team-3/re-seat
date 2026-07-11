package com.backtoback.reseat.domain.user.admin.dto.response;

import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class AdminUserResponse {

    private final Long id;
    private final String email;
    private final String name;
    private final String nickname;
    private final String phone;
    private final UserRole role;
    private final UserStatus status;
    private final boolean isVerified;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private AdminUserResponse(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.name = user.getName();
        this.nickname = user.getNickname();
        this.phone = user.getPhone();
        this.role = user.getRole();
        this.status = user.getStatus();
        this.isVerified = user.isVerified();
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
    }

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(user);
    }
}
