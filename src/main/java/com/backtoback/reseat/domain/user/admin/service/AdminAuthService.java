package com.backtoback.reseat.domain.user.admin.service;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.user.admin.dto.request.AdminLoginRequest;
import com.backtoback.reseat.domain.user.admin.dto.response.AdminLoginResponse;
import com.backtoback.reseat.domain.user.entity.RefreshToken;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import com.backtoback.reseat.domain.user.exception.AdminAccessRequiredException;
import com.backtoback.reseat.domain.user.exception.InactiveUserException;
import com.backtoback.reseat.domain.user.exception.InvalidPasswordException;
import com.backtoback.reseat.domain.user.exception.UserNotFoundException;
import com.backtoback.reseat.domain.user.repository.RefreshTokenRepository;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import com.backtoback.reseat.global.security.JwtTokenProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request) {
        User user = userRepository.findByEmail(request.email()).orElseThrow(UserNotFoundException::new);

        if (user.getPassword() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidPasswordException();
        }

        if (user.getRole() != UserRole.ADMIN) {
            throw new AdminAccessRequiredException();
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InactiveUserException();
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

        return AdminLoginResponse.of(accessToken, refreshToken, user);
    }
}
