import {act, cleanup, fireEvent, render, screen} from "@testing-library/react";
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
const nextGame = {
    ...game,
    gameId: 112,
    title: "키움 히어로즈 vs KIA 타이거즈",
} satisfies GameSummary;

describe("GamesHero 컴포넌트", () => {
    afterEach(() => {
        cleanup();
        vi.useRealTimers();
    });

    it("선택 경기 영역을 히어로 오른쪽 열에 표시한다", () => {
        render(
            <GamesHero
                authenticated
                bookingBusy={false}
                games={[game]}
                onSelect={vi.fn()}
                onStartBooking={vi.fn()}
                selectedCompleted={false}
                selectedGame={game}
            />,
        );

        expect(screen.getByRole("group", {name: "선택 경기"})).toHaveClass(
            "w-full",
            "max-w-[460px]",
            "lg:col-start-2",
        );
    });

    it("잠실야구장 이미지를 히어로 전체의 장식 배경으로 표시한다", () => {
        const {container} = render(
            <GamesHero
                authenticated
                bookingBusy={false}
                games={[game]}
                onSelect={vi.fn()}
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
                games={[game]}
                onSelect={vi.fn()}
                onStartBooking={vi.fn()}
                selectedCompleted={false}
                selectedGame={game}
            />,
        );

        expect(screen.getByText("예매하세요")).toHaveClass("text-brand");
    });

    it("로그아웃 상태에서도 예매 가능한 경기는 예매하기로 표시한다", () => {
        render(
            <GamesHero
                authenticated={false}
                bookingBusy={false}
                games={[game]}
                onSelect={vi.fn()}
                onStartBooking={vi.fn()}
                selectedCompleted={false}
                selectedGame={game}
            />,
        );

        expect(
            screen.getByRole("button", {name: /예매하기/}),
        ).toBeEnabled();
    });

    it("일정 주기마다 다음 오늘 경기로 전환한다", () => {
        vi.useFakeTimers();
        const onSelect = vi.fn();

        render(
            <GamesHero
                authenticated
                bookingBusy={false}
                games={[game, nextGame]}
                onSelect={onSelect}
                onStartBooking={vi.fn()}
                selectedCompleted={false}
                selectedGame={game}
            />,
        );

        act(() => vi.advanceTimersByTime(5000));

        expect(onSelect).toHaveBeenCalledWith(nextGame);
    });

    it("선택 경기 영역에 마우스를 올리면 자동 전환을 멈춘다", () => {
        vi.useFakeTimers();
        const onSelect = vi.fn();

        render(
            <GamesHero
                authenticated
                bookingBusy={false}
                games={[game, nextGame]}
                onSelect={onSelect}
                onStartBooking={vi.fn()}
                selectedCompleted={false}
                selectedGame={game}
            />,
        );

        fireEvent.mouseEnter(screen.getByRole("group", {name: "선택 경기"}));
        act(() => vi.advanceTimersByTime(5000));

        expect(onSelect).not.toHaveBeenCalled();
    });

    it("하단 경기 표시점에 마우스를 올리면 해당 경기를 선택한다", () => {
        const onSelect = vi.fn();

        render(
            <GamesHero
                authenticated
                bookingBusy={false}
                games={[game, nextGame]}
                onSelect={onSelect}
                onStartBooking={vi.fn()}
                selectedCompleted={false}
                selectedGame={game}
            />,
        );

        fireEvent.mouseEnter(
            screen.getByRole("button", {name: "2번째 경기 보기"}),
        );

        expect(onSelect).toHaveBeenCalledWith(nextGame);
        expect(
            screen.getByRole("button", {name: "1번째 경기 보기"}),
        ).toHaveAttribute("aria-current", "true");
    });
});
