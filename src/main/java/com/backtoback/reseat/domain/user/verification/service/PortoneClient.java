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

    private final WebClient webClient = WebClient.builder().baseUrl("https://api.iamport.kr").build();
    // 하드코딩 유출 방지를 위해 application.yml 또는 환경변수에서 토큰 주입
    @Value("${portone.api-secret}")
    private String issuerToken;

    public PortoneVerificationResponse fetchVerificationInfo(String impUid) {
        return webClient
            .get()
            .uri("/certifications/" + impUid)
            .header("Authorization", issuerToken)
            .retrieve()
            // 외부 API가 4xx, 5xx 에러를 뱉을 때 예외 핸들링 가드 추가
            .onStatus(
                HttpStatusCode::isError,
                response -> Mono.error(new IllegalStateException("포트원 외부 API 호출 실패: 상태코드 " + response.statusCode()))
            )
            .bodyToMono(PortoneVerificationResponse.class)
            // 무한 대기로 인한 스레드 고갈 방지를 위해 5초 타임아웃(Timeout) 적용
            .block(Duration.ofSeconds(5));
    }
}
