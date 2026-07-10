package com.backtoback.reseat.domain.seatinventory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.backtoback.reseat.domain.stadium.entity.SeatGrade;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * PricePolicy 단위 테스트.
 * <p>
 * LocalDateTime을 직접 만들어 넣기 때문에 7월 시드만 있어도
 * 9월 로직(시기 배수 × 1.2)을 검증할 수 있다.
 */
class PricePolicyTest {

    private final PricePolicy pricePolicy = new PricePolicy();

    /**
     * 요일 × 등급(INFIELD/OUTFIELD) × 시기(7월/9월)의 조합을 정책 표 그대로 검증한다.
     *
     * <p>2026년 기준 요일:
     * 07-01(수) 07-02(목) 07-03(금) 07-04(토) 07-05(일) 07-06(월) 07-07(화)
     * 08-04(화) 09-01(화) 09-03(목) 09-05(토)
     */
    @DisplayName("요일과 시기에 따라 정책대로 가격이 산정된다")
    @ParameterizedTest(name = "[{index}] {0} / {1} / base={2} -> {3}원")
    @CsvSource({
        // --- 화~목 기본가, 시기 배수 없음(7월 × 1.0) ---
        "2026-07-01T18:30:00, INFIELD,  18000, 18000",   // 수
        "2026-07-01T18:30:00, OUTFIELD, 16000, 16000",   // 수
        "2026-07-07T18:30:00, INFIELD,  18000, 18000",   // 화

        // --- 금~일 상승가, 3~8월 (× 1.0) ---
        // 코드 상수(INFIELD 25000, OUTFIELD 23000)
        "2026-07-03T18:30:00, INFIELD,  18000, 25000",   // 금
        "2026-07-04T18:30:00, INFIELD,  18000, 25000",   // 토
        "2026-07-04T18:30:00, OUTFIELD, 16000, 23000",   // 토
        "2026-07-05T18:30:00, OUTFIELD, 16000, 23000",   // 일

        // --- 월요일: KBO 정규 시즌 미편성. 공휴일 대체·잔여 경기 대비 방어 케이스.
        //     코드가 의도대로 동작한다는 사실만 확인한다.
        "2026-07-06T18:30:00, INFIELD,  18000, 25000",   // 월

        // --- 9월 이후 시기 배수 (× 1.2). ---
        // games 시드가 7월뿐이라 통합 테스트로는 이 경로를 밟을 수 없다.
        // 공백을 메우기 위한 행.
        // 계산식: 화~목이면 basePrice × 1.2, 금~일이면 코드 상수 × 1.2
        "2026-09-03T18:30:00, INFIELD,  18000, 21600",   // 목: 18000 × 1.2
        "2026-09-01T18:30:00, OUTFIELD, 16000, 19200",   // 화: 16000 × 1.2
        "2026-09-05T18:30:00, OUTFIELD, 16000, 27600",   // 토: 23000 × 1.2
        "2026-09-05T18:30:00, INFIELD,  18000, 30000"    // 토: 25000 × 1.2
    })
    void should_returnPolicyPrice_when_dayOfWeekAndSeasonGiven(
        LocalDateTime gameAt, SeatGrade grade, int basePrice, int expectedPrice
    ) {
        // when
        int actualPrice = pricePolicy.calculate(gameAt, grade, basePrice);

        // then
        assertThat(actualPrice).isEqualTo(expectedPrice);
    }

    /**
     * 요일 경계 테스트.
     *
     * <p>목요일(기본가)과 금요일(상승가)은 하루 차이지만 가격이 달라진다.
     * 등급·기본가·월은 모두 동일하게 두고 날짜만 하루 움직이다.
     */
    @DisplayName("목요일과 금요일 경계에서 기본가와 상승가가 갈린다")
    @Test
    void should_applyRaisedPrice_when_dayChangesFromThursdayToFriday() {
        // given
        LocalDateTime thursday = LocalDateTime.of(2026, 7, 2, 18, 30);
        LocalDateTime friday = LocalDateTime.of(2026, 7, 3, 18, 30);

        // when & then
        assertThat(pricePolicy.calculate(thursday, SeatGrade.INFIELD, 18_000)).isEqualTo(18_000);
        assertThat(pricePolicy.calculate(friday, SeatGrade.INFIELD, 18_000)).isEqualTo(25_000);
    }

    /**
     * 시기 경계 테스트.
     * 8월(배수 없음)과 9월(× 1.2)의 경계를 확인한다.
     */
    @DisplayName("8월과 9월 경계에서 시기 배수 1.2가 적용되기 시작한다")
    @Test
    void should_applyMultiplier_when_monthChangesFromAugustToSeptember() {
        // given — 둘 다 화요일
        LocalDateTime augustTuesday = LocalDateTime.of(2026, 8, 4, 18, 30);
        LocalDateTime septemberTuesday = LocalDateTime.of(2026, 9, 1, 18, 30);

        // when & then
        assertThat(pricePolicy.calculate(augustTuesday, SeatGrade.INFIELD, 18_000)).isEqualTo(18_000);
        assertThat(pricePolicy.calculate(septemberTuesday, SeatGrade.INFIELD, 18_000)).isEqualTo(21_600);
    }

    /**
     * 부동소수점 오차 테스트.
     * double 곱셈이었다면 23000 × 1.2 = 27599.999... → (int) 27599가 나올 수 있다.
     * 정수 연산(23000 × 12 / 10)이 27600을 정확히 반환하는지 못 박아 둔다.
     */
    @DisplayName("시기 배수 적용 시 부동소수점 오차 없이 정확한 정수 가격을 반환한다")
    @Test
    void should_returnExactInteger_when_multiplierApplied() {
        // given — 9월 토요일 외야석: 상승가 23,000 × 1.2 = 27,600
        LocalDateTime septemberSaturday = LocalDateTime.of(2026, 9, 5, 18, 30);

        // when
        int price = pricePolicy.calculate(septemberSaturday, SeatGrade.OUTFIELD, 16_000);

        // then
        assertThat(price).isEqualTo(27_600);
    }
}
