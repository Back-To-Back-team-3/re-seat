package com.backtoback.reseat.domain.user.dto.response;

import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
public class UserProfileResponse {
    private final Long id;
    private final String email;
    private final String name;
    private final String nickname;
    private final String phone;
    private final UserRole role;
    private final UserStatus status;
    private final boolean isVerified;
    private final String realName;

    @Builder
    public UserProfileResponse(Long id, String email, String name, String nickname, String phone,
                              UserRole role, UserStatus status, boolean isVerified, String realName) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.nickname = nickname;
        this.phone = phone;
        this.role = role;
        this.status = status;
        this.isVerified = isVerified;
        this.realName = realName;
    }

    public static UserProfileResponse from(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .nickname(user.getNickname())
                .phone(user.getPhone())
                .role(user.getRole())
                .status(user.getStatus())
                .isVerified(user.isVerified())
                .realName(user.getRealName())
                .build();
    }
}
