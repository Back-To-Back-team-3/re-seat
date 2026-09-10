import {render, screen} from "@testing-library/react";
import {describe, expect, it} from "vitest";

import {Button} from "@/components/ui/button";

describe("Button", () => {
    it("로딩 중에는 버튼을 비활성화하고 진행 상태를 알린다", () => {
        render(<Button loading>결제 처리 중</Button>);

        const button = screen.getByRole("button", {name: "결제 처리 중"});

        expect(button).toBeDisabled();
        expect(button).toHaveAttribute("aria-busy", "true");
    });
});
