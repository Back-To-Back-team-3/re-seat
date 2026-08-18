package com.backtoback.reseat.domain.user.verification.service;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import com.backtoback.reseat.domain.user.verification.dto.response.PortoneVerificationResponse;
import com.backtoback.reseat.domain.user.verification.dto.response.VerificationStatusResponse;
import com.backtoback.reseat.domain.user.verification.exception.VerificationException;
import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService {

    private final UserRepository userRepository;
    private final PortoneClient portoneClient;
    private final Environment environment;

    @Transactional(readOnly = true)
    public VerificationStatusResponse getVerificationStatus(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return VerificationStatusResponse.of(user.isVerified(), user.getName(), user.getPhone());
    }

    @Transactional
    public void verifyAndUpdateUser(Long userId, String impUid) {
        // 1. 현재 세션 유저 조회
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 이미 본인인증을 완료한 경우 외부 API 호출 전 사전 예외 처리
        if (user.isVerified()) {
            throw new IllegalStateException("이미 본인인증이 완료된 회원입니다.");
        }

        String certifiedCi;
        String certifiedName;
        String certifiedPhone;

        try {
            // 포트원 외부 API를 통해 실제 신원 정보 조회 시도
            PortoneVerificationResponse portoneData = portoneClient.fetchVerificationInfo(impUid);
            if (portoneData == null || portoneData.getCode() != 0 || portoneData.getResponse() == null) {
                throw new VerificationException(ErrorCode.VERIFICATION_FAILED);
            }
            certifiedCi = portoneData.getResponse().getUniqueKey();
            certifiedName = portoneData.getResponse().getName();
            certifiedPhone = portoneData.getResponse().getPhone();
        } catch (VerificationException e) {
            // 외부 조회 실패 시 본인인증 전용 예외(VerificationException)는 그대로 던져서 에러 처리합니다.
            throw e;
        } catch (Exception e) {
            // prod 프로필에서는 로컬 Fallback을 허용하지 않고 즉시 예외를 발생시킵니다.
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                log.error("[본인인증 실패] 운영(prod) 환경에서 포트원 실제 정보 조회 실패: {}", e.getMessage(), e);
                throw new VerificationException(ErrorCode.VERIFICATION_FAILED);
            }

            // 로컬/테스트 환경(local, test 등)에서만 Slf4j 경고 로깅 및 Fallback 적용
            log.warn("[본인인증 경고] 포트원 실제 정보 조회 실패 (로컬 Fallback 적용): {}", e.getMessage());
            certifiedCi = "FALLBACK_TEST_CI_" + userId; // 중복 가입 에러 방지용 고유 ID 매핑
            certifiedName = user.getName() != null && !user.getName().isEmpty() ? user.getName() : "인증회원";
            certifiedPhone = "010-9999-9999";
        }

        // 중복 가입 명의 예외 처리 (내 아이디 본인 정보인 경우는 패스)
        userRepository.findByCi(certifiedCi).ifPresent(existingUser -> {
            if (!existingUser.getId().equals(userId)) {
                throw new VerificationException(ErrorCode.VERIFICATION_DUPLICATE_CI);
            }
        });

        // 비즈니스 로직 반영 및 실명/전화번호 갱신
        user.completeVerification(certifiedCi, certifiedName, certifiedPhone);
    }
}
