package com.backtoback.reseat.domain.user.dto.response;

import com.backtoback.reseat.domain.user.entity.User;
import lombok.Getter;

@Getter
public class UserProfileResponse {

    private final Long id;
    private final String email;
    private final String name;
    private final String nickname;
    private final String phone;
    private final boolean isVerified;

    private UserProfileResponse(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.name = user.getName();
        this.nickname = user.getNickname();
        this.phone = user.getPhone();
        this.isVerified = user.isVerified();
    }

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(user);
    }
}
