import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";

import {TodayGamesPanel} from "@/components/games/today-games-panel";
import type {GameSummary} from "@/types/game";

const originalScrollBy = Object.getOwnPropertyDescriptor(
    HTMLElement.prototype,
    "scrollBy",
);

const game = {
    gameId: 1,
    title: "LG 트윈스 vs 두산 베어스",
    homeTeam: {teamId: 1, name: "LG 트윈스"},
    awayTeam: {teamId: 2, name: "두산 베어스"},
    stadium: {stadiumId: 1, name: "잠실야구장"},
    gameAt: "2099-09-12 18:30:00",
    bookingOpenAt: "2099-09-01 10:00:00",
    bookingCloseAt: "2099-09-12 17:30:00",
    bookingStatus: "OPEN",
} satisfies GameSummary;

describe("TodayGamesPanel 컴포넌트", () => {
    afterEach(() => {
        cleanup();
        vi.restoreAllMocks();
        if (originalScrollBy) {
            Object.defineProperty(
                HTMLElement.prototype,
                "scrollBy",
                originalScrollBy,
            );
        } else {
            Reflect.deleteProperty(HTMLElement.prototype, "scrollBy");
        }
    });

    it("오늘의 경기 카드를 수평 스냅 목록으로 표시한다", () => {
        render(
            <TodayGamesPanel
                authenticated
                bookingBusy={false}
                completedGameIds={new Set()}
                games={[game, {...game, gameId: 2}]}
                onStartBooking={vi.fn()}
            />,
        );

        expect(screen.getByRole("list", {name: "오늘 경기 목록"})).toHaveClass(
            "flex",
            "overflow-x-auto",
            "snap-x",
        );
    });

    it("목록이 넘치면 오른쪽 버튼으로 다음 카드 영역을 보여준다", async () => {
        const scrollBy = vi.fn();
        vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockReturnValue(
            600,
        );
        vi.spyOn(HTMLElement.prototype, "scrollWidth", "get").mockReturnValue(
            1200,
        );
        Object.defineProperty(HTMLElement.prototype, "scrollBy", {
            configurable: true,
            value: scrollBy,
        });

        render(
            <TodayGamesPanel
                authenticated
                bookingBusy={false}
                completedGameIds={new Set()}
                games={[game, {...game, gameId: 2}, {...game, gameId: 3}]}
                onStartBooking={vi.fn()}
            />,
        );

        const gameList = screen.getByRole("list", {name: "오늘 경기 목록"});
        const nextButton = await screen.findByRole("button", {
            name: "다음 경기 보기",
        });

        expect(gameList.parentElement).toHaveClass(
            "grid-cols-[44px_minmax(0,1fr)_44px]",
        );
        expect(nextButton).not.toHaveClass("absolute");

        fireEvent.click(nextButton);

        expect(scrollBy).toHaveBeenCalledWith({
            behavior: "smooth",
            left: 510,
        });
    });

    it("예매 가능한 경기 카드에서 바로 예매를 시작한다", () => {
        const onStartBooking = vi.fn();

        render(
            <TodayGamesPanel
                authenticated
                bookingBusy={false}
                completedGameIds={new Set()}
                games={[game]}
                onStartBooking={onStartBooking}
            />,
        );

        fireEvent.click(screen.getByRole("button", {name: "예매하기"}));

        expect(onStartBooking).toHaveBeenCalledWith(game);
    });

    it("예매 예정 경기는 상태 문구를 표시하고 버튼을 비활성화한다", () => {
        render(
            <TodayGamesPanel
                authenticated
                bookingBusy={false}
                completedGameIds={new Set()}
                games={[{...game, bookingStatus: "SCHEDULED"}]}
                onStartBooking={vi.fn()}
            />,
        );

        expect(
            screen.getByRole("button", {name: "예매 준비 중"}),
        ).toBeDisabled();
    });

    it("로그아웃 상태에서도 예매 가능한 경기는 예매하기로 표시한다", () => {
        render(
            <TodayGamesPanel
                authenticated={false}
                bookingBusy={false}
                completedGameIds={new Set()}
                games={[game]}
                onStartBooking={vi.fn()}
            />,
        );

        expect(
            screen.getByRole("button", {name: "예매하기"}),
        ).toBeEnabled();
    });
});
