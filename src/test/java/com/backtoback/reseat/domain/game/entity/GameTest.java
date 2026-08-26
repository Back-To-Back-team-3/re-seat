package com.backtoback.reseat.domain.game.entity;

import com.backtoback.reseat.domain.game.exception.InvalidBookingStatusTransitionException;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.team.entity.Team;
import com.backtoback.reseat.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameTest {

    @Test
    @DisplayName("SCHEDULED 경기의 예매를 오픈하면 OPEN으로 전이된다")
    void should_transitionToOpen_when_openBookingOnScheduledGame() {
        Game game = gameFixture(BookingStatus.SCHEDULED);

        game.openBooking();

        assertThat(game.getBookingStatus()).isEqualTo(BookingStatus.OPEN);
    }

    @Test
    @DisplayName("CLOSED 경기의 예매를 다시 오픈하면 예외가 발생한다")
    void should_throwException_when_openBookingOnClosedGame() {
        Game game = gameFixture(BookingStatus.CLOSED);

        assertThatThrownBy(game::openBooking)
            .isInstanceOf(InvalidBookingStatusTransitionException.class)
            .extracting(e -> ((InvalidBookingStatusTransitionException)e).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_BOOKING_STATUS_TRANSITION);

        // 예외 발생 후 상태가 오염되지 않았는지 확인한다
        assertThat(game.getBookingStatus()).isEqualTo(BookingStatus.CLOSED);
    }

    @Test
    @DisplayName("마감된 경기도 취소할 수 있다")
    void should_transitionToCancelled_when_cancelGameOnClosedGame() {
        Game game = gameFixture(BookingStatus.CLOSED);

        game.cancelGame();

        assertThat(game.getBookingStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("취소된 경기는 다시 취소할 수 없다")
    void should_throwException_when_cancelGameOnCancelledGame() {
        Game game = gameFixture(BookingStatus.CANCELLED);

        assertThatThrownBy(game::cancelGame).isInstanceOf(InvalidBookingStatusTransitionException.class);
    }

    /** DB 저장 없이 객체만 만드는 순수 단위 테스트라 Stadium·Team도 저장하지 않는다. */
    private Game gameFixture(BookingStatus bookingStatus) {
        Stadium stadium = Stadium.of("테스트 구장", "서울시 테스트구", 10_000);
        Team homeTeam = Team.of("홈팀", stadium);
        Team awayTeam = Team.of("원정팀", stadium);
        LocalDateTime now = LocalDateTime.now();

        return Game
            .builder()
            .homeTeam(homeTeam)
            .awayTeam(awayTeam)
            .stadium(stadium)
            .gameAt(now.plusDays(7))
            .bookingOpenAt(now)
            .bookingCloseAt(now.plusDays(7))
            .bookingStatus(bookingStatus)
            .title("테스트 경기")
            .build();
    }
}
