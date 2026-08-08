package com.backtoback.reseat.domain.seatinventory.service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.backtoback.reseat.domain.stadium.entity.SeatGrade;

/**
 * 경기 좌석 재고(game_seats)의 판매 가격을 산정하는 정책 클래스.
 *
 * <p> 산정식: {@code price = 구역 성인 기본가(요일 반영) × 시기 배수}
 * 가격 책임 경계 - 이 클래스가 책임지는 범위는 성인 정가까지다.
 */
@Component
public class PricePolicy {

    /**
     * 기본가가 적용되는 요일
     *
     * <P>여기에 없는 요일(금,토.일)에는 상승가를 적용한다.
     */
    private static final Set<DayOfWeek> BASE_PRICE_DAYS =
        Collections.unmodifiableSet(
            EnumSet.of(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY));

    // 금~일(및 예외적 월요일) 성인 기본가
    private static final Map<SeatGrade, Integer> RAISED_BASE_PRICES;
    // 성수기 시작인 달
    private static final int PEAK_SEASON_START_MONTH = 9;
    // 성수기 배수 1.2를 정수 연산으로 표현한 분자/분모
    private static final int PEAK_MULTIPLIER_NUMERATOR = 12;
    private static final int MULTIPLIER_DENOMINATOR = 10;

    static {
        Map<SeatGrade, Integer> prices = new EnumMap<>(SeatGrade.class);
        prices.put(SeatGrade.INFIELD, 25_000);
        prices.put(SeatGrade.OUTFIELD, 23_000);
        RAISED_BASE_PRICES = Collections.unmodifiableMap(prices);
    }

    /**
     * 경기 일시와 좌석 등급으로 산정한 최종 판매 가격
     *
     * @param gameAt    경기 일시 (요일·월 판정에 사용)
     * @param grade     좌석 등급 (금~일 상승가 조회에 사용)
     * @param basePrice 구역 기본 가격 ({@code seat_zones.base_price}, 화~목 기준값)
     * @return 성인 정가 (원)
     */
    public int calculate(LocalDateTime gameAt, SeatGrade grade, int basePrice) {
        int dayPrice = resolveDayPrice(gameAt.getDayOfWeek(), grade, basePrice);

        return applySeasonMultiplier(gameAt.getMonthValue(), dayPrice);
    }

    /**
     * 요일에 따른 기본가
     *
     * <p>화~목: DB의 {@code basePrice} 사용
     * 나머지 요일: 등급별 상승가 상수 사용
     * 추가 경기를 위해 월요일을 아예 제외하지는 않는다.
     */
    private int resolveDayPrice(DayOfWeek dayOfWeek, SeatGrade grade, int basePrice) {
        if (BASE_PRICE_DAYS.contains(dayOfWeek)) {
            return basePrice;
        }

        Integer raisedPrice = RAISED_BASE_PRICES.get(grade);
        if (raisedPrice == null) {
            // SeatGrade에 새 등급이 추가됐는데 상승가를 등록하지 않은 경우.
            throw new IllegalStateException("금~일 기본가가 정의되지 않은 좌석 등급입니다. grade=" + grade);
        }

        return raisedPrice;
    }

    /**
     * 시기 배수 적용
     *
     * <p>9월 이상이면 1.2배, 3~8월은 1.0배.
     *
     */
    private int applySeasonMultiplier(int month, int dayPrice) {
        if (month < PEAK_SEASON_START_MONTH) {
            return dayPrice;
        }

        return dayPrice * PEAK_MULTIPLIER_NUMERATOR / MULTIPLIER_DENOMINATOR;
    }

}
