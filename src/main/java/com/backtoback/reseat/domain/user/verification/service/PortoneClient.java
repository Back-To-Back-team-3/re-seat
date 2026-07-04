package com.backtoback.reseat.domain.user.verification.service;

import com.backtoback.reseat.domain.user.verification.dto.response.VerificationResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class PortoneClient {

    private final WebClient webClient = WebClient.builder()
            .baseUrl("http://api.iamport.kr") //포트원 표준 인증 베이스 URL
            .build();

    public VerificationResponse fetchVerificationInfo(String impUid, String accessToken){
        return webClient.get()
                .uri("/certifications" + impUid)
                .header("Authorization", accessToken)
                .retrieve()
                .bodyToMono(VerificationResponse.class)
                .block(); //동기식 변환 처리
    }
}
