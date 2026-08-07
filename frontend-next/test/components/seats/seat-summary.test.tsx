import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { SeatSummary } from "@/components/seats/seat-summary";
import type { GameSeat } from "@/types/game";
import type { ReservationResponse } from "@/types/reservation";

const seat = (id: number, price = 10000): GameSeat => ({
  gameSeatId: id,
  zoneId: 1,
  zoneName: "내야",
  grade: "INFIELD",
  seatBlock: "A",
  seatRow: "1",
  seatNumber: String(id),
  price,
  status: "AVAILABLE",
});

const reservation: ReservationResponse = {
  reservationId: 1,
  reservationNo: "R-1",
  status: "HOLDING",
  gameSeats: [],
  holdExpiresAt: new Date(Date.now() + 60_000).toISOString(),
  gameAt: new Date().toISOString(),
};

describe("좌석 선택 요약", () => {
  afterEach(cleanup);

  it("선택한 좌석 수와 합계 금액을 보여준다", () => {
    render(
      <SeatSummary
        busy={false}
        locked={false}
        onCancelReservation={vi.fn()}
        onContinue={vi.fn()}
        onReserve={vi.fn()}
        reservation={null}
        seats={[seat(1, 10000), seat(2, 15000)]}
        timerExpired={false}
        timerTarget={new Date(Date.now() + 60_000).toISOString()}
      />,
    );

    expect(
      screen.getByRole("button", { name: "2석 선점하기 →" }),
    ).toBeInTheDocument();
    expect(screen.getByText("25,000원")).toBeInTheDocument();
  });

  it("좌석을 고르지 않으면 선점 버튼이 비활성화된다", () => {
    render(
      <SeatSummary
        busy={false}
        locked={false}
        onCancelReservation={vi.fn()}
        onContinue={vi.fn()}
        onReserve={vi.fn()}
        reservation={null}
        seats={[]}
        timerExpired={false}
        timerTarget={null}
      />,
    );

    expect(
      screen.getByRole("button", { name: "0석 선점하기 →" }),
    ).toBeDisabled();
    expect(
      screen.getByText("좌석을 선택하면 이곳에 표시됩니다."),
    ).toBeInTheDocument();
  });

  it("예약이 있으면 선점 버튼 대신 주문 이동과 선점 해제 버튼을 보여준다", () => {
    render(
      <SeatSummary
        busy={false}
        locked
        onCancelReservation={vi.fn()}
        onContinue={vi.fn()}
        onReserve={vi.fn()}
        reservation={reservation}
        seats={[seat(1)]}
        timerExpired={false}
        timerTarget={reservation.holdExpiresAt}
      />,
    );

    expect(
      screen.getByRole("button", { name: "주문 정보 입력 →" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "선점 해제" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /선점하기/ }),
    ).not.toBeInTheDocument();
  });

  it("제한시간이 끝나면 선점 버튼 문구가 바뀌고 비활성화된다", () => {
    render(
      <SeatSummary
        busy={false}
        locked
        onCancelReservation={vi.fn()}
        onContinue={vi.fn()}
        onReserve={vi.fn()}
        reservation={null}
        seats={[seat(1)]}
        timerExpired
        timerTarget={new Date(Date.now() - 1000).toISOString()}
      />,
    );

    expect(
      screen.getByRole("button", { name: "좌석 선택 시간 만료" }),
    ).toBeDisabled();
  });

  it("busy 상태에서는 선점 해제와 주문 이동 버튼이 비활성화된다", () => {
    render(
      <SeatSummary
        busy
        locked
        onCancelReservation={vi.fn()}
        onContinue={vi.fn()}
        onReserve={vi.fn()}
        reservation={reservation}
        seats={[seat(1)]}
        timerExpired={false}
        timerTarget={reservation.holdExpiresAt}
      />,
    );

    expect(screen.getByRole("button", { name: "주문 정보 입력 →" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "선점 해제" })).toBeDisabled();
  });

  it("onContinue가 없으면(다른 화면 재사용) 기존 단순 요약을 그대로 보여준다", () => {
    render(<SeatSummary busy={false} locked onReserve={vi.fn()} seats={[seat(1)]} />);

    expect(
      screen.getByRole("button", { name: "예약 완료" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /선점하기|선점 해제|주문 정보/ }),
    ).not.toBeInTheDocument();
  });
});
