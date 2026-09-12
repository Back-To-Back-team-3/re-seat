package com.backtoback.reseat.domain.queue.admin.dto.response;

/**
 * 관리자 입장 지표의 기간별 집계 응답.
 *
 * @param bucket 집계 구간
 * @param admittedCount 해당 구간의 Queue-Token 발급 수
 */
public record AdminQueueAdmissionMetricResponse(String bucket, long admittedCount) {
}
