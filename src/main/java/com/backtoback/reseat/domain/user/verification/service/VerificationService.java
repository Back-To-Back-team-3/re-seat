package com.backtoback.reseat.domain.user.verification.service;

import com.backtoback.reseat.domain.user.verification.dto.response.VerificationResponse;
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

        // 2. 외부 대행사 단건 조회
        String mockIssuerToken = "GET_PORTONE_TOKEN_HERE";
        VerificationResponse portoneData = portoneClient.fetchVerificationInfo(impUid, mockIssuerToken);

        if (portoneData.getCode() != 0 || portoneData.getResponse() == null) {
            throw new IllegalArgumentException("외부 본인인증 정보 조회에 실패했습니다.");
        }

        String certifiedCi = portoneData.getResponse().getUnique_key();
        String certifiedName = portoneData.getResponse().getName();


        // 3. 중복 가입 명의 예외 처리 (정상적인 메서드 내부 스코프)
        userRepository.findByCi(certifiedCi).ifPresent(existingUser -> {
            throw new IllegalStateException("이미 동일한 명의로 가입된 다른 계정이 존재합니다.");
        });

        // 4. 비즈니스 로직 반영
        user.completeVerification(certifiedCi, certifiedName);
    }
}
