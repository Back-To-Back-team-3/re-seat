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
                onStartBooking={vi.fn()}
                selectedCompleted={false}
                selectedGame={game}
            />,
        );

        expect(screen.getByRole("group", {name: "선택 경기"})).toHaveClass(
            "w-full",
            "max-w-[590px]",
        );
    });

    it("잠실야구장 이미지를 히어로 전체의 장식 배경으로 표시한다", () => {
        const {container} = render(
            <GamesHero
                authenticated
                bookingBusy={false}
                onStartBooking={vi.fn()}
                selectedCompleted={false}
                selectedGame={game}
            />,
        );

        const backgroundImage = container.querySelector(
            'section > img[src="/jamsil-stadium.jpg"]',
        );

        expect(backgroundImage).toHaveAttribute("alt", "");
        expect(backgroundImage).toHaveClass(
            "absolute",
            "inset-0",
            "object-cover",
        );
    });

    it("히어로 강조 문구에 브랜드 색상을 사용한다", () => {
        render(
            <GamesHero
                authenticated
                bookingBusy={false}
                onStartBooking={vi.fn()}
                selectedCompleted={false}
                selectedGame={game}
            />,
        );

        expect(screen.getByText("예매하세요")).toHaveClass("text-brand");
    });
});
