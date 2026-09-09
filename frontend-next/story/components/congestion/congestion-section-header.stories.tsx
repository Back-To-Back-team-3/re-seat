import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {CongestionSectionHeader} from "@/components/congestion/congestion-section-header";

const congestion = {
    stadiumNum: 1,
    stadiumName: "잠실야구장",
    areaName: "잠실종합운동장",
    congestionLevel: "보통" as const,
    congestionMessage: "보통 수준의 유동인구입니다.",
    populationMin: 14000,
    populationMax: 16000,
    latitude: 37.5121,
    longitude: 127.0719,
    observedAt: "2026-09-09 21:30",
};

const meta = {
    title: "혼잡도/CongestionSectionHeader",
    component: CongestionSectionHeader,
    decorators: [
        (Story) => (
            <div className="w-[min(1120px,100%)] border border-border bg-surface">
                <Story/>
            </div>
        ),
    ],
    args: {
        congestion,
        loading: false,
        onRefresh: () => undefined,
    },
} satisfies Meta<typeof CongestionSectionHeader>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Loading: Story = {
    args: {loading: true},
};
