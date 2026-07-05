package com.backtoback.reseat.stadium.repository;

import com.backtoback.reseat.stadium.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {
}
