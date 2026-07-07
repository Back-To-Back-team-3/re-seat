package com.backtoback.reseat.domain.queue.repository;

import com.backtoback.reseat.domain.queue.entity.AdmissionToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdmissionTokenRepository extends JpaRepository<AdmissionToken, Long> {
}
