package com.backtoback.reseat.domain.queue.repository;

import com.backtoback.reseat.domain.queue.entity.AdmissionToken;
import com.backtoback.reseat.domain.queue.entity.AdmissionTokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 입장 토큰의 저장과 조회를 담당하는 Repository
 */
public interface AdmissionTokenRepository extends JpaRepository<AdmissionToken, Long> {

    // 경기, 사용자, 토큰 상태가 일치하고 기준 시간 이후까지 유효한 입장 토큰을 조회한다.
    Optional<AdmissionToken> findByGame_IdAndUser_IdAndStatusAndExpiresAtAfter(
            Long gameId,
            Long userId,
            AdmissionTokenStatus status,
            LocalDateTime now
    );
}
