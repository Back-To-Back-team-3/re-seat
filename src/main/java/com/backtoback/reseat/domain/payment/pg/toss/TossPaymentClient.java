package com.backtoback.reseat.domain.payment.pg.toss;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.backtoback.reseat.domain.payment.pg.toss.dto.request.TossCancelRequest;
import com.backtoback.reseat.domain.payment.pg.toss.dto.request.TossConfirmRequest;
import com.backtoback.reseat.domain.payment.pg.toss.dto.response.TossPaymentResponse;
import com.backtoback.reseat.domain.payment.pg.toss.exception.TossApiException;
import com.backtoback.reseat.domain.payment.pg.toss.exception.TossPaymentStatusUnknownException;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class TossPaymentClient {

    private static final String CONFIRM_PATH = "/v1/payments/confirm";
    private static final String CANCEL_PATH = "/v1/payments/{paymentKey}/cancel";
    private static final String PAYMENT_PATH = "/v1/payments/{paymentKey}";
    private final WebClient webClient;
    private final String secretKey;
    private final String baseUrl;

    /** Toss 인증 정보와 HTTPS API 주소로 클라이언트를 생성한다. */
    public TossPaymentClient(@Value("${toss.secret-key}") String secretKey, @Value("${toss.base-url}") String baseUrl) {
        validateBaseUrl(baseUrl);
        this.webClient = WebClient.builder().build();
        this.secretKey = secretKey;
        this.baseUrl = baseUrl;
    }

    /** Toss 인증 정보가 평문으로 전송되지 않도록 HTTPS 주소만 허용한다. */
    private void validateBaseUrl(String baseUrl) {
        if (baseUrl == null || !"https".equalsIgnoreCase(URI.create(baseUrl).getScheme())) {
            throw new IllegalArgumentException("Toss API Base URL은 HTTPS만 사용할 수 있습니다.");
        }
    }

    /**
     * 승인 응답을 받지 못하면 결제를 재조회해 Toss의 확정 상태를 반환한다.
     */
    public TossPaymentResponse confirm(String paymentKey, String orderId, Integer amount) {
        try {
            return requestConfirm(paymentKey, orderId, amount);
        } catch (RuntimeException confirmException) {
            return requeryAfterConfirmFailure(paymentKey, confirmException);
        }
    }

    /**
     * Toss 결제 승인 API를 한 번 호출한다.
     */
    private TossPaymentResponse requestConfirm(String paymentKey, String orderId, Integer amount) {
        return webClient
            .post()
            .uri(baseUrl + CONFIRM_PATH)
            .header(HttpHeaders.AUTHORIZATION, authorizationHeader())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new TossConfirmRequest(paymentKey, orderId, amount))
            .retrieve()
            // 토스 API 오류 응답은 에러 본문을 포함해 호출부로 전파한다.
            .onStatus(
                HttpStatusCode::isError,
                response -> response
                    .bodyToMono(String.class)
                    .defaultIfEmpty("응답 본문 없음")
                    .flatMap(body -> Mono.error(new TossApiException("승인", response.statusCode().value(), body)))
            )
            .bodyToMono(TossPaymentResponse.class)
            // 외부 API 응답 지연으로 요청 스레드가 오래 묶이지 않게 최대 5초만 기다린다.
            .block(Duration.ofSeconds(5));
    }

    /**
     * 취소 응답을 받지 못하면 결제를 재조회해 Toss의 취소 완료 상태를 반환한다.
     */
    public TossPaymentResponse cancel(String paymentKey, String cancelReason) {
        return cancelWithoutIdempotencyKey(paymentKey, cancelReason, null);
    }

    /** 취소 금액을 지정해 Toss에 부분 취소를 요청한다. */
    public TossPaymentResponse cancel(String paymentKey, String cancelReason, Integer cancelAmount) {
        return cancelWithoutIdempotencyKey(paymentKey, cancelReason, cancelAmount);
    }

    /** 기존 취소 요청은 응답이 불명확하면 결제 단건 조회로 상태를 확인한다. */
    private TossPaymentResponse cancelWithoutIdempotencyKey(
        String paymentKey,
        String cancelReason,
        Integer cancelAmount
    ) {
        try {
            return requestCancel(paymentKey, cancelReason, cancelAmount, null);
        } catch (RuntimeException cancelException) {
            return requeryAfterFailure(paymentKey, cancelException, "취소");
        }
    }

    /** PG 멱등키를 사용해 Toss에 부분 취소를 요청한다. */
    public TossPaymentResponse cancel(
        String paymentKey,
        String cancelReason,
        Integer cancelAmount,
        String idempotencyKey
    ) {
        return requestCancel(paymentKey, cancelReason, cancelAmount, idempotencyKey);
    }

    /**
     * Toss 결제 취소 API를 한 번 호출한다.
     */
    private TossPaymentResponse requestCancel(
        String paymentKey,
        String cancelReason,
        Integer cancelAmount,
        String idempotencyKey
    ) {
        return webClient.post().uri(baseUrl + CANCEL_PATH, paymentKey).headers(headers -> {
            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader());
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                headers.set("Idempotency-Key", idempotencyKey);
            }
        })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new TossCancelRequest(cancelReason, cancelAmount))
            .retrieve()
            .onStatus(
                HttpStatusCode::isError,
                response -> response
                    .bodyToMono(String.class)
                    .defaultIfEmpty("응답 본문 없음")
                    .flatMap(body -> Mono.error(new TossApiException("취소", response.statusCode().value(), body)))
            )
            .bodyToMono(TossPaymentResponse.class)
            .block(Duration.ofSeconds(5));
    }

    public TossPaymentResponse getPayment(String paymentKey) {
        return webClient
            .get()
            .uri(baseUrl + PAYMENT_PATH, paymentKey)
            .header(HttpHeaders.AUTHORIZATION, authorizationHeader())
            .retrieve()
            .onStatus(
                HttpStatusCode::isError,
                response -> response
                    .bodyToMono(String.class)
                    .defaultIfEmpty("응답 본문 없음")
                    .flatMap(body -> Mono.error(new TossApiException("조회", response.statusCode().value(), body)))
            )
            .bodyToMono(TossPaymentResponse.class)
            .block(Duration.ofSeconds(5));
    }

    /**
     * 승인 실패 후 결제를 재조회하고 최종 상태를 확인할 수 없으면 복구 대상 예외를 던진다.
     */
    private TossPaymentResponse requeryAfterConfirmFailure(String paymentKey, RuntimeException confirmException) {
        TossPaymentResponse response = requeryAfterFailure(paymentKey, confirmException, "승인");

        if (response.isApproved() || response.isConfirmFailureStatus()) {
            log
                .info(
                    "토스 결제 승인 API 성공 응답 없이 재조회로 상태 확인 (paymentKey={}, tossStatus={})",
                    paymentKey,
                    response.getStatus()
                );
            return response;
        }

        log.warn("토스 결제 승인 실패 후 재조회했지만 최종 상태 확인 불가 (paymentKey={}, tossStatus={})", paymentKey, response.getStatus());
        throw new TossPaymentStatusUnknownException("승인", confirmException);
    }

    /**
     * API 호출 실패 후 결제 단건을 재조회하고, 재조회도 실패하면 상태 불명확 예외를 던진다.
     */
    private TossPaymentResponse requeryAfterFailure(
        String paymentKey,
        RuntimeException originalException,
        String operation
    ) {
        try {
            return getPayment(paymentKey);
        } catch (RuntimeException requeryException) {
            originalException.addSuppressed(requeryException);
            log.warn("토스 결제 {} API 호출 실패 후 재조회로 상태 확인 불가 (paymentKey={})", operation, paymentKey, originalException);
            throw new TossPaymentStatusUnknownException(operation, originalException);
        }
    }

    private String authorizationHeader() {
        // 토스 API는 표준 HTTP Basic Auth(Base64(아이디:비밀번호)) 규격을 쓰되
        // 아이디 자리에 시크릿 키를 넣고 비밀번호는 비워두는 방식을 사용한다.
        String encodedAuth = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        return "Basic " + encodedAuth;
    }
}
