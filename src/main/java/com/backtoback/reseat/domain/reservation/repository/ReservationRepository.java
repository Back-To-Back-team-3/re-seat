package com.backtoback.reseat.domain.reservation.repository;

import com.backtoback.reseat.domain.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    // C-4: findByReservationNo, findByUserIdAndStatus, findExpiredHolding ...
}
