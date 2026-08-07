import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { PaymentScreen } from "@/components/payments/payment-screen";
import type { GameSummary } from "@/types/game";
import type { OrderResponse } from "@/types/order";
import type { PaymentResponse } from "@/types/payment";

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

function makeOrder(overrides: Partial<OrderResponse> = {}): OrderResponse {
  return {
    orderId: 2,
    orderNo: "O-2",
    totalAmount: 20000,
    status: "CREATED",
    paymentDeadline: new Date(Date.now() + 60_000).toISOString(),
    holdExpiresAt: new Date(Date.now() + 60_000).toISOString(),
    orderItems: [],
    ...overrides,
  };
}

function makePayment(overrides: Partial<PaymentResponse> = {}): PaymentResponse {
  return {
    paymentId: 1,
    paymentNo: "P-1",
    orderId: 2,
    amount: 20000,
    method: null,
    status: "READY",
    pgProvider: "TOSS",
    failReason: null,
    approvedAt: null,
    failedAt: null,
    ...overrides,
  };
}

const noop = {
  onBack: vi.fn(),
  onOpenPayment: vi.fn(),
  onRefreshOrder: vi.fn(),
  onTickets: vi.fn(),
};

describe("결제 화면", () => {
  afterEach(cleanup);

  it("결제 조회가 실패하면 로딩 대신 오류 원인을 표시한다", () => {
    render(
      <PaymentScreen
        {...noop}
        busy={false}
        error="인증 정보가 유효하지 않거나 만료되었습니다."
        game={null}
        order={null}
        payment={null}
      />,
    );

    expect(
      screen.getByText("인증 정보가 유효하지 않거나 만료되었습니다."),
    ).toBeInTheDocument();
    expect(
      screen.queryByText("결제 정보를 불러오고 있습니다."),
    ).not.toBeInTheDocument();
  });

  it("결제 정보가 아직 없으면 불러오는 중임을 안내한다", () => {
    render(
      <PaymentScreen
        {...noop}
        busy={false}
        error={null}
        game={null}
        order={null}
        payment={null}
      />,
    );

    expect(
      screen.getByText("결제 정보를 불러오고 있습니다."),
    ).toBeInTheDocument();
  });

  it("READY 결제에서는 주문으로 돌아가기와 Toss 결제창 열기를 제공한다", () => {
    render(
      <PaymentScreen
        {...noop}
        busy={false}
        error={null}
        game={game}
        order={makeOrder()}
        payment={makePayment({ status: "READY" })}
      />,
    );

    expect(
      screen.getByRole("button", { name: "← 주문으로 돌아가기" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Toss 결제창 열기 →" }),
    ).toBeEnabled();
    expect(screen.getByText("O-2")).toBeInTheDocument();
    expect(screen.getByText("P-1")).toBeInTheDocument();
  });

  it("FAILED 결제에서는 결제 상태가 실패로 표시되고 PG 버튼이 비활성화된다", () => {
    render(
      <PaymentScreen
        {...noop}
        busy={false}
        error={null}
        game={game}
        order={makeOrder()}
        payment={makePayment({ status: "FAILED", failReason: "카드 승인 거절" })}
      />,
    );

    expect(screen.getByText("FAILED")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Toss 결제창 열기 →" }),
    ).toBeDisabled();
    expect(
      screen.queryByRole("button", { name: "내 티켓 확인 →" }),
    ).not.toBeInTheDocument();
  });

  it("결제 기한이 만료되면 만료 문구와 함께 PG 버튼이 비활성화된다", () => {
    render(
      <PaymentScreen
        {...noop}
        busy={false}
        error={null}
        game={game}
        order={makeOrder({
          paymentDeadline: new Date(Date.now() - 1_000).toISOString(),
        })}
        payment={makePayment({ status: "READY" })}
      />,
    );

    expect(
      screen.getByText("결제시간이 만료되었습니다. 주문 상태를 확인해주세요."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "결제 시간 만료" }),
    ).toBeDisabled();
  });

  it("결제가 승인되면 완료 화면과 티켓 확인 버튼을 보여주고 PG 버튼을 숨긴다", () => {
    render(
      <PaymentScreen
        {...noop}
        busy={false}
        error={null}
        game={game}
        order={makeOrder({ status: "PAID" })}
        payment={makePayment({ status: "APPROVED" })}
      />,
    );

    expect(screen.getByText("예매가 완료되었습니다!")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "내 티켓 확인 →" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Toss 결제창 열기 →" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "← 주문으로 돌아가기" }),
    ).not.toBeInTheDocument();
  });
});
