package com.backtoback.reseat.domain.reservation.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 예약 번호 생성기.
 *
 * <p>형식: RSV-yyyyMMdd-{랜덤6자리 대문자+숫자} 예: RSV-20260711-A7B3C1}
 *
 * <p>랜덤 6자리 충돌 가능성이 존재한다 (1/36^6 ≈ 0.0000022%).
 * DB unique constraint(uk_reservations_no)가 물리 방어선이다.
 * 충돌 재시도 로직은 C-3에서 추가 예정.
 */
@Component
public class ReservationNumberGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int RANDOM_LENGTH = 6;

    /**
     * return 예: RSV-20260711-A7B3C1}
     */
    public String generate() {
        String date = LocalDate.now().format(DATE_FORMATTER);
        return "RSV-" + date + "-" + generateRandom();
    }

    private String generateRandom() {
        String uuid = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        StringBuilder sb = new StringBuilder(RANDOM_LENGTH);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            int idx = Integer.parseInt(uuid.substring(i * 2, i * 2 + 2), 16) % CHARS.length();
            sb.append(CHARS.charAt(idx));
        }
        return sb.toString();
    }
}
