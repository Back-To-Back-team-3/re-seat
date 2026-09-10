import {cleanup, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";

import {GamesHero} from "@/components/games/games-hero";
import type {GameSummary} from "@/types/game";

const game = {
    gameId: 111,
    title: "LG 트윈스 vs 두산 베어스",
    homeTeam: {teamId: 1, name: "LG 트윈스"},
    awayTeam: {teamId: 2, name: "두산 베어스"},
    stadium: {stadiumId: 1, name: "잠실야구장"},
    gameAt: "2099-09-12 18:30:00",
    bookingOpenAt: "2099-09-01 10:00:00",
    bookingCloseAt: "2099-09-12 17:30:00",
    bookingStatus: "OPEN",
} satisfies GameSummary;

describe("GamesHero 컴포넌트", () => {
    afterEach(() => {
        cleanup();
    });

    it("선택 경기 영역이 부모 너비 안에서 줄어들 수 있다", () => {
        render(
            <GamesHero
                authenticated
                bookingBusy={false}
                onSelect={vi.fn()}
                onStartBooking={vi.fn()}
                selectedCompleted={false}
                selectedGame={game}
                todayGames={[]}
            />,
        );

        expect(screen.getByRole("group", {name: "선택 경기"})).toHaveClass(
            "w-full",
            "max-w-[590px]",
        );
    });
});
