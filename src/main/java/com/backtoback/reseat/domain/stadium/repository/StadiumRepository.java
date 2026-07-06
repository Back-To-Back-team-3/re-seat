package com.backtoback.reseat.domain.stadium.repository;

import com.backtoback.reseat.domain.stadium.entity.Stadium;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StadiumRepository extends JpaRepository<Stadium, Long> {
}
