//로그인 입력 검증 DTO

package com.backtoback.reseat.domain.user.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserLoginRequest {
    private String email;
    private String password;
}
