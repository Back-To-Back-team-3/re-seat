package com.backtoback.reseat.domain.queue.repository;

import java.time.LocalDate;

/**
 * AdmissionToken 발급 수를 날짜별로 조회한 결과.
 */
public interface AdmissionMetricDailyProjection {

    /**
     * 입장 토큰 발급일을 반환한다.
     *
     * @return 입장 토큰 발급일
     */
    LocalDate getAdmissionDate();

    /**
     * 해당 날짜의 입장 토큰 발급 수를 반환한다.
     *
     * @return 입장 토큰 발급 수
     */
    Long getAdmittedCount();
}
