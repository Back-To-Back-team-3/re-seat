import {cleanup, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it} from "vitest";

import {Footer} from "@/components/layout/footer";

describe("Footer", () => {
    afterEach(() => {
        cleanup();
    });

    it("브랜드 소개와 저작권 문구만 보여준다", () => {
        render(<Footer/>);

        expect(
            screen.getByText("KBO 리그 예매 서비스.", {exact: false}),
        ).toBeInTheDocument();
        expect(screen.queryByText("SUPPORT")).not.toBeInTheDocument();
        expect(screen.queryByText("POLICY")).not.toBeInTheDocument();
        expect(
            screen.getByText("© 2026 Re:Seat. All rights reserved.", {
                exact: false,
            }),
        ).toBeInTheDocument();
    });

    it("구장 사진 출처 링크는 새 탭에서 연다", () => {
        render(<Footer/>);

        const attribution = screen.getByRole("link", {
            name: "구장 사진: Christophe95, CC BY-SA 4.0",
        });
        expect(attribution).toHaveAttribute("target", "_blank");
        expect(attribution).toHaveAttribute("rel", "noreferrer");
    });
});
