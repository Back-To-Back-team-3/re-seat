package com.backtoback.reseat.domain.user.verification.service;


import com.backtoback.reseat.domain.user.verification.dto.response.PortoneVerificationResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class PortoneClient {

    private final WebClient webClient = WebClient.builder()
        .baseUrl("https://api.iamport.kr")
        .build();

    public PortoneVerificationResponse fetchVerificationInfo(String impUid) {
        String mockIssuerToken = "GET_PORTONE_TOKEN_HERE";

        return webClient.get()
            .uri("/certifications/" + impUid)
            .header("Authorization", mockIssuerToken)
            .retrieve()
            .bodyToMono(PortoneVerificationResponse.class)
            .block();
    }
}
