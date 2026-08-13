package com.backtoback.reseat.domain.user.auth.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.backtoback.reseat.domain.user.auth.dto.request.UserLoginRequest;
import com.backtoback.reseat.domain.user.auth.dto.response.TokenResponse;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import com.backtoback.reseat.domain.user.exception.DeleteUserException;
import com.backtoback.reseat.domain.user.exception.InvalidPasswordException;
import com.backtoback.reseat.domain.user.exception.SuspendedUserException;
import com.backtoback.reseat.domain.user.exception.UserNotFoundException;
import com.backtoback.reseat.domain.user.repository.RefreshTokenRepository;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import com.backtoback.reseat.global.common.BaseUnitTest;
import com.backtoback.reseat.global.security.JwtTokenProvider;

class AuthServiceTest extends BaseUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("올바른 이메일과 비밀번호로 로그인하면 토큰을 발급한다")
    void login_Success() {
        // given
        UserLoginRequest request = new UserLoginRequest("user@test.com", "password123!");
        User user
            = User
                .builder()
                .id(1L)
                .email("user@test.com")
                .password("encodedPassword")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123!", "encodedPassword")).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(1L, "user@test.com", "USER")).thenReturn("mock-access-token");
        when(jwtTokenProvider.createRefreshToken(1L)).thenReturn("mock-refresh-token");
        when(refreshTokenRepository.findByUser(user)).thenReturn(Optional.empty());

        // when
        TokenResponse response = authService.login(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("mock-refresh-token");
    }

    @Test
    @DisplayName("존재하지 않는 회원 이메일로 로그인 시 예외가 발생한다")
    void login_UserNotFound() {
        // given
        UserLoginRequest request = new UserLoginRequest("none@test.com", "password123!");
        when(userRepository.findByEmail("none@test.com")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(UserNotFoundException.class)
            .hasMessage("존재하지 않는 회원입니다.");
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 예외가 발생한다")
    void login_InvalidPassword() {
        // given
        UserLoginRequest request = new UserLoginRequest("user@test.com", "wrongPassword");
        User user
            = User
                .builder()
                .id(1L)
                .email("user@test.com")
                .password("encodedPassword")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(InvalidPasswordException.class)
            .hasMessage("비밀번호가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("정지된 계정으로 로그인 시 SuspendedUserException이 발생한다")
    void login_SuspendedUser() {
        // given
        UserLoginRequest request = new UserLoginRequest("user@test.com", "password123!");
        User user
            = User
                .builder()
                .id(1L)
                .email("user@test.com")
                .password("encodedPassword")
                .role(UserRole.USER)
                .status(UserStatus.SUSPENDED)
                .build();

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123!", "encodedPassword")).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(SuspendedUserException.class)
            .hasMessage("이용이 정지된 계정입니다.");
    }

    @Test
    @DisplayName("탈퇴된 계정으로 로그인 시 DeleteUserException이 발생한다")
    void login_DeletedUser() {
        // given
        UserLoginRequest request = new UserLoginRequest("user@test.com", "password123!");
        User user
            = User
                .builder()
                .id(1L)
                .email("user@test.com")
                .password("encodedPassword")
                .role(UserRole.USER)
                .status(UserStatus.DELETED)
                .build();

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123!", "encodedPassword")).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(DeleteUserException.class)
            .hasMessage("탈퇴 처리된 계정입니다.");
    }
}
