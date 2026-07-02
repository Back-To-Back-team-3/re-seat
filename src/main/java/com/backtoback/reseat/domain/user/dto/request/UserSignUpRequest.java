//회원가입 입력 검증 DTO

package com.backtoback.reseat.domain.user.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserSignUpRequest {
    private String email;
    private String password;
    private String name;
    private String nickname;
    private String phone;
}
