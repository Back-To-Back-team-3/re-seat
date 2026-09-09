import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {GamesHero} from "@/components/games/games-hero";
import {storyGames} from "@/story/fixtures";

const meta = {
    title: "경기/GamesHero",
    component: GamesHero,
    parameters: {layout: "fullscreen"},
    args: {
        selectedGame: storyGames[0],
        todayGames: storyGames,
        authenticated: true,
        bookingBusy: false,
        selectedCompleted: false,
        onSelect: () => undefined,
        onStartBooking: () => undefined,
    },
} satisfies Meta<typeof GamesHero>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Authenticated: Story = {};

export const Guest: Story = {
    args: {authenticated: false},
};

export const Completed: Story = {
    args: {selectedCompleted: true},
};

export const NoSelection: Story = {
    args: {selectedGame: null},
};
