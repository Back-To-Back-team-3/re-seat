import { cleanup, render, screen } from "@testing-library/react";
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

describe("좌석 선택", () => {
  afterEach(cleanup);
  it("두 좌석 선택 후 다른 좌석과 unavailable 좌석을 비활성화한다", () => {
    render(
      <SeatMap
        locked={false}
        onToggle={vi.fn()}
        seats={[seat(1), seat(2), seat(3), seat(4, "SOLD")]}
        selectedSeats={[seat(1), seat(2)]}
      />,
    );
    expect(screen.getByRole("button", { name: "1열 3번" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "1열 4번" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "1열 1번" })).not.toBeDisabled();
  });
});
