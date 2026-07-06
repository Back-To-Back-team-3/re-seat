package com.backtoback.reseat.domain.stadium.repository;

import com.backtoback.reseat.domain.stadium.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {
}
