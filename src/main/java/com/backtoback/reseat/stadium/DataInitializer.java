package com.backtoback.reseat.stadium;

import com.backtoback.reseat.stadium.entity.*;
import com.backtoback.reseat.stadium.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final StadiumRepository stadiumRepository;
    private final SeatZoneRepository seatZoneRepository;
    private final SeatRepository seatRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (stadiumRepository.count() > 0) {
            log.info("[DataInitializer] 시드 데이터가 이미 존재합니다. 초기화를 건너뜁니다.");
            return;
        }

        List<Stadium> stadiums = insertStadiums();
        log.info("[DataInitializer] 구장 {}개 삽입 완료", stadiums.size());

        Stadium target = stadiums.get(0); // 서울종합운동장 야구장
        List<SeatZone> zones = insertSeatZones(target);
        log.info("[DataInitializer] 구역 {}개 삽입 완료", zones.size());

        List<Seat> seats = insertSeats(target, zones);
        log.info("[DataInitializer] 좌석 {}개 삽입 완료", seats.size());
    }

    private List<Stadium> insertStadiums() {
        List<Stadium> stadiums = List.of(
            Stadium.of("서울종합운동장 야구장", "서울 송파구",  23750),
            Stadium.of("고척스카이돔",          "서울 구로구",  16000),
            Stadium.of("인천SSG랜더스필드",      "인천 미추홀구", 23000),
            Stadium.of("수원KT위즈파크",         "경기 수원시",  18700),
            Stadium.of("대구삼성라이온즈파크",   "대구 수성구",  24000),
            Stadium.of("창원NC파크",             "경남 창원시",  17861),
            Stadium.of("사직야구장",             "부산 동래구",  22990),
            Stadium.of("광주-기아 챔피언스필드", "광주 북구",   20500),
            Stadium.of("대전 한화생명 볼파크",   "대전 중구",   20000)
        );
        return stadiumRepository.saveAll(stadiums);
    }

    /**
     * 구역 10개: 1루(101·102·103), 3루(301·302·303) = INFIELD / 외야(201·202·203·204) = OUTFIELD
     * base_price: INFIELD=18000, OUTFIELD=16000 (화~목 성인 정가 기준)
     * 요일·시기·연령 할인은 C-2 PricePolicy에서 처리 — 여기선 기준 정가만 저장
     */
    private List<SeatZone> insertSeatZones(Stadium stadium) {
        List<SeatZone> zones = List.of(
            SeatZone.of(stadium, "101", SeatGrade.INFIELD,  18000),
            SeatZone.of(stadium, "102", SeatGrade.INFIELD,  18000),
            SeatZone.of(stadium, "103", SeatGrade.INFIELD,  18000),
            SeatZone.of(stadium, "301", SeatGrade.INFIELD,  18000),
            SeatZone.of(stadium, "302", SeatGrade.INFIELD,  18000),
            SeatZone.of(stadium, "303", SeatGrade.INFIELD,  18000),
            SeatZone.of(stadium, "201", SeatGrade.OUTFIELD, 16000),
            SeatZone.of(stadium, "202", SeatGrade.OUTFIELD, 16000),
            SeatZone.of(stadium, "203", SeatGrade.OUTFIELD, 16000),
            SeatZone.of(stadium, "204", SeatGrade.OUTFIELD, 16000)
        );
        return seatZoneRepository.saveAll(zones);
    }

    /**
     * 좌석 500석: 구역 10 × 행 5(A~E) × 열 10(1~10)
     * seat_block = 구역명(예: "101"), seat_row = 행(A~E), seat_number = 열("1"~"10")
     */
    private List<Seat> insertSeats(Stadium stadium, List<SeatZone> zones) {
        String[] rows = {"A", "B", "C", "D", "E"};
        List<Seat> seats = new ArrayList<>();

        for (SeatZone zone : zones) {
            for (String row : rows) {
                for (int col = 1; col <= 10; col++) {
                    seats.add(Seat.of(stadium, zone, zone.getName(), row, String.valueOf(col)));
                }
            }
        }
        return seatRepository.saveAll(seats);
    }
}
