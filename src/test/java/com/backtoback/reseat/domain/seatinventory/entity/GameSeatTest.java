package com.backtoback.reseat.domain.seatinventory.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.backtoback.reseat.domain.seatinventory.exception.InvalidStateTransitionException;

@DisplayName("GameSeat 상태 전이")
class GameSeatTest {

    private static final LocalDateTime EXPIRES_AT = LocalDateTime.now().plusMinutes(10);

    // 특정 상태의 좌석을 만드는 헬퍼. 실제 빌더/팩토리에 맞게 조정.
    private GameSeat seatWith(GameSeatStatus status) {
        return GameSeat.builder().status(status).build();
    }

    @Nested
    @DisplayName("hold(): AVAILABLE → HELD")
    class Hold {

        @Test
        @DisplayName("AVAILABLE 좌석은 HELD로 전이되고 만료 시각이 세팅된다")
        void success() {
            GameSeat seat = seatWith(GameSeatStatus.AVAILABLE);

            seat.hold(EXPIRES_AT);

            assertThat(seat.getStatus()).isEqualTo(GameSeatStatus.HELD);
            assertThat(seat.getHoldExpiresAt()).isEqualTo(EXPIRES_AT);
        }

        @Test
        @DisplayName("이미 HELD인 좌석을 hold()하면 예외 (중복 선점 차단)")
        void alreadyHeld_throws() {
            GameSeat seat = seatWith(GameSeatStatus.HELD);

            assertThatThrownBy(() -> seat.hold(EXPIRES_AT)).isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("SOLD 좌석을 hold()하면 예외")
        void sold_throws() {
            GameSeat seat = seatWith(GameSeatStatus.SOLD);

            assertThatThrownBy(() -> seat.hold(EXPIRES_AT)).isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("release(): HELD → AVAILABLE")
    class Release {

        @Test
        @DisplayName("HELD 좌석은 AVAILABLE로 돌아가고 만료 시각이 초기화된다")
        void success() {
            GameSeat seat = seatWith(GameSeatStatus.HELD);

            seat.release();

            assertThat(seat.getStatus()).isEqualTo(GameSeatStatus.AVAILABLE);
            assertThat(seat.getHoldExpiresAt()).isNull();
        }

        @Test
        @DisplayName("AVAILABLE 좌석을 release()하면 예외")
        void available_throws() {
            GameSeat seat = seatWith(GameSeatStatus.AVAILABLE);

            assertThatThrownBy(seat::release).isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("sell(): HELD → SOLD")
    class Sell {

        @Test
        @DisplayName("HELD 좌석은 SOLD로 전이되고 만료 시각이 초기화된다")
        void success() {
            GameSeat seat = seatWith(GameSeatStatus.HELD);

            seat.sell();

            assertThat(seat.getStatus()).isEqualTo(GameSeatStatus.SOLD);
            assertThat(seat.getHoldExpiresAt()).isNull();
        }

        @Test
        @DisplayName("AVAILABLE 좌석을 sell()하면 예외 (선점 없이 판매 불가)")
        void available_throws() {
            GameSeat seat = seatWith(GameSeatStatus.AVAILABLE);

            assertThatThrownBy(seat::sell).isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("refund(): SOLD → AVAILABLE")
    class Refund {

        @Test
        @DisplayName("SOLD 좌석은 AVAILABLE로 돌아가고 판매 시각이 초기화된다")
        void success() {
            GameSeat seat = seatWith(GameSeatStatus.SOLD);

            seat.refund();

            assertThat(seat.getStatus()).isEqualTo(GameSeatStatus.AVAILABLE);
            assertThat(seat.getSoldAt()).isNull();
        }

        @Test
        @DisplayName("AVAILABLE 좌석을 refund()하면 예외")
        void available_throws() {
            GameSeat seat = seatWith(GameSeatStatus.AVAILABLE);

            assertThatThrownBy(seat::refund).isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("HELD 좌석을 refund()하면 예외 (아직 판매되지 않은 좌석은 환불 대상이 아님)")
        void held_throws() {
            GameSeat seat = seatWith(GameSeatStatus.HELD);

            assertThatThrownBy(seat::refund).isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("BLOCKED 좌석을 refund()하면 예외 (기존 available()의 미검증 문제를 재발시키지 않는다)")
        void blocked_throws() {
            GameSeat seat = seatWith(GameSeatStatus.BLOCKED);

            assertThatThrownBy(seat::refund).isInstanceOf(InvalidStateTransitionException.class);
        }
    }
}
