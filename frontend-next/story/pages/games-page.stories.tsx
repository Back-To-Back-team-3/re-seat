import {useState} from "react";
import type {Meta, StoryObj} from "@storybook/nextjs-vite";

import {GameList} from "@/components/games/game-list";
import {GamesHero} from "@/components/games/games-hero";
import {TodayGamesPanel} from "@/components/games/today-games-panel";
import {getKstDateKey} from "@/lib/date";
import {storyGames} from "@/story/fixtures";
import type {GameSummary} from "@/types/game";

const today = getKstDateKey();
const currentGames = storyGames.map((game, index) => ({
    ...game,
    gameAt: `${today} ${index === 0 ? "18:30:00" : "19:00:00"}`,
}));

const meta = {
    title: "페이지/홈과 예매",
    parameters: {layout: "fullscreen"},
} satisfies Meta;

export default meta;
type Story = StoryObj<typeof meta>;

function GamesPagePreview({view}: {view: "home" | "booking"}) {
    const [selectedGame, setSelectedGame] = useState<GameSummary | null>(
        currentGames[0],
    );

    if (view === "home") {
        return (
            <main>
                <GamesHero
                    authenticated
                    bookingBusy={false}
                    onStartBooking={() => undefined}
                    selectedCompleted={false}
                    selectedGame={selectedGame}
                />
                <section className="mx-auto max-w-[1120px] px-6 py-12">
                    <TodayGamesPanel
                        games={currentGames}
                        onSelect={setSelectedGame}
                        selectedGameId={selectedGame?.gameId ?? null}
                    />
                </section>
            </main>
        );
    }

    return (
        <main className="mx-auto max-w-[1120px] px-6 py-12">
            <GameList
                completedGameIds={new Set<number>()}
                games={currentGames}
                onReload={() => undefined}
                onSelect={setSelectedGame}
                onStartBooking={() => undefined}
                reloading={false}
                selectedGameId={selectedGame?.gameId ?? null}
            />
        </main>
    );
}

export const Home: Story = {
    render: () => <GamesPagePreview view="home"/>,
};

export const Booking: Story = {
    render: () => <GamesPagePreview view="booking"/>,
};
