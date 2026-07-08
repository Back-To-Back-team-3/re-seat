package com.backtoback.reseat.domain.queue.repository;

import com.backtoback.reseat.domain.queue.entity.AdmissionToken;
import com.backtoback.reseat.domain.queue.entity.AdmissionTokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AdmissionTokenRepository extends JpaRepository<AdmissionToken, Long> {

    Optional<AdmissionToken> findByGame_IdAndUser_IdAndStatusAndExpiresAtAfter(
            Long gameId,
            Long userId,
            AdmissionTokenStatus status,
            LocalDateTime now
    );
}
