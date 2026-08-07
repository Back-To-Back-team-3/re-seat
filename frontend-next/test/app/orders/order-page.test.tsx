import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import OrderPage from "@/app/(booking)/orders/[orderId]/page";

const routeParams = vi.hoisted(() => ({ orderId: "1" }));

vi.mock("next/navigation", () => ({
  useParams: () => routeParams,
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("@/hooks/use-order", () => ({
  useOrder: () => ({
    detail: {
      data: undefined,
      error: new Error("인증 정보가 유효하지 않거나 만료되었습니다."),
      isError: true,
    },
    cancel: { mutate: vi.fn() },
  }),
}));

vi.mock("@/hooks/use-payment", () => ({
  usePayment: () => ({
    prepare: { isPending: false, mutate: vi.fn() },
  }),
}));

vi.mock("@/providers/booking-store-provider", () => ({
  useBookingStore: (
    selector: (state: {
      selectedGameId: null;
      selectedSeats: never[];
    }) => unknown,
  ) => selector({ selectedGameId: null, selectedSeats: [] }),
}));

describe("주문 상세 페이지", () => {
  afterEach(() => {
    cleanup();
    routeParams.orderId = "1";
  });

  it("주문 조회가 실패하면 로딩 대신 오류 원인을 표시한다", () => {
    render(<OrderPage />);

    expect(
      screen.getByText("인증 정보가 유효하지 않거나 만료되었습니다."),
    ).toBeInTheDocument();
    expect(
      screen.queryByText("주문을 불러오고 있습니다."),
    ).not.toBeInTheDocument();
  });

  it("숫자가 아닌 주문 ID로 진입하면 잘못된 주소임을 안내한다", () => {
    routeParams.orderId = "not-a-number";

    render(<OrderPage />);

    expect(screen.getByText("올바르지 않은 주문 주소입니다.")).toBeInTheDocument();
    expect(
      screen.queryByText("주문을 불러오고 있습니다."),
    ).not.toBeInTheDocument();
  });
});
