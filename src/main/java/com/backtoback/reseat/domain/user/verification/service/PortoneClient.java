package com.backtoback.reseat.domain.user.verification.service;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.backtoback.reseat.domain.user.verification.dto.response.PortoneTokenResponse;
import com.backtoback.reseat.domain.user.verification.dto.response.PortoneVerificationResponse;

import reactor.core.publisher.Mono;

@Component
public class PortoneClient {

    private final WebClient webClient;
    private final String apiKey;
    private final String apiSecret;

    public PortoneClient(
        @Value("${portone.base-url:https://api.iamport.kr}") String baseUrl,
        @Value("${portone.api-key:}") String apiKey,
        @Value("${portone.api-secret:}") String apiSecret
    ) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    private String getAccessToken() {
        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalStateException("포트원 API Key 또는 Secret이 설정되지 않았습니다.");
        }

        Map<String, String> body = Map.of("imp_key", apiKey, "imp_secret", apiSecret);
        PortoneTokenResponse tokenRes = webClient.post()
            .uri("/users/getToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .onStatus(
                HttpStatusCode::isError,
                res -> res.bodyToMono(String.class)
                    .flatMap(errorBody -> Mono.error(new IllegalStateException("포트원 토큰 발급 HTTP 오류 (" + res.statusCode() + "): " + errorBody)))
            )
            .bodyToMono(PortoneTokenResponse.class)
            .block(Duration.ofSeconds(5));

        if (tokenRes == null || tokenRes.getCode() != 0 || tokenRes.getResponse() == null) {
            String errorMsg = tokenRes != null ? tokenRes.getMessage() : "응답 없음";
            throw new IllegalStateException("포트원 토큰 발급 실패: " + errorMsg);
        }

        return tokenRes.getResponse().getAccessToken();
    }

    public PortoneVerificationResponse fetchVerificationInfo(String impUid) {
        String accessToken = getAccessToken();

        return webClient
            .get()
            .uri("/certifications/{impUid}", impUid)
            .header(HttpHeaders.AUTHORIZATION, accessToken)
            .retrieve()
            .onStatus(
                HttpStatusCode::isError,
                response -> response.bodyToMono(String.class)
                    .flatMap(errorBody -> Mono.error(new IllegalStateException("포트원 외부 API 호출 실패 (" + response.statusCode() + "): " + errorBody)))
            )
            .bodyToMono(PortoneVerificationResponse.class)
            .block(Duration.ofSeconds(5));
    }
}
