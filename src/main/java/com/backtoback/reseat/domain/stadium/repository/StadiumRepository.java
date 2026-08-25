package com.backtoback.reseat.domain.stadium.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.backtoback.reseat.domain.stadium.entity.Stadium;

public interface StadiumRepository extends JpaRepository<Stadium, Long> {

}
