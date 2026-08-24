package com.backtoback.reseat.domain.game.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 예매 상태 전이 규칙 테스트.
 * <p>상태가 4개이므로 전이 조합 16건을 전수 검증한다.
 */
class BookingStatusTest {

    @ParameterizedTest(name = "{0} → {1} 전이는 {2}")
    @CsvSource(
        {
            // SCHEDULED
            "SCHEDULED, SCHEDULED, false",
            "SCHEDULED, OPEN,      true",
            "SCHEDULED, CLOSED,    false",
            "SCHEDULED, CANCELLED, true",
            // OPEN
            "OPEN,      SCHEDULED, false",
            "OPEN,      OPEN,      false",
            "OPEN,      CLOSED,    true",
            "OPEN,      CANCELLED, true",
            // CLOSED — 재오픈 불가, 경기 취소로만 빠져나간다
            "CLOSED,    SCHEDULED, false",
            "CLOSED,    OPEN,      false",
            "CLOSED,    CLOSED,    false",
            "CLOSED,    CANCELLED, true",
            // CANCELLED — 종단 상태
            "CANCELLED, SCHEDULED, false",
            "CANCELLED, OPEN,      false",
            "CANCELLED, CLOSED,    false",
            "CANCELLED, CANCELLED, false"
        }
    )
    @DisplayName("예매 상태 전이 허용 여부가 규칙대로 판정된다")
    void should_judgeTransition_when_allCombinations(BookingStatus current, BookingStatus target, boolean expected) {
        assertThat(current.canTransitionTo(target)).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(BookingStatus.class)
    @DisplayName("allowedTransitions는 호출자가 변경해도 원본 규칙을 오염시키지 않는다")
    void should_returnDefensiveCopy_when_allowedTransitionsCalled(BookingStatus status) {
        Set<BookingStatus> first = status.allowedTransitions();
        first.clear();

        assertThat(status.allowedTransitions()).isEqualTo(BookingStatus.valueOf(status.name()).allowedTransitions());
    }

    @ParameterizedTest
    @EnumSource(
        value = BookingStatus.class,
        names = {
            "CANCELLED"
        }
    )
    @DisplayName("종단 상태는 어떤 전이도 허용하지 않는다")
    void should_allowNoTransition_when_terminalStatus(BookingStatus status) {
        assertThat(status.allowedTransitions()).isEmpty();
    }
}
