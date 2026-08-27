package com.backtoback.reseat.domain.user.verification.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import com.backtoback.reseat.domain.user.verification.dto.response.PortoneVerificationResponse;
import com.backtoback.reseat.domain.user.verification.dto.response.VerificationStatusResponse;
import com.backtoback.reseat.domain.user.verification.exception.VerificationException;
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

    private PortoneVerificationResponse createPortoneResponse(int code, String uniqueKey, String name, String phone) {
        PortoneVerificationResponse response = new PortoneVerificationResponse();
        ReflectionTestUtils.setField(response, "code", code);

        if (uniqueKey != null || name != null || phone != null) {
            PortoneVerificationResponse.ResponseData data = new PortoneVerificationResponse.ResponseData();
            ReflectionTestUtils.setField(data, "uniqueKey", uniqueKey);
            ReflectionTestUtils.setField(data, "name", name);
            ReflectionTestUtils.setField(data, "phone", phone);
            ReflectionTestUtils.setField(response, "response", data);
        }
        return response;
    }

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

    @Test
    @DisplayName("포트원 인증 성공 시 회원의 본인인증 정보가 정상 갱신된다")
    void verifyAndUpdateUser_Success() {
        // given
        Long userId = 1L;
        String impUid = "imp_123456";
        User user = User.builder().id(userId).email("user@test.com").name("임시이름").isVerified(false).build();

        PortoneVerificationResponse portoneResponse = createPortoneResponse(0, "CI_UNIQUE_123", "홍길동", "01012345678");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(portoneClient.fetchVerificationInfo(impUid)).thenReturn(portoneResponse);
        when(userRepository.findByCi("CI_UNIQUE_123")).thenReturn(Optional.empty());

        // when
        verificationService.verifyAndUpdateUser(userId, impUid);

        // then
        assertThat(user.isVerified()).isTrue();
        assertThat(user.getCi()).isEqualTo("CI_UNIQUE_123");
        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(user.getPhone()).isEqualTo("01012345678");
    }

    @Test
    @DisplayName("이미 본인인증이 완료된 회원이 재인증 시도시 IllegalStateException이 발생한다")
    void verifyAndUpdateUser_AlreadyVerified_ThrowsIllegalStateException() {
        // given
        Long userId = 1L;
        User user = User.builder().id(userId).email("user@test.com").name("홍길동").isVerified(true).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> verificationService.verifyAndUpdateUser(userId, "imp_123456"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("이미 본인인증이 완료된 회원입니다.");
    }

    @Test
    @DisplayName("동일한 CI로 가입된 다른 회원이 이미 존재하면 VERIFICATION_DUPLICATE_CI 예외가 발생한다")
    void verifyAndUpdateUser_DuplicateCi_ThrowsVerificationException() {
        // given
        Long userId = 1L;
        Long otherUserId = 2L;
        String impUid = "imp_123456";
        String duplicateCi = "DUPLICATE_CI_KEY";

        User currentUser = User.builder().id(userId).email("user1@test.com").name("유저1").isVerified(false).build();
        User existingUser = User.builder().id(otherUserId).email("user2@test.com").name("유저2").ci(duplicateCi).isVerified(true).build();

        PortoneVerificationResponse portoneResponse = createPortoneResponse(0, duplicateCi, "유저1", "01012345678");

        when(userRepository.findById(userId)).thenReturn(Optional.of(currentUser));
        when(portoneClient.fetchVerificationInfo(impUid)).thenReturn(portoneResponse);
        when(userRepository.findByCi(duplicateCi)).thenReturn(Optional.of(existingUser));

        // when & then
        assertThatThrownBy(() -> verificationService.verifyAndUpdateUser(userId, impUid))
            .isInstanceOf(VerificationException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VERIFICATION_DUPLICATE_CI);
    }

    @Test
    @DisplayName("포트원 응답이 실패 코드(code != 0)인 경우 VERIFICATION_FAILED 예외가 발생한다")
    void verifyAndUpdateUser_PortoneErrorResponse_ThrowsVerificationException() {
        // given
        Long userId = 1L;
        String impUid = "imp_invalid";
        User user = User.builder().id(userId).email("user@test.com").isVerified(false).build();
        PortoneVerificationResponse errorResponse = createPortoneResponse(-1, null, null, null);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(portoneClient.fetchVerificationInfo(impUid)).thenReturn(errorResponse);

        // when & then
        assertThatThrownBy(() -> verificationService.verifyAndUpdateUser(userId, impUid))
            .isInstanceOf(VerificationException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VERIFICATION_FAILED);
    }

    @Test
    @DisplayName("운영(prod) 환경에서 포트원 외부 API 호출 예외 발생 시 VERIFICATION_FAILED 예외를 던진다")
    void verifyAndUpdateUser_ProdProfile_ExternalApiException_ThrowsVerificationException() {
        // given
        Long userId = 1L;
        String impUid = "imp_123456";
        User user = User.builder().id(userId).email("user@test.com").isVerified(false).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(portoneClient.fetchVerificationInfo(impUid)).thenThrow(new IllegalStateException("외부 통신 장애"));
        when(environment.acceptsProfiles(Profiles.of("prod"))).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> verificationService.verifyAndUpdateUser(userId, impUid))
            .isInstanceOf(VerificationException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VERIFICATION_FAILED);
    }
}
