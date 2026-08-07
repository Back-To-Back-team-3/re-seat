import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { PaymentScreen } from "@/components/payments/payment-screen";

describe("결제 화면", () => {
  it("READY 결제에서 Toss 결제 동작을 제공한다", () => {
    render(
      <PaymentScreen
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
