package com.backtoback.reseat.domain.stadium.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.backtoback.reseat.domain.stadium.entity.SeatZone;

public interface SeatZoneRepository extends JpaRepository<SeatZone, Long> {

    /**
     * 구장에 시드된 좌석 구역 총 건수를 센다.
     * 시드 무결성 검증(SeatMasterSeedDataIntegrityTest) 전용 조회.
     *
     * @param stadiumId 구장 ID
     * @return 좌석 구역 건수
     */
    long countByStadiumId(Long stadiumId);
}
