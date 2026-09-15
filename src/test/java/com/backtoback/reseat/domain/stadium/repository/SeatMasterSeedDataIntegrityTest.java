package com.backtoback.reseat.domain.stadium.repository;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 잠실 외 8개 구장(stadium_id=2~9) 좌석 마스터 시드(V35, V36) 무결성 검증.
 * V4(stadium_id=1)를 포함해 9개 구장 전체를 대상으로 한다.
 */
@EnabledIfEnvironmentVariable(
    named = "RUN_SEED_INTEGRITY_TESTS",
    matches = "true"
)
@Tag("seed-integrity")
@ActiveProfiles("local")
@SpringBootTest
class SeatMasterSeedDataIntegrityTest {

    private static final long FIRST_STADIUM_ID = 1L;
    private static final long LAST_STADIUM_ID = 9L;
    private static final long EXPECTED_ZONE_COUNT = 10L;
    private static final long EXPECTED_SEAT_COUNT = 500L;

    @Autowired
    private SeatZoneRepository seatZoneRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Test
    @DisplayName("9개 구장 전체에 좌석 구역이 10건씩 시드된다 (V4 + V35)")
    void should_haveTenZones_forEveryStadium() {
        for (long stadiumId = FIRST_STADIUM_ID; stadiumId <= LAST_STADIUM_ID; stadiumId++) {
            assertThat(seatZoneRepository.countByStadiumId(stadiumId))
                .as("stadiumId=" + stadiumId + " 구역 수")
                .isEqualTo(EXPECTED_ZONE_COUNT);
        }
    }

    @Test
    @DisplayName("9개 구장 전체에 물리 좌석이 500건씩 시드된다 (V4 + V36)")
    void should_have500Seats_forEveryStadium() {
        for (long stadiumId = FIRST_STADIUM_ID; stadiumId <= LAST_STADIUM_ID; stadiumId++) {
            assertThat(seatRepository.countByStadiumId(stadiumId))
                .as("stadiumId=" + stadiumId + " 좌석 수")
                .isEqualTo(EXPECTED_SEAT_COUNT);
        }
    }
}
