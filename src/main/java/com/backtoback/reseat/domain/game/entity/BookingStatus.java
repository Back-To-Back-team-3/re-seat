package com.backtoback.reseat.domain.game.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * 경기 예매 상태.
 * <p>SCHEDULED→OPEN, OPEN→CLOSED, SCHEDULED/OPEN/CLOSED→CANCELLED.
 * <p>CLOSED→CANCELLED를 허용하는 이유:
 * 시드 데이터상 booking_close_at과 game_at이 같아 경기 시작 시점의 상태는 이미 CLOSED다.
 * 우천 취소는 그 이후 발생하므로 CLOSED를 종단 상태로 두면 취소된 경기를 표현할 수단이 없어진다.
 */
public enum BookingStatus {
    /** 예매 오픈 전 (경기 등록 시 기본값) */
    SCHEDULED {
        @Override
        public Set<BookingStatus> allowedTransitions() {
            return EnumSet.of(OPEN, CANCELLED);
        }
    },

    /** 예매 가능 — 이 상태에서만 대기열 진입·좌석 선점이 허용된다 */
    OPEN {
        @Override
        public Set<BookingStatus> allowedTransitions() {
            return EnumSet.of(CLOSED, CANCELLED);
        }
    },

    /** 예매 마감 — 재오픈은 허용하지 않고, 경기 취소로만 빠져나갈 수 있다 */
    CLOSED {
        @Override
        public Set<BookingStatus> allowedTransitions() {
            return EnumSet.of(CANCELLED);
        }
    },

    /** 경기 취소 (우천 등) — 종단 상태. **/
    CANCELLED {
        @Override
        public Set<BookingStatus> allowedTransitions() {
            return EnumSet.noneOf(BookingStatus.class);
        }
    };

    /**
     * 이 상태에서 전이 가능한 상태 목록을 반환한다.
     * <p>관리자 화면에서 선택 가능한 전이를 노출하는 데 사용한다.
     * 호출마다 새 EnumSet을 반환하므로 반환값을 변경해도 원본 규칙은 오염되지 않는다.
     */
    public abstract Set<BookingStatus> allowedTransitions();

    /**
     * 대상 상태로 전이할 수 있는지 판단한다.
     */
    public boolean canTransitionTo(BookingStatus target) {
        return allowedTransitions().contains(target);
    }
}
