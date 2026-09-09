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
        games: storyGames,
        selectedGameId: storyGames[0].gameId,
        onSelect: () => undefined,
    },
} satisfies Meta<typeof TodayGamesPanel>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Selected: Story = {};

export const Empty: Story = {
    args: {games: [], selectedGameId: null},
};
