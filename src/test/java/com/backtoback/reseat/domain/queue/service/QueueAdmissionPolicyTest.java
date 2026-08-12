package com.backtoback.reseat.domain.queue.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QueueAdmissionPolicy")
public class QueueAdmissionPolicyTest {

	@Test
	@DisplayName("20번째와 21번째 대기자는 각각 3초와 6초의 예상 대기시간을 반환한다.")
	void calculateWaitTime_atBoundary() {

		// given
		// 한 번에 20명씩 입장하므로 처리 묶음이 바뀌는 경계를 준비한다.
		long firstRoundLastRank = 20;
		long secondRoundFirstRank = 21;

		// when
		long firstRoundWaitTime = QueueAdmissionPolicy.calculateEstimatedWaitSeconds(firstRoundLastRank);
		long secondRoundWaitTime = QueueAdmissionPolicy.calculateEstimatedWaitSeconds(secondRoundFirstRank);

		// then
		assertThat(firstRoundWaitTime).isEqualTo(3L);
		assertThat(secondRoundWaitTime).isEqualTo(6L);
	}
}
