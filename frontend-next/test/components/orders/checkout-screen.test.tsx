import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { CheckoutScreen } from "@/components/orders/checkout-screen";

describe("체크아웃", () => {
  it("직접 진입해 예약이 없으면 경기 목록으로 안내한다", () => {
    render(
      <CheckoutScreen
        busy={false}
        onCancel={vi.fn()}
        onCreate={vi.fn()}
        reservation={null}
        seats={[]}
      />,
    );
    expect(screen.getByText("진행 중인 예약이 없습니다.")).toBeInTheDocument();
  });
});
