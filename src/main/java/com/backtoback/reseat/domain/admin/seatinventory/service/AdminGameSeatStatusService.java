package com.backtoback.reseat.domain.admin.seatinventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.exception.GameSeatNotFoundException;
import com.backtoback.reseat.domain.seatinventory.exception.InvalidStateTransitionException;
import com.backtoback.reseat.domain.seatinventory.repository.GameSeatRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 관리자 전용 좌석 판매 차단·해제 서비스.
 * <p>일반 사용자 플로우(선점 해제·환불)를 다루는 {@code GameSeatStatusService}와는 호출 주체(관리자 vs 사용자)와 책임이 다르다.
 * 호출 주체와 책임이 다른 상태 전이를 한 서비스에 몰아넣지 않기 위해 별도 클래스로 분리한다.
 * <p>
 * 차단·해제 사유(reason)는 admin_audit_logs 인프라가 아직 없어 구조화된 로그로만 남긴다.
 * 인프라가 준비되면 이 로그 포맷(key=value)을 그대로 INSERT 문으로 옮길 수 있도록 필드를 고정한다.
 * <p>
 * 로그는 커밋 성공 이후에만 남긴다(TransactionSynchronization.afterCommit).
 * 커밋 전에 로그를 찍으면 낙관적 락 충돌 등으로 커밋이 실패해도 완료로 기록되어, 이 로그가 대신하고 있는 감사 기록의 신뢰성이 깨진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminGameSeatStatusService {

    private final GameSeatRepository gameSeatRepository;

    /**
     * 판매 가능(AVAILABLE) 좌석을 관리자가 판매 차단(BLOCKED) 처리한다.
     * <p>
     * HELD·SOLD 상태에서 호출하면 {@link InvalidStateTransitionException}(409)이 발생한다.
     * 상태 검증은 {@link GameSeat#block()} 내부에서 수행하며, 이 서비스는 조회·위임·로그 기록만 담당한다.
     *
     * @param gameSeatId 차단할 경기 좌석 ID
     * @param reason 차단 사유 (감사 로그 인프라 부재로 로그에만 기록)
     * @throws GameSeatNotFoundException 경기 좌석이 존재하지 않는 경우 (404)
     * @throws InvalidStateTransitionException AVAILABLE이 아닌 좌석을 차단 시도한 경우 (409)
     */
    @Transactional
    public void blockSeat(Long gameSeatId, String reason) {
        GameSeat gameSeat = gameSeatRepository.findById(gameSeatId).orElseThrow(GameSeatNotFoundException::new);
        gameSeat.block();
        logAfterCommit(() -> log.info("[admin] 좌석 판매 차단 gameSeatId={}, reason={}", gameSeatId, reason));
    }

    /**
     * 차단(BLOCKED) 좌석을 관리자가 판매 가능(AVAILABLE) 상태로 되돌린다.
     *
     * @param gameSeatId 해제할 경기 좌석 ID
     * @param reason 해제 사유 (감사 로그 인프라 부재로 로그에만 기록)
     * @throws GameSeatNotFoundException 경기 좌석이 존재하지 않는 경우 (404)
     * @throws InvalidStateTransitionException BLOCKED가 아닌 좌석을 해제 시도한 경우 (409)
     */
    @Transactional
    public void unblockSeat(Long gameSeatId, String reason) {
        GameSeat gameSeat = gameSeatRepository.findById(gameSeatId).orElseThrow(GameSeatNotFoundException::new);
        gameSeat.unblock();
        logAfterCommit(() -> log.info("[admin] 좌석 차단 해제 gameSeatId={}, reason={}", gameSeatId, reason));
    }

    /**
     * 현재 트랜잭션이 성공적으로 커밋된 이후에만 로그를 남긴다.
     * <p>낙관적 락 충돌 등으로 커밋이 실패·롤백되면 이 로그는 실행되지 않는다.
     */
    private void logAfterCommit(Runnable logAction) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                logAction.run();
            }
        });
    }
}
