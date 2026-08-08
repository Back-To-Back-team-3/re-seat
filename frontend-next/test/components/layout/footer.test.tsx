import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { Footer } from "@/components/layout/footer";

describe("Footer", () => {
  afterEach(() => {
    cleanup();
  });

  it("브랜드, 지원, 정책 영역과 저작권 문구를 보여준다", () => {
    render(<Footer />);

    expect(
      screen.getByText("KBO 리그 공식 예매 파트너.", { exact: false }),
    ).toBeInTheDocument();
    expect(screen.getByText("SUPPORT")).toBeInTheDocument();
    expect(screen.getByText("POLICY")).toBeInTheDocument();
    expect(
      screen.getByText("© 2026 Re:Seat. All rights reserved.", {
        exact: false,
      }),
    ).toBeInTheDocument();
  });

  it("구장 사진 출처 링크는 새 탭에서 연다", () => {
    render(<Footer />);

    const attribution = screen.getByRole("link", {
      name: "구장 사진: Christophe95, CC BY-SA 4.0",
    });
    expect(attribution).toHaveAttribute("target", "_blank");
    expect(attribution).toHaveAttribute("rel", "noreferrer");
  });
});
