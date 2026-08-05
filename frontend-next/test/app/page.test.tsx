import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import Home from "@/app/page";

describe("마이그레이션 준비 화면", () => {
  it("안내 제목과 기존 Vite 앱 링크를 표시한다", () => {
    render(<Home />);

    expect(
      screen.getByRole("heading", { name: "마이그레이션 준비 중" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "기존 Vite 앱 열기" }),
    ).toHaveAttribute("href", "http://localhost:5173");
  });
});
