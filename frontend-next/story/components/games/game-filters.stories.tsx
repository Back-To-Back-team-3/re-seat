import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {GameFilters} from "@/components/games/game-filters";
import {storyGames} from "@/story/fixtures";

const meta = {
    title: "경기/GameFilters",
    component: GameFilters,
    decorators: [
        (Story) => (
            <div className="flex flex-wrap gap-3 bg-surface p-6">
                <Story/>
            </div>
        ),
    ],
    args: {
        games: storyGames,
        teamId: "ALL",
        stadiumId: "ALL",
        status: "ALL",
        onTeamChange: () => undefined,
        onStadiumChange: () => undefined,
        onStatusChange: () => undefined,
    },
} satisfies Meta<typeof GameFilters>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Filtered: Story = {
    args: {
        teamId: storyGames[0].homeTeam.teamId,
        stadiumId: storyGames[0].stadium.stadiumId,
        status: "OPEN",
    },
};
