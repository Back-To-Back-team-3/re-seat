package com.backtoback.reseat.domain.stadium.repository;

import com.backtoback.reseat.domain.stadium.entity.SeatZone;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatZoneRepository extends JpaRepository<SeatZone, Long> {
}
