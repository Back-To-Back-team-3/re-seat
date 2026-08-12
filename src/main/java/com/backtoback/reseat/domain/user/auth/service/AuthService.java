package com.backtoback.reseat.domain.user.auth.service;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.user.auth.dto.request.ReissueRequest;
import com.backtoback.reseat.domain.user.auth.dto.request.UserLoginRequest;
import com.backtoback.reseat.domain.user.auth.dto.response.TokenResponse;
import com.backtoback.reseat.domain.user.entity.RefreshToken;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import com.backtoback.reseat.domain.user.exception.DeleteUserException;
import com.backtoback.reseat.domain.user.exception.InvalidPasswordException;
import com.backtoback.reseat.domain.user.exception.InvalidTokenException;
import com.backtoback.reseat.domain.user.exception.SuspendedUserException;
import com.backtoback.reseat.domain.user.exception.UserNotFoundException;
import com.backtoback.reseat.domain.user.repository.RefreshTokenRepository;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;
import com.backtoback.reseat.global.security.JwtTokenProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public TokenResponse login(UserLoginRequest request) {
        User user
            = userRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() -> new UserNotFoundException("존재하지 않는 회원입니다."));

        // 비밀번호 일치 검증
        if (user.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidPasswordException("비밀번호가 올바르지 않습니다.");
        }

        // 비밀번호 검증 성공 후 계정 상태 확인
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new SuspendedUserException("이용이 정지된 계정입니다.");
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw new DeleteUserException("탈퇴 처리된 계정입니다.");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.USER_INACTIVE);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        LocalDateTime expiredAt = LocalDateTime.now().plusDays(14);

        RefreshToken dbRefreshToken
            = refreshTokenRepository
                .findByUser(user)
                .orElseGet(
                    () -> RefreshToken.builder().user(user).tokenValue(refreshToken).expiredAt(expiredAt).build()
                );

        dbRefreshToken.updateTokenValue(refreshToken, expiredAt);
        refreshTokenRepository.save(dbRefreshToken);

        return TokenResponse.builder().accessToken(accessToken).refreshToken(refreshToken).build();
    }

    @Transactional
    public TokenResponse reissue(ReissueRequest request) {
        String refreshToken = request.getRefreshToken();

        // 1. Refresh Token 유효성 및 만료 검증 (포맷 자체가 깨진 경우)
        if (refreshToken == null || !jwtTokenProvider.validateToken(refreshToken)) {
            throw new InvalidTokenException("유효하지 않거나 만료된 RefreshToken 입니다.");
        }

        // 2. Refresh Token에서 사용자 식별 정보(userId) 추출
        Long userId = jwtTokenProvider.getUserId(refreshToken);

        // 3. 존재하지 않는 회원 404 예외 처리 연동
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("존재하지 않는 회원입니다."));

        // [CodeRabbit 피드백 반영] DB에 저장된 실제 토큰과 클라이언트가 보낸 토큰이 일치하는지 검증
        // 토큰 재발급을 받으려는 사용자 상태 체크
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new SuspendedUserException("이용이 정지된 계정입니다");
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw new DeleteUserException("탈퇴 처리된 계정입니다.");
        }

        // [CodeRabbit 피드백 반영] DB에 저장된 실제 토큰과 클라이언트가 보낸 토큰이 유치하는지 검증
        RefreshToken dbRefreshToken
            = refreshTokenRepository.findByUser(user).orElseThrow(() -> new InvalidTokenException("유효하지 않은 인증 정보입니다."));

        if (!dbRefreshToken.getTokenValue().equals(refreshToken)) {
            throw new InvalidTokenException("토큰 정보가 일치하지 않습니다.");
        }

        // 4. 새로운 토큰 쌍 생성 및 토큰 회전(Rotation)
        String newAccessToken
            = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        LocalDateTime newExpiredAt = LocalDateTime.now().plusDays(14);

        // DB 최신화
        dbRefreshToken.updateTokenValue(newRefreshToken, newExpiredAt);

        // 성공 시 기존 구형 토큰 대신 새로 교체된 newRefreshToken을 리턴
        return TokenResponse.builder().accessToken(newAccessToken).refreshToken(newRefreshToken).build();
    }
}
