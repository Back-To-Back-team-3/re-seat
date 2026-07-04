package com.backtoback.reseat.stadium.repository;

import com.backtoback.reseat.stadium.entity.Stadium;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StadiumRepository extends JpaRepository<Stadium, Long> {
}
