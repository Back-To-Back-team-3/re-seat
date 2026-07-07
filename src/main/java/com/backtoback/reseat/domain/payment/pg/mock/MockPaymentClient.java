package com.backtoback.reseat.domain.payment.pg.mock;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class MockPaymentClient {

    public MockPaymentResult requestPayment() {
        LocalDateTime requestedAt = LocalDateTime.now();
        return MockPaymentResult.approved("mock_" + UUID.randomUUID(), requestedAt);
    }
}
