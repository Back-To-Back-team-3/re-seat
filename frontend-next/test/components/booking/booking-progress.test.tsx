import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { BookingProgress } from "@/components/booking/booking-progress";

describe("BookingProgress", () => {
  it("현재 단계와 이미 완료한 단계를 구분해 표시한다", () => {
    render(<BookingProgress activeStep="checkout" />);

    const progress = screen.getByRole("list", { name: "예매 진행 단계" });
    const steps = within(progress).getAllByRole("listitem");

    expect(steps).toHaveLength(4);
    expect(steps[0]).toHaveTextContent("✓예매 대기");
    expect(steps[1]).toHaveTextContent("✓좌석 선택");
    expect(steps[2]).toHaveTextContent("3주문");
    expect(steps[2]).toHaveAttribute("aria-current", "step");
    expect(steps[3]).toHaveTextContent("4결제");
    expect(steps[3]).not.toHaveAttribute("aria-current");
  });
});
