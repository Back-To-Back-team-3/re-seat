package com.backtoback.reseat.domain.user.auth.service;

import com.backtoback.reseat.domain.user.auth.dto.request.ReissueRequest;
import com.backtoback.reseat.domain.user.auth.dto.request.UserLoginRequest;
import com.backtoback.reseat.domain.user.auth.dto.response.TokenResponse;
import com.backtoback.reseat.domain.user.entity.RefreshToken;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.exception.InvalidPasswordException;
import com.backtoback.reseat.domain.user.exception.UserNotFoundException;
import com.backtoback.reseat.domain.user.repository.RefreshTokenRepository;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import com.backtoback.reseat.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
        // 1. 이메일 존재 여부 확인
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UserNotFoundException("존재하지 않는 이메일입니다."));

        // 2. 비밀번호 일치 여부 확인 (BCrypt 매칭 필수)
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidPasswordException("비밀번호가 일치하지 않습니다.");
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        // [CodeRabbit 피드백 반영] 절대 만료 시각 계산 (14일 뒤)
        LocalDateTime expiredAt = LocalDateTime.now().plusDays(14);

        // [실제 엔티티 매핑 반영] findByUser 구조로 매핑하여 기존 토큰 갱신 또는 신규 저장
        refreshTokenRepository.findByUser(user)
                .ifPresentOrElse(
                        token -> token.updateTokenValue(refreshToken, expiredAt), // 기존 토큰이 있다면 값/시간 갱신
                        () -> refreshTokenRepository.save(
                                RefreshToken.builder()
                                        .user(user)
                                        .tokenValue(refreshToken)
                                        .expiredAt(expiredAt)
                                        .build()
                        )
                );

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Transactional
    public TokenResponse reissue(ReissueRequest request) {
        String refreshToken = request.getRefreshToken();

        // 1. Refresh Token 유효성 및 만료 검증 (포맷 자체가 깨진 경우)
        if (refreshToken == null || !jwtTokenProvider.validateToken(refreshToken)) {
            // 💡 추후 GlobalExceptionHandler에 InvalidTokenException(401) 매핑하는 걸 권장
            throw new IllegalArgumentException("유효하지 않거나 만료된 RefreshToken 입니다.");
        }

        // 2. Refresh Token에서 사용자 식별 정보(userId) 추출
        Long userId = jwtTokenProvider.getUserId(refreshToken);

        // 3. 존재하지 않는 회원 404 예외 처리 연동
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("존재하지 않는 회원입니다."));

        // [CodeRabbit 피드백 반영] DB에 저장된 실제 토큰과 클라이언트가 보낸 토큰이 유치하는지 검증
        RefreshToken dbRefreshToken = refreshTokenRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 인증 정보입니다."));

        if (!dbRefreshToken.getTokenValue().equals(refreshToken)) {
            throw new IllegalArgumentException("토큰 정보가 일치하지 않습니다.");
        }

        // 4. 새로운 토큰 쌍 생성 및 토큰 회전(Rotation)
        String newAccessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        LocalDateTime newExpiredAt = LocalDateTime.now().plusDays(14);

        // DB 최신화
        dbRefreshToken.updateTokenValue(newRefreshToken, newExpiredAt);

        // 성공 시 기존 구형 토큰 대신 새로 교체된 newRefreshToken을 리턴
        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }
}
