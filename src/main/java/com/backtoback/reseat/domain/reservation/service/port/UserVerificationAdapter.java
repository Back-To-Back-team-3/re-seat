package com.backtoback.reseat.domain.reservation.service.port;

import org.springframework.stereotype.Component;

import com.backtoback.reseat.domain.user.verification.service.VerificationService;

import lombok.RequiredArgsConstructor;

/**
 * UserVerificationPort의 기본 구현체.
 * <p>user 도메인이 제공하는 본인인증 조회 서비스(VerificationService)에 위임한다.
 */
@Component
@RequiredArgsConstructor
public class UserVerificationAdapter implements UserVerificationPort {

    private final VerificationService verificationService;

    @Override
    public boolean isVerified(Long userId) {
        return verificationService.isVerified(userId);
    }
}
