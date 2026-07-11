//유저 정보 반환 DTO

package com.backtoback.reseat.domain.user.dto.response;

import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class UserSignUpResponse {
    private final Long userId;
    private final String email;
    private final String name;
    private final UserRole role;
    private final UserStatus status;

    //엔티티를 받아 응답 DTO로 변환해 주는 정적 팩토리 메서드
    public static UserSignUpResponse from(User user){
        return UserSignUpResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .status(user.getStatus())
                .build();

    }
}
