package com.backtoback.reseat.domain.stadium.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.backtoback.reseat.domain.stadium.entity.SeatZone;

public interface SeatZoneRepository extends JpaRepository<SeatZone, Long> {}
