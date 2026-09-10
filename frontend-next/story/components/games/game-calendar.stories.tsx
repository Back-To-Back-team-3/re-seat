import {useState} from "react";
import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {GameCalendar} from "@/components/games/game-calendar";
import {GameFilters} from "@/components/games/game-filters";
import {getKstDateKey} from "@/lib/date";
import {storyGames} from "@/story/fixtures";

const today = getKstDateKey();
const calendarGames = storyGames.map((game, index) => ({
    ...game,
    gameAt: `${today} ${index === 0 ? "18:30:00" : "14:00:00"}`,
}));

const meta = {
    title: "경기/GameCalendar",
    component: GameCalendar,
    parameters: {layout: "fullscreen"},
    args: {
        games: calendarGames,
        selectedDate: today,
        onSelectDate: () => undefined,
        filters: null,
    },
    decorators: [
        (Story) => (
            <div className="mx-auto max-w-[1120px] p-6">
                <Story/>
            </div>
        ),
    ],
} satisfies Meta<typeof GameCalendar>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
    render: () => {
        const [selectedDate, setSelectedDate] = useState<string | null>(today);

        return (
            <GameCalendar
                filters={
                    <GameFilters
                        games={calendarGames}
                        onStadiumChange={() => undefined}
                        onStatusChange={() => undefined}
                        onTeamChange={() => undefined}
                        stadiumId="ALL"
                        status="ALL"
                        teamId="ALL"
                    />
                }
                games={calendarGames}
                onSelectDate={setSelectedDate}
                selectedDate={selectedDate}
            />
        );
    },
};
