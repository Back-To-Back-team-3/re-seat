package com.backtoback.reseat.domain.payment.pg.toss;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

@Component
public class TossPaymentClient {

    //하드코딩 유출 방지를 위해 application.yaml 또는 환경변수에서 키 주입
    @Value("${toss.secret-key}")
    private String secretKey;

    @Value("${toss.confirm-url}")
    private String confirmUrl;

    private final WebClient webClient = WebClient.builder().build();

    public TossConfirmResponse confirm(String paymentKey, String orderId, Integer amount) {
        String encodedAuth = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

        return webClient.post()
                .uri(confirmUrl)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new TossConfirmRequest(paymentKey, orderId, amount))
                .retrieve()
                //토스 API가 4xx, 5xx 에러를 뱉을 때 에러 본문을 그대로 담아 예외로 던짐
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("응답 본문 없음")
                                .flatMap(body -> Mono.error(new IllegalStateException(
                                        "토스페이먼츠 결제 승인 API 호출 실패: " + body))))
                .bodyToMono(TossConfirmResponse.class)
                //무한 대기로 인한 스레드 고갈 방지를 위해 5초 타임아웃(Timeout) 적용
                .block(Duration.ofSeconds(5));
    }
}
