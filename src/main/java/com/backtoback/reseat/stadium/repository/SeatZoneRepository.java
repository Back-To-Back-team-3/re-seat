package com.backtoback.reseat.stadium.repository;

import com.backtoback.reseat.stadium.entity.SeatZone;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatZoneRepository extends JpaRepository<SeatZone, Long> {
}
