package com.backtoback.reseat.domain.payment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.backtoback.reseat.domain.payment.service.PaymentService;
import com.backtoback.reseat.domain.queue.exception.QueueTokenRequiredException;
import com.backtoback.reseat.global.exception.GlobalExceptionHandler;
import com.backtoback.reseat.global.security.CustomUserDetails;

@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PaymentControllerTest {

    private static final String IDEMPOTENCY_KEY = "idempotency-key";
    private static final String QUEUE_TOKEN = "queue-token";

    private static final String PAYMENT_REQUEST_BODY = """
        {
          "orderId": 1001
        }
        """;
    private static final String PAYMENT_COMPLETE_REQUEST_BODY = """
        {
          "paymentKey": "tgen_20260725120000AbCdE",
          "orderId": "ORD-20260725-A1B2C3",
          "amount": 34000
        }
        """;
    private static final String PAYMENT_FAIL_REQUEST_BODY = """
        {
          "code": "PAY_PROCESS_CANCELED",
          "message": "사용자가 결제를 취소했습니다.",
          "orderId": "ORD-20260725-A1B2C3"
        }
        """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @BeforeEach
    void setUpAuthentication() {
        CustomUserDetails userDetails = CustomUserDetails.of(1L, "user@test.com", "USER");
        SecurityContextHolder
            .getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("결제 요청 API는 주문 결제 요청을 접수한다")
    class RequestPayment {

        @Test
        @DisplayName("Idempotency-Key 헤더가 없으면 400 IDEMPOTENCY_KEY_REQUIRED를 반환한다")
        void rejectsMissingIdempotencyKey() throws Exception {
            mockMvc
                .perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(PAYMENT_REQUEST_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REQUIRED"));

            verifyNoInteractions(paymentService);
        }

        @Test
        @DisplayName("Idempotency-Key 헤더가 공백이면 400 IDEMPOTENCY_KEY_REQUIRED를 반환한다")
        void rejectsBlankIdempotencyKey() throws Exception {
            mockMvc
                .perform(
                    post("/api/v1/payments")
                        .header("Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYMENT_REQUEST_BODY)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REQUIRED"));

            verifyNoInteractions(paymentService);
        }
    }

    @Nested
    @DisplayName("결제 승인 API는 Toss 인증 결과를 확정한다")
    class CompletePayment {

        @Test
        @DisplayName("Queue-Token 헤더를 결제 승인 서비스에 전달한다")
        void passesQueueTokenToPaymentService() throws Exception {
            mockMvc
                .perform(
                    post("/api/v1/payments/{paymentId}/complete", 1001L)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header("Queue-Token", QUEUE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYMENT_COMPLETE_REQUEST_BODY)
                )
                .andExpect(status().isOk());

            verify(paymentService).completePayment(eq(1L), eq(1001L), eq(IDEMPOTENCY_KEY), eq(QUEUE_TOKEN), any());
        }

        @Test
        @DisplayName("Queue-Token 헤더가 없으면 403 QUEUE_TOKEN_REQUIRED를 반환한다")
        void rejectsMissingQueueToken() throws Exception {
            when(paymentService.completePayment(eq(1L), eq(1001L), eq(IDEMPOTENCY_KEY), isNull(), any()))
                .thenThrow(new QueueTokenRequiredException());

            mockMvc
                .perform(
                    post("/api/v1/payments/{paymentId}/complete", 1001L)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYMENT_COMPLETE_REQUEST_BODY)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("QUEUE_TOKEN_REQUIRED"));
        }

        @Test
        @DisplayName("Idempotency-Key 헤더가 없으면 400 IDEMPOTENCY_KEY_REQUIRED를 반환한다")
        void rejectsMissingIdempotencyKey() throws Exception {
            mockMvc
                .perform(
                    post("/api/v1/payments/{paymentId}/complete", 1001L)
                        .header("Queue-Token", QUEUE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYMENT_COMPLETE_REQUEST_BODY)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REQUIRED"));

            verifyNoInteractions(paymentService);
        }

        @Test
        @DisplayName("Idempotency-Key 헤더가 공백이면 400 IDEMPOTENCY_KEY_REQUIRED를 반환한다")
        void rejectsBlankIdempotencyKey() throws Exception {
            mockMvc
                .perform(
                    post("/api/v1/payments/{paymentId}/complete", 1001L)
                        .header("Idempotency-Key", "   ")
                        .header("Queue-Token", QUEUE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYMENT_COMPLETE_REQUEST_BODY)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REQUIRED"));

            verifyNoInteractions(paymentService);
        }
    }

    @Nested
    @DisplayName("결제 실패 API는 Toss 인증 실패 결과를 반영한다")
    class FailPayment {

        @Test
        @DisplayName("Queue-Token 헤더를 결제 실패 서비스에 전달한다")
        void passesQueueTokenToPaymentService() throws Exception {
            mockMvc
                .perform(
                    post("/api/v1/payments/{paymentId}/fail", 1001L)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header("Queue-Token", QUEUE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYMENT_FAIL_REQUEST_BODY)
                )
                .andExpect(status().isOk());

            verify(paymentService).failPayment(eq(1L), eq(1001L), eq(IDEMPOTENCY_KEY), eq(QUEUE_TOKEN), any());
        }

        @Test
        @DisplayName("Queue-Token 헤더가 없으면 403 QUEUE_TOKEN_REQUIRED를 반환한다")
        void rejectsMissingQueueToken() throws Exception {
            when(paymentService.failPayment(eq(1L), eq(1001L), eq(IDEMPOTENCY_KEY), isNull(), any()))
                .thenThrow(new QueueTokenRequiredException());

            mockMvc
                .perform(
                    post("/api/v1/payments/{paymentId}/fail", 1001L)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYMENT_FAIL_REQUEST_BODY)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("QUEUE_TOKEN_REQUIRED"));
        }

        @Test
        @DisplayName("Idempotency-Key 헤더가 없으면 400 IDEMPOTENCY_KEY_REQUIRED를 반환한다")
        void rejectsMissingIdempotencyKey() throws Exception {
            mockMvc
                .perform(
                    post("/api/v1/payments/{paymentId}/fail", 1001L)
                        .header("Queue-Token", QUEUE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYMENT_FAIL_REQUEST_BODY)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REQUIRED"));

            verifyNoInteractions(paymentService);
        }

        @Test
        @DisplayName("Idempotency-Key 헤더가 공백이면 400 IDEMPOTENCY_KEY_REQUIRED를 반환한다")
        void rejectsBlankIdempotencyKey() throws Exception {
            mockMvc
                .perform(
                    post("/api/v1/payments/{paymentId}/fail", 1001L)
                        .header("Idempotency-Key", "   ")
                        .header("Queue-Token", QUEUE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYMENT_FAIL_REQUEST_BODY)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REQUIRED"));

            verifyNoInteractions(paymentService);
        }
    }
}
