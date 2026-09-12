package com.backtoback.reseat.domain.admin.queue.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.admin.queue.dto.request.AdmissionMetricSearchCondition;
import com.backtoback.reseat.domain.admin.queue.dto.response.AdminQueueAdmissionMetricsResponse;
import com.backtoback.reseat.domain.admin.queue.dto.response.AdminQueueOverviewResponse;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.exception.GameNotFoundException;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.queue.entity.AdmissionTokenStatus;
import com.backtoback.reseat.domain.queue.repository.AdmissionMetricDailyProjection;
import com.backtoback.reseat.domain.queue.repository.AdmissionTokenRepository;
import com.backtoback.reseat.domain.queue.service.QueueRedisKey;

import lombok.RequiredArgsConstructor;

/**
 * 관리자용 경기별 대기열 현황과 입장 지표를 조회하는 서비스.
 * <p>현재 대기 인원은 Redis에서, Queue-Token 현황과 발급 이력은 DB에서 조회한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminQueueQueryService {

    private final RedisTemplate<String, String> redisTemplate;
    private final GameRepository gameRepository;
    private final AdmissionTokenRepository admissionTokenRepository;

    /**
     * 경기별 현재 대기 인원, 사용 가능한 Queue-Token 수와 오늘 입장 수를 조회한다.
     *
     * @param gameId 조회할 경기 ID
     * @return 관리자 경기별 대기열 현황
     */
    public AdminQueueOverviewResponse getOverview(Long gameId) {

        Game game = findGame(gameId);

        // 한 응답 안의 토큰 유효성 판단과 오늘 범위 계산에 같은 기준 시간을 사용한다.
        LocalDateTime now = LocalDateTime.now();

        Long redisCount = redisTemplate.opsForZSet().zCard(QueueRedisKey.waiting(gameId));

        // Redis ZSet이 없으면 zCard()가 null일 수 있으므로 대기 인원 0명으로 처리한다.
        long waitingCount = redisCount == null ? 0L : redisCount;
        long usableAdmissionCount
            = admissionTokenRepository.countUsableByGameId(gameId, AdmissionTokenStatus.ACTIVE, now);

        LocalDate today = now.toLocalDate();
        long admittedToday
            = admissionTokenRepository
                .countIssuedByGameIdAndPeriod(gameId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());

        return new AdminQueueOverviewResponse(
            gameId,
            game.getBookingStatus(),
            waitingCount,
            usableAdmissionCount,
            admittedToday,
            now
        );
    }

    /**
     * 경기별 Queue-Token 발급 수를 요청한 일 · 주 · 월 단위로 조회한다.
     *
     * @param gameId 조회할 경기 ID
     * @param condition 입장 지표 조회 조건
     * @return 관리자 경기별 입장 지표
     */
    public AdminQueueAdmissionMetricsResponse getAdmissionMetrics(
        Long gameId,
        AdmissionMetricSearchCondition condition
    ) {

        findGame(gameId);
        condition.validate();

        List<AdmissionMetricDailyProjection> metrics
            = admissionTokenRepository
                .findDailyAdmissionMetrics(gameId, condition.fromDateTime(), condition.toExclusiveDateTime());

        return AdminQueueAdmissionMetricsResponse.from(gameId, condition, metrics);
    }

    /**
     * 경기 ID로 경기를 조회한다.
     *
     * @param gameId 조회할 경기 ID
     * @return 조회된 경기
     * @throws GameNotFoundException 경기가 존재하지 않는 경우
     */
    private Game findGame(Long gameId) {

        return gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
    }
}
