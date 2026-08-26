package com.backtoback.reseat.domain.reservation.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.backtoback.reseat.domain.reservation.exception.MaxSeatCountExceededException;

@Disabled("테스트 제외")
class SeatCountPolicyTest {

    @Test
    @DisplayName("보유 1좌석 상태에서 1좌석 추가 요청은 상한 내이므로 통과한다")
    void should_pass_when_heldOneAndRequestedOne() {
        assertThatCode(() -> SeatCountPolicy.validateSeatCount(1, 1)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("보유 1좌석 상태에서 2좌석 추가 요청은 상한을 초과해 차단된다")
    void should_throwException_when_heldOneAndRequestedTwo() {
        assertThatThrownBy(() -> SeatCountPolicy.validateSeatCount(1, 2))
            .isInstanceOf(MaxSeatCountExceededException.class);
    }

    @Test
    @DisplayName("보유 2좌석 상태에서 1좌석 추가 요청은 상한을 초과해 차단된다")
    void should_throwException_when_heldTwoAndRequestedOne() {
        assertThatThrownBy(() -> SeatCountPolicy.validateSeatCount(2, 1))
            .isInstanceOf(MaxSeatCountExceededException.class);
    }
}
