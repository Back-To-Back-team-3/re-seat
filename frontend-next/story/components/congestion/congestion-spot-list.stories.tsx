import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {CongestionSpotList} from "@/components/congestion/congestion-spot-list";
import {calculateStadiumZones} from "@/lib/stadium-zones";

const spots = calculateStadiumZones({
    stadiumNum: 1,
    stadiumName: "잠실야구장",
    areaName: "잠실종합운동장",
    congestionLevel: "보통",
    congestionMessage: "보통 수준의 유동인구입니다.",
    populationMin: 14000,
    populationMax: 16000,
    latitude: 37.5121,
    longitude: 127.0719,
    observedAt: "2026-09-09 21:30",
});

const meta = {
    title: "혼잡도/CongestionSpotList",
    component: CongestionSpotList,
    decorators: [
        (Story) => (
            <div className="h-[580px] w-[420px] border border-border bg-surface">
                <Story/>
            </div>
        ),
    ],
    args: {
        spots,
        selectedSpotId: null,
        error: false,
        onSelect: () => undefined,
        onRetry: () => undefined,
    },
} satisfies Meta<typeof CongestionSpotList>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Selected: Story = {
    args: {selectedSpotId: spots[0].id},
};

export const Error: Story = {
    args: {error: true},
};
