import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { CheckoutScreen } from "@/components/orders/checkout-screen";
import type { GameSeat, GameSummary } from "@/types/game";
import type { OrderResponse } from "@/types/order";
import type { ReservationResponse } from "@/types/reservation";

const game: GameSummary = {
  gameId: 1,
  title: "한화 이글스 vs 삼성 라이온즈",
  homeTeam: { teamId: 1, name: "한화 이글스" },
  awayTeam: { teamId: 2, name: "삼성 라이온즈" },
  stadium: { stadiumId: 1, name: "대전 한화생명 볼파크" },
  gameAt: new Date(Date.now() + 3_600_000).toISOString(),
  bookingOpenAt: new Date().toISOString(),
  bookingCloseAt: new Date().toISOString(),
  bookingStatus: "OPEN",
};

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

function makeOrder(overrides: Partial<OrderResponse> = {}): OrderResponse {
  return {
    orderId: 1,
    orderNo: "O-1",
    totalAmount: 20000,
    status: "CREATED",
    paymentDeadline: new Date(Date.now() + 60_000).toISOString(),
    holdExpiresAt: new Date(Date.now() + 60_000).toISOString(),
    orderItems: [],
    ...overrides,
  };
}

describe("주문 화면", () => {
  afterEach(cleanup);

  it("직접 진입해 예약도 주문도 없으면 경기 목록으로 안내한다", () => {
    render(
      <CheckoutScreen
        busy={false}
        game={null}
        onBack={vi.fn()}
        onCreateOrder={vi.fn()}
        order={null}
        reservation={null}
        seats={[]}
      />,
    );
    expect(screen.getByText("진행 중인 예약이 없습니다.")).toBeInTheDocument();
  });

  it("예약만 있으면 선점 남은 시간과 주문 생성 버튼을 보여준다", () => {
    render(
      <CheckoutScreen
        busy={false}
        game={game}
        onBack={vi.fn()}
        onCreateOrder={vi.fn()}
        order={null}
        reservation={reservation}
        seats={[seat(1, 10000), seat(2, 15000)]}
      />,
    );

    expect(screen.getByText("선점 남은 시간")).toBeInTheDocument();
    expect(screen.queryByText("결제 남은 시간")).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "주문 생성하기 →" }),
    ).toBeEnabled();
    expect(
      screen.queryByRole("button", { name: "주문 상태 확인" }),
    ).not.toBeInTheDocument();
    // 좌석 금액과 최종 결제금액에 같은 합계가 함께 나오므로 최종 금액 줄을 특정해 확인한다.
    expect(screen.getByText("최종 결제금액").parentElement).toHaveTextContent(
      "25,000원",
    );
  });

  it("주문이 생성되면 결제 남은 시간과 주문 상태 액션을 보여준다", () => {
    render(
      <CheckoutScreen
        busy={false}
        game={game}
        onBack={vi.fn()}
        onCancelOrder={vi.fn()}
        onPayment={vi.fn()}
        onRefreshOrder={vi.fn()}
        order={makeOrder()}
        seats={[seat(1, 20000)]}
      />,
    );

    expect(screen.getByText("결제 남은 시간")).toBeInTheDocument();
    expect(screen.queryByText("선점 남은 시간")).not.toBeInTheDocument();
    expect(screen.getByText("O-1")).toBeInTheDocument();
    expect(screen.getByText("CREATED")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "20,000원 결제 준비 →" }),
    ).toBeEnabled();
    expect(
      screen.getByRole("button", { name: "주문 상태 확인" }),
    ).toBeEnabled();
    expect(screen.getByRole("button", { name: "주문 취소" })).toBeEnabled();
    expect(
      screen.queryByRole("button", { name: "주문 생성하기 →" }),
    ).not.toBeInTheDocument();
  });

  it("주문 상태가 CREATED가 아니면 결제 준비와 주문 취소 버튼이 비활성화된다", () => {
    render(
      <CheckoutScreen
        busy={false}
        game={game}
        onBack={vi.fn()}
        onCancelOrder={vi.fn()}
        onPayment={vi.fn()}
        onRefreshOrder={vi.fn()}
        order={makeOrder({ status: "PAID" })}
        seats={[seat(1, 20000)]}
      />,
    );

    expect(
      screen.getByRole("button", { name: "20,000원 결제 준비 →" }),
    ).toBeDisabled();
    expect(screen.getByRole("button", { name: "주문 취소" })).toBeDisabled();
    expect(
      screen.getByRole("button", { name: "주문 상태 확인" }),
    ).toBeEnabled();
  });

  it("결제 기한이 만료되면 경고 문구와 함께 결제 준비 버튼이 비활성화된다", () => {
    render(
      <CheckoutScreen
        busy={false}
        game={game}
        onBack={vi.fn()}
        onCancelOrder={vi.fn()}
        onPayment={vi.fn()}
        onRefreshOrder={vi.fn()}
        order={makeOrder({ paymentDeadline: new Date(Date.now() - 1_000).toISOString() })}
        seats={[seat(1, 20000)]}
      />,
    );

    expect(
      screen.getByText("제한시간이 만료되어 더 이상 진행할 수 없습니다."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "결제 시간 만료" }),
    ).toBeDisabled();
  });

  it("busy 상태에서는 좌석 선택으로 돌아가기 버튼이 비활성화된다", () => {
    render(
      <CheckoutScreen
        busy
        game={game}
        onBack={vi.fn()}
        onCreateOrder={vi.fn()}
        order={null}
        reservation={reservation}
        seats={[seat(1)]}
      />,
    );

    expect(
      screen.getByRole("button", { name: "← 좌석 선택으로" }),
    ).toBeDisabled();
  });
});
