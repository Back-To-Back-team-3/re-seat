import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { SeatMap } from "@/components/seats/seat-map";
import type { GameSeat } from "@/types/game";

const seat = (id: number, status: GameSeat["status"] = "AVAILABLE"): GameSeat => ({
  gameSeatId: id,
  zoneId: 1,
  zoneName: "내야",
  grade: "INFIELD",
  seatBlock: "A",
  seatRow: "1",
  seatNumber: String(id),
  price: 10000,
  status,
});

// Vite App.tsx의 seat aria-label과 같은 형식(구역 · 열 · 번호 · 가격)을 그대로 쓴다.
const seatName = (id: number) => `내야 1열 ${id}번 10,000원`;

describe("좌석 선택", () => {
  afterEach(cleanup);

  it("두 좌석 선택 후 다른 좌석과 unavailable 좌석을 비활성화한다", () => {
    render(
      <SeatMap
        locked={false}
        onToggle={vi.fn()}
        seats={[seat(1), seat(2), seat(3), seat(4, "SOLD")]}
        selectedSeats={[seat(1), seat(2)]}
        selectedZoneName="내야"
      />,
    );
    expect(screen.getByRole("button", { name: seatName(3) })).toBeDisabled();
    expect(screen.getByRole("button", { name: seatName(4) })).toBeDisabled();
    expect(
      screen.getByRole("button", { name: seatName(1) }),
    ).not.toBeDisabled();
  });

  it("구역을 고르지 않으면 안내 문구를 보여준다", () => {
    render(
      <SeatMap
        locked={false}
        onToggle={vi.fn()}
        seats={[]}
        selectedSeats={[]}
        selectedZoneName={null}
      />,
    );
    expect(screen.getByText("구역을 선택해주세요.")).toBeInTheDocument();
  });

  it("좌석 목록이 비어 있으면 빈 상태 안내를 보여준다", () => {
    render(
      <SeatMap
        locked={false}
        onToggle={vi.fn()}
        seats={[]}
        selectedSeats={[]}
        selectedZoneName="내야"
      />,
    );
    expect(screen.getByText("표시할 좌석이 없습니다.")).toBeInTheDocument();
    expect(
      screen.getByText("다른 구역을 선택하거나 좌석 상태를 새로 확인해주세요."),
    ).toBeInTheDocument();
  });

  it("locked 상태에서는 선택 가능한 좌석도 클릭할 수 없다", () => {
    render(
      <SeatMap
        locked
        onToggle={vi.fn()}
        seats={[seat(1)]}
        selectedSeats={[]}
        selectedZoneName="내야"
      />,
    );
    expect(screen.getByRole("button", { name: seatName(1) })).toBeDisabled();
  });

  it("좌석 버튼을 누르면 onToggle에 해당 좌석을 전달한다", () => {
    const onToggle = vi.fn();
    render(
      <SeatMap
        locked={false}
        onToggle={onToggle}
        seats={[seat(1)]}
        selectedSeats={[]}
        selectedZoneName="내야"
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: seatName(1) }));
    expect(onToggle).toHaveBeenCalledWith(seat(1));
  });
});
