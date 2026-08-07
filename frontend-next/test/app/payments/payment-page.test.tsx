import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import PaymentPage from "@/app/(booking)/payments/[paymentId]/page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ paymentId: "not-a-number" }),
}));

vi.mock("@/hooks/use-payment", () => ({
  usePayment: () => ({
    detail: {
      data: undefined,
      error: null,
    },
  }),
}));

describe("결제 상세 페이지", () => {
  it("숫자가 아닌 결제 ID로 진입하면 잘못된 주소임을 안내한다", () => {
    render(<PaymentPage />);

    expect(screen.getByText("올바르지 않은 결제 주소입니다.")).toBeInTheDocument();
    expect(
      screen.queryByText("결제 정보를 불러오고 있습니다."),
    ).not.toBeInTheDocument();
  });
});
