import {cleanup, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";

import {CongestionSpotList} from "@/components/congestion/congestion-spot-list";
import {calculateStadiumZones} from "@/lib/stadium-zones";

const longNamedSpot = calculateStadiumZones().find(
    (spot) => spot.id === "station-exit-5-6",
)!;

describe("CongestionSpotList 컴포넌트", () => {
    afterEach(cleanup);

    it("긴 장소명에서도 분류와 혼잡도 배지는 줄바꿈하지 않는다", () => {
        render(
            <CongestionSpotList
                error={false}
                onRetry={vi.fn()}
                onSelect={vi.fn()}
                selectedSpotId={null}
                spots={[longNamedSpot]}
            />,
        );

        expect(screen.getByText("[지하철/대중교통]")).toHaveClass(
            "shrink-0",
            "whitespace-nowrap",
        );
        expect(
            screen.getByText("종합운동장역 5·6번 출구 (2·9호선)"),
        ).toHaveClass("min-w-0", "truncate");
        expect(screen.getByText("약간 붐빔")).toHaveClass(
            "shrink-0",
            "whitespace-nowrap",
        );
    });
});
