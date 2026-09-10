import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {GameSummaryBar} from "@/components/booking/game-summary-bar";

const game = {
    gameId: 1,
    title: "LG 트윈스 vs 두산 베어스",
    homeTeam: {teamId: 1, name: "LG 트윈스"},
    awayTeam: {teamId: 2, name: "두산 베어스"},
    stadium: {stadiumId: 1, name: "잠실야구장"},
    gameAt: "2026-09-12T18:30:00",
    bookingOpenAt: "2026-09-01T10:00:00",
    bookingCloseAt: "2026-09-12T17:30:00",
    bookingStatus: "OPEN" as const,
};

const meta = {
    title: "예매/GameSummaryBar",
    component: GameSummaryBar,
    decorators: [
        (Story) => (
            <div className="w-[min(820px,100%)] p-6">
                <Story/>
            </div>
        ),
    ],
    args: {game},
} satisfies Meta<typeof GameSummaryBar>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const WithLegend: Story = {
    args: {
        children: (
            <div className="ml-auto flex items-center gap-2 text-xs text-muted-foreground">
                <span className="size-3 rounded-sm bg-brand"/>
                선택 좌석
            </div>
        ),
    },
};
