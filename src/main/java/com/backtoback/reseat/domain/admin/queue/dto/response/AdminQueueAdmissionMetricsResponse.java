package com.backtoback.reseat.domain.admin.queue.dto.response;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.backtoback.reseat.domain.admin.queue.dto.request.AdmissionMetricSearchCondition;
import com.backtoback.reseat.domain.admin.queue.service.AdmissionMetricPeriod;
import com.backtoback.reseat.domain.queue.repository.AdmissionMetricDailyProjection;

/**
 * 관리자 경기별 입장 지표 응답.
 *
 * @param gameId 경기 ID
 * @param period 입장 지표 집계 단위
 * @param from 조회 시작일
 * @param to 조회 종료일
 * @param series 기간별 Queue-Token 발급 수
 */
public record AdminQueueAdmissionMetricsResponse(
    Long gameId,
    AdmissionMetricPeriod period,
    LocalDate from,
    LocalDate to,
    List<AdminQueueAdmissionMetricResponse> series
) {

    /**
     * 일별 조회 결과를 요청한 기간 단위로 묶고 빈 구간을 0으로 채운다.
     *
     * @param gameId 경기 ID
     * @param condition 입장 지표 조회 조건
     * @param dailyMetrics 날짜별 Queue-Token 발급 수
     * @return 관리자 경기별 입장 지표 응답
     */
    public static AdminQueueAdmissionMetricsResponse from(
        Long gameId,
        AdmissionMetricSearchCondition condition,
        List<AdmissionMetricDailyProjection> dailyMetrics
    ) {

        // 조회 결과가 없는 날짜도 응답하도록 전체 기간의 bucket을 먼저 0으로 생성한다.
        Map<String, Long> counts = createEmptyBuckets(condition);

        // 일별 원본 집계를 요청한 일 · 주 · 월 bucket에 누적한다.
        dailyMetrics.forEach(dailyMetric -> {
            LocalDate date = dailyMetric.getAdmissionDate();
            String bucket = bucketOf(condition.period(), date);

            counts.merge(bucket, dailyMetric.getAdmittedCount(), Long::sum);
        });

        List<AdminQueueAdmissionMetricResponse> series
            = counts
                .entrySet()
                .stream()
                .map(entry -> new AdminQueueAdmissionMetricResponse(entry.getKey(), entry.getValue()))
                .toList();

        return new AdminQueueAdmissionMetricsResponse(
            gameId,
            condition.period(),
            condition.from(),
            condition.to(),
            series
        );
    }

    /**
     * 조회 기간에 포함되는 집계 구간을 순서대로 생성하고 발급 수를 0으로 초기화한다.
     *
     * @param condition 입장 지표 조회 조건
     * @return 집계 구간별 초기 발급 수
     */
    private static Map<String, Long> createEmptyBuckets(AdmissionMetricSearchCondition condition) {

        Map<String, Long> result = new LinkedHashMap<>();

        for (LocalDate date = condition.from(); !date.isAfter(condition.to()); date = date.plusDays(1)) {
            result.putIfAbsent(bucketOf(condition.period(), date), 0L);
        }

        return result;
    }

    /**
     * 날짜를 요청한 일 · 주 · 월 집계 단위의 bucket 문자열로 변환한다.
     *
     * @param period 입장 지표 집계 단위
     * @param date 변환할 날짜
     * @return 집계 단위에 해당하는 bucket 문자열
     */
    private static String bucketOf(AdmissionMetricPeriod period, LocalDate date) {

        return switch (period) {
            case DAILY -> date.toString();

            // 주간 지표는 조회 시작일과 관계없이 월요일을 bucket 시작일로 사용한다.
            case WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
            case MONTHLY -> YearMonth.from(date).toString();
        };
    }
}
