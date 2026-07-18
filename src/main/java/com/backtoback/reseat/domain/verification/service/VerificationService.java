package com.backtoback.reseat.domain.verification.service;

import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private final UserRepository userRepository;
    private final PortoneClient portoneClient;

    @Transactional
    public void verifyAndUpdateUser(Long userId, String impUid) {
        // 1. 현재 세션 유저 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        //postman 테스트용 주석처리 -> 실제 연동 시 주석 지우기
        //PortoneVerificationResponse portoneData = portoneClient.fetchVerificationInfo(impUid);
         //if (portoneData == null || portoneData.getCode() != 0 || portoneData.getResponse() == null) {
          //   throw new IllegalArgumentException("외부 본인인증 정보 조회에 실패했습니다.");
        // }
        // String certifiedCi = portoneData.getResponse().getUniqueKey();
        // String certifiedName = portoneData.getResponse().getName();

        //  포스트맨 테스트용 임시 하드코딩 데이터
        String certifiedCi = "POSTMAN_TEST_CI_12345";
        String certifiedName = "김재환";

        // 중복 가입 명의 예외 처리
        userRepository.findByCi(certifiedCi).ifPresent(existingUser -> {
            throw new IllegalStateException("이미 동일한 명의로 가입된 다른 계정이 존재합니다.");
        });

        // 비즈니스 로직 반영
        user.completeVerification(certifiedCi, certifiedName);
    }
}
