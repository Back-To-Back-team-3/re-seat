package com.backtoback.reseat.domain.stadium.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.backtoback.reseat.domain.stadium.entity.Seat;
import com.backtoback.reseat.domain.stadium.entity.SeatStatus;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    /**
     * 구장의 활성 좌석 전체를 구역(SeatZone)과 함께 조회한다.
     *
     * @param stadiumId 구장 ID
     * @param status 조회할 좌석 상태 (재고 오픈 시 {@link SeatStatus#ACTIVE})
     * @return 구역이 함꼐 로딩된 좌석 목록 (좌석 ID 오름차순)
     */
    @Query("""
        select s
        from Seat s
        join fetch s.zone
        where s.stadium.id = :stadiumId
          and s.status = :status
        order by s.id asc
        """)
    List<Seat> findAllByStadiumIdAndStatusWithZone(
        @Param("stadiumId") Long stadiumId,
        @Param("status") SeatStatus status
    );

    /**
     * 구장에 시드된 물리 좌석 총 건수를 센다.
     * 시드 무결성 검증(SeatMasterSeedDataIntegrityTest) 전용 조회.
     *
     * @param stadiumId 구장 ID
     * @return 좌석 건수
     */
    long countByStadiumId(Long stadiumId);
}
