package com.backtoback.reseat.domain.seatinventory.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.backtoback.reseat.domain.seatinventory.exception.InvalidStateTransitionException;

/**
 * GameSeat 상태 전이 단위 테스트.
 * <p>Spring 컨텍스트 없이 순수 도메인 로직만 검증한다.
 * hold/release/sell 상태 전이에 이어 block/unblock(관리자 판매 차단·해제)도 이 파일에서 다룬다.
 */
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
            GameSeat seat = seatWith(GameSeatStatus.HELD);
            seat.sell(); // soldAt이 실제로 채워진 상태를 먼저 만든다

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

    @Nested
    @DisplayName("block(): AVAILABLE → BLOCKED")
    class Block {

        @Test
        @DisplayName("AVAILABLE 좌석은 BLOCKED로 전이된다")
        void success() {
            GameSeat seat = seatWith(GameSeatStatus.AVAILABLE);

            seat.block();

            assertThat(seat.getStatus()).isEqualTo(GameSeatStatus.BLOCKED);
        }

        @Test
        @DisplayName("HELD 좌석을 block()하면 예외 (선점 해제가 선행돼야 함)")
        void held_throws() {
            GameSeat seat = seatWith(GameSeatStatus.HELD);

            assertThatThrownBy(seat::block).isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("SOLD 좌석을 block()하면 예외 (환불이 선행돼야 함)")
        void sold_throws() {
            GameSeat seat = seatWith(GameSeatStatus.SOLD);

            assertThatThrownBy(seat::block).isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("이미 BLOCKED인 좌석을 다시 block()하면 예외 (멱등 아님)")
        void alreadyBlocked_throws() {
            GameSeat seat = seatWith(GameSeatStatus.BLOCKED);

            assertThatThrownBy(seat::block).isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("unblock(): BLOCKED → AVAILABLE")
    class Unblock {

        @Test
        @DisplayName("BLOCKED 좌석은 AVAILABLE로 돌아간다")
        void success() {
            GameSeat seat = seatWith(GameSeatStatus.BLOCKED);

            seat.unblock();

            assertThat(seat.getStatus()).isEqualTo(GameSeatStatus.AVAILABLE);
        }

        @Test
        @DisplayName("AVAILABLE 좌석을 unblock()하면 예외")
        void available_throws() {
            GameSeat seat = seatWith(GameSeatStatus.AVAILABLE);

            assertThatThrownBy(seat::unblock).isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("HELD 좌석을 unblock()하면 예외")
        void held_throws() {
            GameSeat seat = seatWith(GameSeatStatus.HELD);

            assertThatThrownBy(seat::unblock).isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("SOLD 좌석을 unblock()하면 예외")
        void sold_throws() {
            GameSeat seat = seatWith(GameSeatStatus.SOLD);

            assertThatThrownBy(seat::unblock).isInstanceOf(InvalidStateTransitionException.class);
        }
    }
}
