import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {GameList} from "@/components/games/game-list";
import {getKstDateKey} from "@/lib/date";
import {storyGames} from "@/story/fixtures";

const today = getKstDateKey();
const currentGames = storyGames.map((game, index) => ({
    ...game,
    gameAt: `${today} ${index === 0 ? "18:30:00" : "19:00:00"}`,
}));

const meta = {
    title: "경기/GameList",
    component: GameList,
    parameters: {layout: "fullscreen"},
    decorators: [
        (Story) => (
            <div className="mx-auto max-w-[1120px] p-6">
                <Story/>
            </div>
        ),
    ],
    args: {
        games: currentGames,
        completedGameIds: new Set<number>(),
        selectedGameId: currentGames[0].gameId,
        onSelect: () => undefined,
        onReload: () => undefined,
        reloading: false,
    },
} satisfies Meta<typeof GameList>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const CompletedGame: Story = {
    args: {completedGameIds: new Set([currentGames[0].gameId])},
};

export const Empty: Story = {
    args: {games: [], selectedGameId: null},
};
