import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {TodayGamesPanel} from "@/components/games/today-games-panel";
import {storyGames} from "@/story/fixtures";

const meta = {
    title: "경기/TodayGamesPanel",
    component: TodayGamesPanel,
    decorators: [
        (Story) => (
            <div className="w-[min(680px,100%)] p-6">
                <Story/>
            </div>
        ),
    ],
    args: {
        authenticated: true,
        bookingBusy: false,
        completedGameIds: new Set<number>(),
        games: storyGames,
        onStartBooking: () => undefined,
    },
} satisfies Meta<typeof TodayGamesPanel>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Selected: Story = {};

export const Overflow: Story = {
    args: {
        games: Array.from({length: 6}, (_, index) => ({
            ...storyGames[index % storyGames.length],
            gameId: index + 1,
        })),
    },
};

export const Empty: Story = {
    args: {games: []},
};
