package com.backtoback.reseat.domain.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

class PaymentControllerDocsTest {

    @ParameterizedTest
    @ValueSource(
        strings = {
            "completePayment",
            "failPayment"
        }
    )
    void paymentFinalizationRequiresJwtSecurity(String methodName) {
        Method method = findMethod(methodName);

        assertThat(method.getAnnotation(Operation.class).security())
            .extracting(securityRequirement -> securityRequirement.name())
            .containsExactly("JWT Bearer Token");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "completePayment",
            "failPayment"
        }
    )
    void documentsQueueTokenAsConditionalHeader(String methodName) {
        Parameter queueToken
            = Arrays
                .stream(findMethod(methodName).getParameters())
                .map(parameter -> parameter.getAnnotation(Parameter.class))
                .filter(annotation -> annotation != null && annotation.description().contains("Queue-Token"))
                .findFirst()
                .orElseThrow();

        assertThat(queueToken.required()).isFalse();
        assertThat(queueToken.description()).contains("READY", "멱등 재호출");
    }

    private Method findMethod(String methodName) {
        return Arrays
            .stream(PaymentControllerDocs.class.getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals(methodName))
            .findFirst()
            .orElseThrow();
    }
}
