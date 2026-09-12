package com.backtoback.reseat.domain.admin.seatinventory.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.seatinventory.repository.GameSeatRepository;
import com.backtoback.reseat.domain.seatinventory.service.GameSeatCreateService;
import com.backtoback.reseat.domain.stadium.entity.Seat;
import com.backtoback.reseat.domain.stadium.entity.SeatGrade;
import com.backtoback.reseat.domain.stadium.entity.SeatZone;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.stadium.repository.SeatRepository;
import com.backtoback.reseat.domain.stadium.repository.SeatZoneRepository;
import com.backtoback.reseat.domain.stadium.repository.StadiumRepository;
import com.backtoback.reseat.domain.team.entity.Team;
import com.backtoback.reseat.domain.team.repository.TeamRepository;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * AdminGameSeatStatusService의 커밋 후 로그 기록(afterCommit) 검증 테스트.
 * <p>
 * afterCommit()은 실제 트랜잭션 커밋이 일어나야만 실행되므로, 다른 통합 테스트처럼 {@code @Transactional}(자동 롤백)을 붙이면 검증할 수 없다.
 * 이 클래스만 예외적으로 실제 커밋을 허용하는 대신, 이 테스트가 만든 엔티티를 각 필드에 직접 기억해뒀다가
 * {@code @AfterEach}에서 PK 기준으로 전부 정리한다.
 */
@SpringBootTest
class AdminGameSeatStatusServiceLoggingTest {

    @Autowired
    private AdminGameSeatStatusService adminGameSeatStatusService;

    @Autowired
    private StadiumRepository stadiumRepository;

    @Autowired
    private SeatZoneRepository seatZoneRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameSeatCreateService gameSeatCreateService;

    @Autowired
    private GameSeatRepository gameSeatRepository;

    private Stadium stadium;
    private Team homeTeam;
    private Team awayTeam;
    private SeatZone zone;
    private Seat seat;
    private Long gameId;
    private Long gameSeatId;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        stadium = stadiumRepository.save(Stadium.of("로깅 테스트 구장", "서울시 테스트구", 10_000));
        homeTeam = teamRepository.save(Team.of("홈팀", stadium));
        awayTeam = teamRepository.save(Team.of("원정팀", stadium));

        zone = seatZoneRepository.save(SeatZone.of(stadium, "테스트 구역", SeatGrade.INFIELD, 18_000));
        seat = seatRepository.save(Seat.of(stadium, zone, "A", "1", "1"));

        LocalDateTime now = LocalDateTime.now();
        Game game
            = Game
                .builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .stadium(stadium)
                .gameAt(now.plusDays(7))
                .bookingOpenAt(now)
                .bookingCloseAt(now.plusDays(7))
                .bookingStatus(BookingStatus.SCHEDULED)
                .title("로깅 테스트 경기")
                .build();

        gameId = gameRepository.save(game).getId();
        gameSeatCreateService.openInventory(gameId);
        gameSeatId = gameSeatRepository.findAllByGameIdWithSeatAndZone(gameId).get(0).getId();

        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger)LoggerFactory.getLogger(AdminGameSeatStatusService.class)).addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        ((Logger)LoggerFactory.getLogger(AdminGameSeatStatusService.class)).detachAppender(logAppender);

        // @Transactional이 없어 자동 롤백되지 않으므로 만든 데이터를 직접 정리한다.
        // FK 역순(game_seats → games → seats → seat_zones → teams → stadiums)으로 직접 정리한다.
        gameSeatRepository.deleteAllByGameId(gameId);
        gameRepository.deleteById(gameId);
        seatRepository.delete(seat);
        seatZoneRepository.delete(zone);
        teamRepository.delete(homeTeam);
        teamRepository.delete(awayTeam);
        stadiumRepository.delete(stadium);
    }

    @DisplayName("blockSeat 커밋이 성공하면 로그가 남는다")
    @Test
    void should_logAfterCommit_when_blockSeatSucceeds() {
        adminGameSeatStatusService.blockSeat(gameSeatId, "매크로 의심 좌석 임시 차단");

        List<String> messages = logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();

        assertThat(messages).anyMatch(message -> message.contains("좌석 판매 차단") && message.contains("매크로 의심"));
    }

    @DisplayName("blockSeat이 예외로 실패하면(존재하지 않는 좌석) 로그가 남지 않는다")
    @Test
    void should_notLog_when_blockSeatFails() {
        try {
            adminGameSeatStatusService.blockSeat(999_999L, "사유");
        } catch (Exception ignored) {
            // 예외 발생 자체는 이 테스트의 관심사가 아니다 — 로그 미기록 여부만 확인한다.
        }

        boolean hasBlockLog = logAppender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("좌석 판매 차단"));

        assertThat(hasBlockLog).isFalse();
    }
}
