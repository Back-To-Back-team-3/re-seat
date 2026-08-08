package com.backtoback.reseat.domain.queue.service;

/**
 * 경기별 대기열의 자동 입장 인원과 실행 간격을 관리하고 예상 대기시간을 계산하는 정책
 */
public final class QueueAdmissionPolicy {

    // 한 번의 자동 입장에서 경기별로 처리할 최대 사용자 수
    public static final int ADMIT_LIMIT = 20;

    // 앱 시작 후 최초 실행까지의 지연 시간이며 이전 작업 완료 후 다음 실행까지 기다리는 시간
    public static final long ADMISSION_INTERVAL_MILLIS = 3000L;

    // 상수와 정적 계산 메서드만 제공하므로 외부 생성을 막는다.
    private QueueAdmissionPolicy() {
    }

    /**
     * 현재 대기 순번을 기준으로 입장까지 필요한 예상 시간을 초 단위로 계산한다.
     *
     * <p>한 번에 최대 20명씩 처리하므로 같은 처리 묶음의 사용자는 동일한 예상 시간을 반환한다.</p>
     *
     * @param rank 1부터 시작하는 현재 대기 순번
     * @return 자동 입장 처리 주기를 기준으로 계산한 예상 대기시간(초)
     */
    public static long calculateEstimatedWaitSeconds(long rank) {

        if (rank <= 0) {
            throw new IllegalArgumentException("대기 순번은 1 이상이어야 합니다.");
        }

        long admissionRounds = (rank - 1) / ADMIT_LIMIT + 1;
        long admissionIntervalSeconds = ADMISSION_INTERVAL_MILLIS / 1000;

        return admissionRounds * admissionIntervalSeconds;
    }

}
