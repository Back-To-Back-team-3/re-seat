package com.backtoback.reseat.domain.user.service;

import com.backtoback.reseat.domain.user.dto.request.ReissueRequest;
import com.backtoback.reseat.domain.user.dto.request.UserLoginRequest;
import com.backtoback.reseat.domain.user.dto.response.TokenResponse;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.exception.InvalidPasswordException;
import com.backtoback.reseat.domain.user.exception.UserNotFoundException;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import com.backtoback.reseat.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

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

        // 필요 시 여기에 RefreshToken을 DB에 저장/업데이트하는 로직을 추가합니다.

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
    @Transactional
    public TokenResponse reissue(ReissueRequest request) {
        String refreshToken = request.getRefreshToken();

        //1.Refresh Token 유효성 및 만료 검증
        if(refreshToken == null || !jwtTokenProvider.validateToken(refreshToken)){
            throw new IllegalArgumentException("유효하지 않거나 만료된 RefreshToken 입니다.");
        }
        //2.Refresh Token에서 사용자 식별 정보 추출
        Long userId = jwtTokenProvider.getUserId(refreshToken);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다"));

        String newAccessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .build();
    }
}