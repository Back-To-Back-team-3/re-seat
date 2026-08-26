package com.backtoback.reseat.domain.user.verification.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.backtoback.reseat.domain.user.verification.dto.response.PortoneVerificationResponse;

import reactor.core.publisher.Mono;

@Component
public class PortoneClient {

    private final WebClient webClient;
    private final String issuerToken;

    public PortoneClient(
        @Value("${portone.base-url:https://api.iamport.kr}") String baseUrl,
        @Value("${portone.api-secret}") String issuerToken
    ) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.issuerToken = issuerToken;
    }

    public PortoneVerificationResponse fetchVerificationInfo(String impUid) {
        return webClient
            .get()
            .uri("/certifications/{impUid}", impUid)
            .header("Authorization", issuerToken)
            .retrieve()
            .onStatus(
                HttpStatusCode::isError,
                response -> Mono.error(new IllegalStateException("포트원 외부 API 호출 실패: 상태코드 " + response.statusCode()))
            )
            .bodyToMono(PortoneVerificationResponse.class)
            .block(Duration.ofSeconds(5));
    }
}
