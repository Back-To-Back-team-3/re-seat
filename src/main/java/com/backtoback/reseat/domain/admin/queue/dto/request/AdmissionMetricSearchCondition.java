package com.backtoback.reseat.domain.admin.queue.dto.request;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import com.backtoback.reseat.domain.admin.queue.exception.QueueAdmissionMetricSearchConditionInvalidException;
import com.backtoback.reseat.domain.admin.queue.service.AdmissionMetricPeriod;

/**
 * 관리자 입장 지표 조회 조건.
 *
 * @param period 입장 지표 집계 단위
 * @param from 조회 시작일
 * @param to 조회 종료일
 */
public record AdmissionMetricSearchCondition(AdmissionMetricPeriod period, LocalDate from, LocalDate to) {

    private static final long MAX_RANGE_DAYS = 365L;

    /**
     * 필수 조회 조건, 날짜 순서와 최대 조회 기간을 검증한다.
     */
    public void validate() {

        if (period == null || from == null || to == null) {
            throw new QueueAdmissionMetricSearchConditionInvalidException();
        }

        if (from.isAfter(to)) {
            throw new QueueAdmissionMetricSearchConditionInvalidException();
        }

        // 시작일과 종료일을 모두 포함하므로 날짜 차이가 365일이면 총 366일이다.
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new QueueAdmissionMetricSearchConditionInvalidException();
        }
    }

    /**
     * 조회 시작일을 당일 00:00으로 변환한다.
     *
     * @return 조회 시작 시간
     */
    public LocalDateTime fromDateTime() {

        return from.atStartOfDay();
    }

    /**
     * 종료일 전체를 포함하도록 종료일 다음 날 00:00을 반환한다.
     *
     * @return 미포함 조회 종료 시간
     */
    public LocalDateTime toExclusiveDateTime() {

        return to.plusDays(1).atStartOfDay();
    }
}
