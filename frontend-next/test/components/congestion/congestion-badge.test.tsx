import {cleanup, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it} from "vitest";

import {CongestionBadge} from "@/components/congestion/congestion-badge";
import type {CongestionLevel} from "@/types/congestion";

describe("CongestionBadge 컴포넌트", () => {
    afterEach(cleanup);

    const levels: CongestionLevel[] = ["여유", "보통", "약간 붐빔", "붐빔"];

    levels.forEach((level) => {
        it(`'${level}' 혼잡도 상태를 올바르게 렌더링한다`, () => {
            render(<CongestionBadge level={level} />);
            expect(screen.getByText(level)).toBeInTheDocument();
        });
    });

    it("기본/알 수 없는 혼잡도 상태일 때 보통으로 fallback 처리한다", () => {
        /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
        render(<CongestionBadge level={"알수없음" as any} />);
        expect(screen.getByText("보통")).toBeInTheDocument();
    });
});
