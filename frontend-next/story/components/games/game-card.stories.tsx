import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {GameCard} from "@/components/games/game-card";
import {storyGames} from "@/story/fixtures";

const meta = {
    title: "경기/GameCard",
    component: GameCard,
    decorators: [
        (Story) => (
            <div className="w-[min(380px,100%)] p-6">
                <Story/>
            </div>
        ),
    ],
    args: {
        game: storyGames[0],
        completed: false,
        selected: false,
        onSelect: () => undefined,
    },
} satisfies Meta<typeof GameCard>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Selected: Story = {
    args: {selected: true},
};

export const Completed: Story = {
    args: {completed: true},
};

export const Scheduled: Story = {
    args: {game: storyGames[1]},
};
