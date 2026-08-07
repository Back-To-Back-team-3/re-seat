import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { PaymentScreen } from "@/components/payments/payment-screen";

describe("결제 화면", () => {
  it("결제 조회가 실패하면 로딩 대신 오류 원인을 표시한다", () => {
    render(
      <PaymentScreen
        error="인증 정보가 유효하지 않거나 만료되었습니다."
        onPay={vi.fn()}
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

  it("READY 결제에서 Toss 결제 동작을 제공한다", () => {
    render(
      <PaymentScreen
        error={null}
        onPay={vi.fn()}
        payment={{
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
        }}
      />,
    );
    expect(
      screen.getByRole("button", { name: "Toss로 결제하기" }),
    ).toBeInTheDocument();
  });
});
