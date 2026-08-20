package com.backtoback.reseat.domain.user.verification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.core.env.Environment;

import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import com.backtoback.reseat.domain.user.verification.dto.response.VerificationStatusResponse;
import com.backtoback.reseat.global.common.BaseUnitTest;
import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

class VerificationServiceTest extends BaseUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PortoneClient portoneClient;

    @Mock
    private Environment environment;

    @InjectMocks
    private VerificationService verificationService;

    @Test
    @DisplayName("getVerificationStatus는 회원의 본인인증 상태를 반환한다")
    void getVerificationStatus_Success() {
        // given
        Long userId = 1L;
        User user = User.builder().id(userId).email("user@test.com").name("홍길동").isVerified(true).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when
        VerificationStatusResponse response = verificationService.getVerificationStatus(userId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.isVerified()).isTrue();
    }

    @Test
    @DisplayName("isVerified는 회원의 본인인증 여부를 boolean으로 반환한다")
    void isVerified_Success() {
        // given
        Long userId = 1L;
        User user = User.builder().id(userId).email("user@test.com").name("홍길동").isVerified(false).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when
        boolean result = verificationService.isVerified(userId);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 회원 조회 시 USER_NOT_FOUND 예외가 발생한다")
    void userNotFound_ThrowsException() {
        // given
        Long userId = 999L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> verificationService.getVerificationStatus(userId))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }
}
