package com.backtoback.reseat.domain.payment.entity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("PaymentRecoveryTask 상태 전이")
class PaymentRecoveryTaskTest {

	private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 7, 25, 12, 0);

	private PaymentRecoveryTask pendingTask() {
		return new PaymentRecoveryTask(mock(Payment.class));
	}

	private PaymentRecoveryTask taskInStatus(PaymentRecoveryStatus status) {
		PaymentRecoveryTask task = pendingTask();
		switch (status) {
			case PENDING -> {
				return task;
			}
			case PROCESSING -> task.startProcessing(BASE_TIME);
			case RETRY -> {
				task.startProcessing(BASE_TIME);
				task.scheduleRetry("토스 결제 조회 실패", BASE_TIME.plusMinutes(1));
			}
			case COMPLETED -> {
				task.startProcessing(BASE_TIME);
				task.complete(BASE_TIME.plusMinutes(1));
			}
			case FAILED -> {
				task.startProcessing(BASE_TIME);
				task.fail("최대 재시도 횟수 초과");
			}
		}
		return task;
	}

	@Nested
	@DisplayName("복구 작업을 생성한다")
	class Creation {

		@Test
		@DisplayName("기본 상태는 PENDING이고 시도 횟수는 0이다.")
		void startsAsPending() {
			PaymentRecoveryTask task = pendingTask();

			assertThat(task.getStatus()).isEqualTo(PaymentRecoveryStatus.PENDING);
			assertThat(task.getAttemptCount()).isZero();
		}
	}

	@Nested
	@DisplayName("복구 작업을 처리 중으로 전환한다")
	class StartProcessing {

		@Test
		@DisplayName("대기 중인 작업은 PROCESSING 상태와 처리 시작 시각이 기록된다.")
		void marksProcessing() {
			PaymentRecoveryTask task = pendingTask();
			LocalDateTime startedAt = LocalDateTime.of(2026, 7, 25, 12, 0);

			task.startProcessing(startedAt);

			assertThat(task.getStatus()).isEqualTo(PaymentRecoveryStatus.PROCESSING);
			assertThat(task.getProcessingStartedAt()).isEqualTo(startedAt);
			assertThat(task.getNextRetryAt()).isNull();
		}

		@Test
		@DisplayName("재시도 대기 중인 작업도 PROCESSING 상태로 변경할 수 있다.")
		void allowsRetryTask() {
			PaymentRecoveryTask task = pendingTask();
			task.startProcessing(LocalDateTime.of(2026, 7, 25, 12, 0));
			task.scheduleRetry(
				"토스 결제 조회 실패",
				LocalDateTime.of(2026, 7, 25, 12, 1));
			LocalDateTime restartedAt = LocalDateTime.of(2026, 7, 25, 12, 1);

			task.startProcessing(restartedAt);

			assertThat(task.getStatus()).isEqualTo(PaymentRecoveryStatus.PROCESSING);
			assertThat(task.getProcessingStartedAt()).isEqualTo(restartedAt);
			assertThat(task.getNextRetryAt()).isNull();
		}

		@ParameterizedTest(name = "{0} 상태의 작업은 처리를 시작할 수 없다")
		@EnumSource(value = PaymentRecoveryStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"PENDING", "RETRY"})
		@DisplayName("대기 또는 재시도 상태가 아닌 작업을 시작하면 예외가 발생한다.")
		void rejectsUnavailableStatus(PaymentRecoveryStatus status) {
			PaymentRecoveryTask task = taskInStatus(status);

			assertThatThrownBy(() -> task.startProcessing(LocalDateTime.of(2026, 7, 25, 12, 2)))
				.isInstanceOf(IllegalStateException.class);
			assertThat(task.getStatus()).isEqualTo(status);
		}
	}

	@Nested
	@DisplayName("복구 작업의 재시도를 예약한다")
	class ScheduleRetry {

		@Test
		@DisplayName("처리 중인 작업은 시도 횟수와 다음 재시도 정보가 기록된다.")
		void marksRetry() {
			PaymentRecoveryTask task = pendingTask();
			task.startProcessing(LocalDateTime.of(2026, 7, 25, 12, 0));
			LocalDateTime nextRetryAt = LocalDateTime.of(2026, 7, 25, 12, 1);

			task.scheduleRetry("토스 결제 조회 실패", nextRetryAt);

			assertThat(task.getStatus()).isEqualTo(PaymentRecoveryStatus.RETRY);
			assertThat(task.getAttemptCount()).isEqualTo(1);
			assertThat(task.getNextRetryAt()).isEqualTo(nextRetryAt);
			assertThat(task.getLastError()).isEqualTo("토스 결제 조회 실패");
			assertThat(task.getProcessingStartedAt()).isNull();
		}

		@ParameterizedTest(name = "{0} 상태의 작업은 재시도를 예약할 수 없다")
		@EnumSource(value = PaymentRecoveryStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "PROCESSING")
		@DisplayName("처리 중이 아닌 모든 작업은 예외가 발생한다.")
		void requiresProcessing(PaymentRecoveryStatus status) {
			PaymentRecoveryTask task = taskInStatus(status);

			assertThatThrownBy(() -> task.scheduleRetry(
				"토스 결제 조회 실패",
				LocalDateTime.of(2026, 7, 25, 12, 1)))
				.isInstanceOf(IllegalStateException.class);
			assertThat(task.getStatus()).isEqualTo(status);
		}
	}

	@Nested
	@DisplayName("복구 작업을 완료한다")
	class Complete {

		@Test
		@DisplayName("완료하면 COMPLETED 상태와 완료 시각이 기록된다.")
		void marksCompleted() {
			PaymentRecoveryTask task = pendingTask();
			task.startProcessing(LocalDateTime.of(2026, 7, 25, 12, 0));
			LocalDateTime completedAt = LocalDateTime.of(2026, 7, 25, 12, 1);

			task.complete(completedAt);

			assertThat(task.getStatus()).isEqualTo(PaymentRecoveryStatus.COMPLETED);
			assertThat(task.getCompletedAt()).isEqualTo(completedAt);
			assertThat(task.getProcessingStartedAt()).isNull();
			assertThat(task.getNextRetryAt()).isNull();
			assertThat(task.getLastError()).isNull();
		}

		@ParameterizedTest(name = "{0} 상태의 작업은 완료할 수 없다")
		@EnumSource(value = PaymentRecoveryStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "PROCESSING")
		@DisplayName("처리 중이 아닌 모든 작업은 예외가 발생한다.")
		void requiresProcessing(PaymentRecoveryStatus status) {
			PaymentRecoveryTask task = taskInStatus(status);

			assertThatThrownBy(() -> task.complete(BASE_TIME.plusMinutes(2)))
				.isInstanceOf(IllegalStateException.class);
			assertThat(task.getStatus()).isEqualTo(status);
		}
	}

	@Nested
	@DisplayName("복구 작업을 최종 실패 처리한다")
	class Fail {

		@Test
		@DisplayName("최종 실패하면 FAILED 상태와 오류가 기록된다.")
		void marksFailed() {
			PaymentRecoveryTask task = pendingTask();
			task.startProcessing(LocalDateTime.of(2026, 7, 25, 12, 0));

			task.fail("최대 재시도 횟수 초과");

			assertThat(task.getStatus()).isEqualTo(PaymentRecoveryStatus.FAILED);
			assertThat(task.getLastError()).isEqualTo("최대 재시도 횟수 초과");
			assertThat(task.getProcessingStartedAt()).isNull();
			assertThat(task.getNextRetryAt()).isNull();
		}

		@ParameterizedTest(name = "{0} 상태의 작업은 최종 실패 처리할 수 없다")
		@EnumSource(value = PaymentRecoveryStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "PROCESSING")
		@DisplayName("처리 중이 아닌 모든 작업은 예외가 발생한다.")
		void requiresProcessing(PaymentRecoveryStatus status) {
			PaymentRecoveryTask task = taskInStatus(status);

			assertThatThrownBy(() -> task.fail("최대 재시도 횟수 초과"))
				.isInstanceOf(IllegalStateException.class);
			assertThat(task.getStatus()).isEqualTo(status);
		}
	}
}
