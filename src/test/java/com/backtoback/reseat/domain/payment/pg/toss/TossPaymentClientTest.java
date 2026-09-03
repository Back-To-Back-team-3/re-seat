package com.backtoback.reseat.domain.payment.pg.toss;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TossPaymentClient Toss API 설정")
class TossPaymentClientTest {

    private static final String SECRET_KEY = "test-secret-key";

    @Nested
    @DisplayName("Toss API Base URL을 검증한다")
    class ValidateBaseUrl {

        @Test
        @DisplayName("HTTPS URL이면 클라이언트를 생성한다.")
        void createsClientWithHttpsBaseUrl() {
            assertThatCode(() -> new TossPaymentClient(SECRET_KEY, "https://api.tosspayments.com"))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("HTTP URL이면 인증 정보의 평문 전송을 막기 위해 생성을 거부한다.")
        void rejectsHttpBaseUrl() {
            assertThatThrownBy(() -> new TossPaymentClient(SECRET_KEY, "http://api.tosspayments.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Toss API Base URL은 HTTPS만 사용할 수 있습니다.");
        }
    }
}
