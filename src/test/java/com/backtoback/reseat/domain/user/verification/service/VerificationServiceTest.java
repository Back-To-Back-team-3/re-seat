package com.backtoback.reseat.domain.user.verification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import com.backtoback.reseat.domain.user.verification.dto.response.PortoneVerificationResponse;
import com.backtoback.reseat.domain.user.verification.dto.response.VerificationStatusResponse;
import com.backtoback.reseat.domain.user.verification.exception.VerificationException;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private UserRepository userRepository;
    @Mock
    private PortoneClient portoneClient;
    @Mock
    private Environment environment;
    @InjectMocks
    private VerificationService verificationService;

    @Test
    @DisplayName("사용자 본인인증 상태를 성공적으로 조회한다")
    void getVerificationStatus_Success() {
        // given
        Long userId = 1L;
        User user = User.builder().id(userId).name("홍길동").phone("010-1234-5678").isVerified(true).build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        VerificationStatusResponse response = verificationService.getVerificationStatus(userId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.isVerified()).isTrue();
        assertThat(response.getName()).isEqualTo("홍길동");
        assertThat(response.getPhone()).isEqualTo("010-1234-5678");
    }

    @Test
    @DisplayName("포트원 API 정상 응답 시 사용자 본인인증 성공")
    void verifyAndUpdateUser_Success() throws Exception {
        // given
        Long userId = 1L;
        String impUid = "imp_123456";
        User user = User.builder().id(userId).email("test@example.com").build();

        String jsonResponse = """
            {
                "code": 0,
                "message": "성공",
                "response": {
                    "unique_key": "CI_12345",
                    "name": "홍길동",
                    "phone": "010-1234-5678"
                }
            }
            """;
        PortoneVerificationResponse response = objectMapper.readValue(jsonResponse, PortoneVerificationResponse.class);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(portoneClient.fetchVerificationInfo(impUid)).willReturn(response);
        given(userRepository.findByCi("CI_12345")).willReturn(Optional.empty());

        // when
        verificationService.verifyAndUpdateUser(userId, impUid);

        // then
        assertThat(user.getCi()).isEqualTo("CI_12345");
        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(user.getPhone()).isEqualTo("010-1234-5678");
        assertThat(user.isVerified()).isTrue();
    }

    @Test
    @DisplayName("prod 환경에서 포트원 API 예외 발생 시 Fallback 없이 VerificationException 발생")
    void verifyAndUpdateUser_Prod_Exception_ThrowsVerificationException() {
        // given
        Long userId = 1L;
        String impUid = "imp_123456";
        User user = User.builder().id(userId).email("test@example.com").build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(portoneClient.fetchVerificationInfo(impUid)).willThrow(new RuntimeException("Portone API Error"));
        given(environment.acceptsProfiles(any(Profiles.class))).willReturn(true); // prod profile active

        // when & then
        assertThatThrownBy(() -> verificationService.verifyAndUpdateUser(userId, impUid))
            .isInstanceOf(VerificationException.class);
    }

    @Test
    @DisplayName("local/test 환경에서 포트원 API 예외 발생 시 Fallback 데이터로 인증 성공")
    void verifyAndUpdateUser_Local_Exception_FallbackSuccess() {
        // given
        Long userId = 1L;
        String impUid = "imp_123456";
        User user = User.builder().id(userId).email("test@example.com").name("개발자").build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(portoneClient.fetchVerificationInfo(impUid))
            .willThrow(new RuntimeException("Portone API Connection Error"));
        given(environment.acceptsProfiles(any(Profiles.class))).willReturn(false); // non-prod profile
        given(userRepository.findByCi("FALLBACK_TEST_CI_1")).willReturn(Optional.empty());

        // when
        verificationService.verifyAndUpdateUser(userId, impUid);

        // then
        assertThat(user.getCi()).isEqualTo("FALLBACK_TEST_CI_1");
        assertThat(user.getName()).isEqualTo("개발자");
        assertThat(user.getPhone()).isEqualTo("010-9999-9999");
        assertThat(user.isVerified()).isTrue();
    }
}
