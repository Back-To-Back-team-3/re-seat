package com.backtoback.reseat.domain.admin.queue.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import com.backtoback.reseat.domain.admin.queue.dto.request.AdmissionMetricSearchCondition;
import com.backtoback.reseat.domain.admin.queue.dto.response.AdminQueueAdmissionMetricResponse;
import com.backtoback.reseat.domain.admin.queue.dto.response.AdminQueueAdmissionMetricsResponse;
import com.backtoback.reseat.domain.admin.queue.dto.response.AdminQueueOverviewResponse;
import com.backtoback.reseat.domain.admin.queue.exception.QueueAdmissionMetricSearchConditionInvalidException;
import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.exception.GameNotFoundException;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.queue.entity.AdmissionTokenStatus;
import com.backtoback.reseat.domain.queue.repository.AdmissionMetricDailyProjection;
import com.backtoback.reseat.domain.queue.repository.AdmissionTokenRepository;
import com.backtoback.reseat.domain.queue.service.QueueRedisKey;

/**
 * 관리자 대기열 현황과 입장 지표 조회 정책을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminQueueQueryService")
public class AdminQueueQueryServiceTest {

    private static final Long GAME_ID = 1L;
    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 1);

    @Mock
    RedisTemplate<String, String> redisTemplate;
    @Mock
    ZSetOperations<String, String> zSetOperations;
    @Mock
    GameRepository gameRepository;
    @Mock
    AdmissionTokenRepository admissionTokenRepository;
    @InjectMocks
    AdminQueueQueryService adminQueueQueryService;

    /**
     * 현황 조회 테스트에서 Redis ZSet 연산 객체를 반환하도록 공통 동작을 준비한다.
     */
    @BeforeEach
    void setUp() {

        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    /**
     * 날짜와 발급 수를 반환하는 일별 집계 Projection을 생성한다.
     *
     * @param date 발급일
     * @param admittedCount 해당 날짜의 발급 수
     * @return 준비된 일별 집계 Projection
     */
    private AdmissionMetricDailyProjection dailyMetric(LocalDate date, long admittedCount) {

        AdmissionMetricDailyProjection metric = mock(AdmissionMetricDailyProjection.class);

        when(metric.getAdmissionDate()).thenReturn(date);
        when(metric.getAdmittedCount()).thenReturn(admittedCount);

        return metric;
    }

    /**
     * 역전된 기간, 최대 범위를 초과한 기간과 종료일 최댓값을 사용하는 입장 지표 조회 조건을 제공한다.
     *
     * @return 잘못된 입장 지표 조회 조건 Stream
     */
    private static Stream<AdmissionMetricSearchCondition> invalidAdmissionMetricConditions() {

        LocalDate maxDate = LocalDate.parse(LocalDate.MAX.toString());

        return Stream
            .of(
                new AdmissionMetricSearchCondition(AdmissionMetricPeriod.DAILY, TO, FROM),
                new AdmissionMetricSearchCondition(AdmissionMetricPeriod.DAILY, FROM, FROM.plusDays(366)),
                new AdmissionMetricSearchCondition(AdmissionMetricPeriod.DAILY, maxDate, maxDate)
            );
    }

    // ---------- 관리자 대기열 현황 조회 ----------

    @Test
    @DisplayName("Redis 대기 인원과 사용할 수 있는 Queue-Token 수 및 오늘 발급 수를 경기 현황으로 반환한다.")
    void getOverview_withExistingGame_returnsQueueOverview() {

        // given
        Game game = Game.builder().bookingStatus(BookingStatus.OPEN).build();

        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));

        // 현재 대기 인원은 Redis에서, Queue-Token 현황은 DB에서 각각 조회한다.
        long redisCount = 12L;
        long usableAdmissionCount = 3L;
        long admittedToday = 7L;

        ArgumentCaptor<LocalDateTime> usableNowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> issuedFromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> issuedToExclusiveCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        // 현재 대기 인원은 waiting Redis ZSet에 저장된 member 수를 사용한다.
        given(zSetOperations.zCard(QueueRedisKey.waiting(GAME_ID))).willReturn(redisCount);

        // 사용할 수 있는 토큰은 전체 만료 전이며 탐색을 완료했거나 최초 탐색 만료 전이어야 한다.
        given(
            admissionTokenRepository
                .countUsableByGameId(eq(GAME_ID), eq(AdmissionTokenStatus.ACTIVE), any(LocalDateTime.class))
        ).willReturn(usableAdmissionCount);

        // 오늘 발급 수는 [오늘 00:00, 다음 날 00:00) 반개방 구간으로 집계한다.
        given(
            admissionTokenRepository
                .countIssuedByGameIdAndPeriod(eq(GAME_ID), any(LocalDateTime.class), any(LocalDateTime.class))
        ).willReturn(admittedToday);

        LocalDateTime beforeCall = LocalDateTime.now();

        // when
        AdminQueueOverviewResponse response = adminQueueQueryService.getOverview(GAME_ID);

        LocalDateTime afterCall = LocalDateTime.now();

        // then
        assertThat(response.gameId()).isEqualTo(GAME_ID);
        assertThat(response.bookingStatus()).isEqualTo(BookingStatus.OPEN);

        // Redis와 DB에서 조회한 세 현황 값이 하나의 응답에 함께 반영돼야 한다.
        assertThat(response.waitingCount()).isEqualTo(redisCount);
        assertThat(response.usableAdmissionCount()).isEqualTo(usableAdmissionCount);
        assertThat(response.admittedToday()).isEqualTo(admittedToday);

        // collectedAt은 Service 실행 중 생성된 시간이어야 한다.
        assertThat(response.collectedAt()).isBetween(beforeCall, afterCall);

        then(admissionTokenRepository)
            .should()
            .countUsableByGameId(eq(GAME_ID), eq(AdmissionTokenStatus.ACTIVE), usableNowCaptor.capture());
        then(admissionTokenRepository)
            .should()
            .countIssuedByGameIdAndPeriod(eq(GAME_ID), issuedFromCaptor.capture(), issuedToExclusiveCaptor.capture());

        // 토큰 유효성 판단과 오늘의 발급 범위는 응답의 collectedAt과 같은 시간을 기준으로 계산한다.
        assertThat(usableNowCaptor.getValue()).isEqualTo(response.collectedAt());
        assertThat(issuedFromCaptor.getValue()).isEqualTo(response.collectedAt().toLocalDate().atStartOfDay());
        assertThat(issuedToExclusiveCaptor.getValue())
            .isEqualTo(response.collectedAt().toLocalDate().plusDays(1).atStartOfDay());
    }

    @Test
    @DisplayName("Redis 대기열이 없으면 현재 대기 인원을 0명으로 반환한다.")
    void getOverview_withoutRedisQueue_returnsZeroWaitingCount() {

        // given
        Game game = Game.builder().bookingStatus(BookingStatus.OPEN).build();

        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));

        // Redis에 대기열 ZSet이 없으면 zCard()가 null을 반환할 수 있다.
        Long redisCount = null;

        given(zSetOperations.zCard(QueueRedisKey.waiting(GAME_ID))).willReturn(redisCount);

        // when
        AdminQueueOverviewResponse response = adminQueueQueryService.getOverview(GAME_ID);

        // then
        // Redis 조회 결과 null은 관리자 응답에서 대기 인원 0명으로 보정한다.
        assertThat(response.waitingCount()).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 경기의 대기열 현황을 조회하면 경기 없음 예외가 발생한다.")
    void getOverview_withMissingGame_throwsGameNotFoundException() {

        // given
        // 경기 존재 여부는 Redis와 Queue-Token 집계보다 먼저 확인한다.
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminQueueQueryService.getOverview(GAME_ID)).isInstanceOf(GameNotFoundException.class);

        // 존재하지 않는 경기의 Redis 대기 인원과 DB 토큰 수를 불필요하게 조회하지 않는다.
        then(redisTemplate).shouldHaveNoInteractions();
        then(admissionTokenRepository).shouldHaveNoInteractions();
    }

    // ---------- 관리자 입장 지표 조회 ----------

    @ParameterizedTest
    @EnumSource(AdmissionMetricPeriod.class)
    @DisplayName("일별 발급 결과를 요청한 기간 단위로 묶고 데이터가 없는 구간을 0으로 반환한다.")
    void getAdmissionMetrics_withPeriod_returnsBucketsIncludingEmptyPeriod(AdmissionMetricPeriod period) {

        // given
        Game game = Game.builder().build();
        AdmissionMetricSearchCondition condition = new AdmissionMetricSearchCondition(period, FROM, TO);

        // Repository의 일별 집계 결과를 Service가 DAILY · WEEKLY · MONTHLY 단위로 다시 묶는다.
        AdmissionMetricDailyProjection metric = dailyMetric(FROM, 2L);

        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(
            admissionTokenRepository
                .findDailyAdmissionMetrics(GAME_ID, FROM.atStartOfDay(), TO.plusDays(1).atStartOfDay())
        ).willReturn(List.of(metric));

        // when
        AdminQueueAdmissionMetricsResponse response = adminQueueQueryService.getAdmissionMetrics(GAME_ID, condition);

        // then
        assertThat(response.gameId()).isEqualTo(GAME_ID);
        assertThat(response.period()).isEqualTo(period);
        assertThat(response.from()).isEqualTo(FROM);
        assertThat(response.to()).isEqualTo(TO);

        // DAILY는 날짜, WEEKLY는 해당 주의 월요일, MONTHLY는 연월을 bucket으로 사용한다.
        List<String> expectedBuckets = switch (period) {
            case DAILY -> FROM.datesUntil(TO.plusDays(1)).map(LocalDate::toString).toList();
            case WEEKLY -> List.of("2026-08-31", "2026-09-07", "2026-09-14", "2026-09-21", "2026-09-28");
            case MONTHLY -> List.of("2026-09", "2026-10");
        };

        assertThat(response.series())
            .extracting(AdminQueueAdmissionMetricResponse::bucket)
            .containsExactlyElementsOf(expectedBuckets);

        assertThat(response.series().get(0).admittedCount()).isEqualTo(2L);

        // 관리자 차트가 중간 구간을 건너뛰지 않도록 조회 범위의 빈 bucket도 응답에 남긴다.
        assertThat(response.series().subList(1, response.series().size()))
            .allSatisfy(metricResponse -> assertThat(metricResponse.admittedCount()).isZero());
    }

    @ParameterizedTest
    @MethodSource("invalidAdmissionMetricConditions")
    @DisplayName("조회 기간이 역전되거나 366일을 초과하거나 종료일이 최대 날짜이면 조회 조건 예외가 발생한다.")
    void getAdmissionMetrics_withInvalidCondition_throwsSearchConditionException(
        AdmissionMetricSearchCondition condition
    ) {

        // given
        Game game = Game.builder().bookingStatus(BookingStatus.OPEN).build();

        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));

        // when & then
        // condition은 기간이 역전됐거나 366일을 초과했거나 종료일의 다음 날을 계산할 수 없는 조건이다.
        assertThatThrownBy(() -> adminQueueQueryService.getAdmissionMetrics(GAME_ID, condition))
            .isInstanceOf(QueueAdmissionMetricSearchConditionInvalidException.class);

        // 잘못된 기간은 DB 집계 쿼리를 실행하기 전에 차단한다.
        then(admissionTokenRepository)
            .should(never())
            .findDailyAdmissionMetrics(any(Long.class), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("같은 주의 일별 발급 수를 합산해 주간 입장 지표로 반환한다.")
    void getAdmissionMetrics_withWeeklyPeriod_mergesDailyMetricsInSameWeek() {

        // given
        Game game = Game.builder().build();
        AdmissionMetricSearchCondition condition
            = new AdmissionMetricSearchCondition(AdmissionMetricPeriod.WEEKLY, FROM, TO);

        // 같은 주의 두 데이터는 하나의 주간 bucket으로 합산되어야 한다.
        AdmissionMetricDailyProjection firstDayMetric = dailyMetric(FROM, 2L);
        AdmissionMetricDailyProjection secondDayMetric = dailyMetric(FROM.plusDays(1), 3L);
        AdmissionMetricDailyProjection nextWeekMetric = dailyMetric(FROM.plusWeeks(1), 4L);

        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));

        given(
            admissionTokenRepository
                .findDailyAdmissionMetrics(GAME_ID, FROM.atStartOfDay(), TO.plusDays(1).atStartOfDay())
        ).willReturn(List.of(firstDayMetric, secondDayMetric, nextWeekMetric));

        // when
        AdminQueueAdmissionMetricsResponse response = adminQueueQueryService.getAdmissionMetrics(GAME_ID, condition);

        // then
        assertThat(response.period()).isEqualTo(AdmissionMetricPeriod.WEEKLY);
        assertThat(response.from()).isEqualTo(FROM);
        assertThat(response.to()).isEqualTo(TO);

        // 주간 bucket은 해당 주의 월요일 날짜를 기준으로 생성된다.
        assertThat(response.series())
            .extracting(AdminQueueAdmissionMetricResponse::bucket, AdminQueueAdmissionMetricResponse::admittedCount)
            .contains(tuple("2026-08-31", 5L));
        assertThat(response.series())
            .extracting(AdminQueueAdmissionMetricResponse::bucket, AdminQueueAdmissionMetricResponse::admittedCount)
            .contains(tuple("2026-09-07", 4L));
    }

    @Test
    @DisplayName("같은 달의 일별 발급 수를 합산해 월간 입장 지표로 반환한다.")
    void getAdmissionMetrics_withMonthlyPeriod_mergesDailyMetricsInSameMonth() {

        // given
        Game game = Game.builder().build();
        AdmissionMetricSearchCondition condition
            = new AdmissionMetricSearchCondition(AdmissionMetricPeriod.MONTHLY, FROM, TO);

        // 같은 달의 두 데이터는 하나의 월간 bucket으로 합산되어야 한다.
        AdmissionMetricDailyProjection earlyMonthMetric = dailyMetric(FROM, 2L);
        AdmissionMetricDailyProjection midMonthMetric = dailyMetric(FROM.plusWeeks(2), 3L);
        AdmissionMetricDailyProjection nextMonthMetric = dailyMetric(FROM.plusMonths(1), 4L);

        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));

        given(
            admissionTokenRepository
                .findDailyAdmissionMetrics(GAME_ID, FROM.atStartOfDay(), TO.plusDays(1).atStartOfDay())
        ).willReturn(List.of(earlyMonthMetric, midMonthMetric, nextMonthMetric));

        // when
        AdminQueueAdmissionMetricsResponse response = adminQueueQueryService.getAdmissionMetrics(GAME_ID, condition);

        // then
        assertThat(response.period()).isEqualTo(AdmissionMetricPeriod.MONTHLY);
        assertThat(response.from()).isEqualTo(FROM);
        assertThat(response.to()).isEqualTo(TO);

        // 월간 bucket은 연월(yyyy-MM)을 기준으로 생성된다.
        assertThat(response.series())
            .extracting(AdminQueueAdmissionMetricResponse::bucket, AdminQueueAdmissionMetricResponse::admittedCount)
            .contains(tuple("2026-09", 5L));
        assertThat(response.series())
            .extracting(AdminQueueAdmissionMetricResponse::bucket, AdminQueueAdmissionMetricResponse::admittedCount)
            .contains(tuple("2026-10", 4L));
    }
}
