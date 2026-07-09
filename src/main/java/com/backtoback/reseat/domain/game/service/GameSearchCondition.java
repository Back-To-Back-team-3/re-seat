package com.backtoback.reseat.domain.game.service;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.exception.InvalidGameSearchConditionException;

import java.time.LocalDate;

/**
 * 경기 목록 검색 조건.
 *
 * <p>컨트롤러의 여러 RequestParam을 하나의 객체로 묶어
 * 서비스와 Repository 계층의 파라미터 개수를 줄인다.</p>
 *
 * @param teamId 홈팀 또는 원정팀 ID
 * @param from 경기 시작일 검색 시작 날짜
 * @param to 경기 시작일 검색 종료 날짜
 * @param bookingStatus 예매 상태
 */
public record GameSearchCondition(
    Long teamId,
    LocalDate from,
    LocalDate to,
    BookingStatus bookingStatus
) {

    /**
     * 날짜 범위 조건이 올바른지 검증한다.
     *
     * <p>from이 to보다 늦으면 잘못된 검색 조건이다.</p>
     */
    public void validate() {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidGameSearchConditionException(
                "검색 시작일(from)은 종료일(to)보다 늦을 수 없습니다."
            );
        }
    }
}
