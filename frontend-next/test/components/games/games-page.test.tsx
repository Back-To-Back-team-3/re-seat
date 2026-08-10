import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {cleanup, fireEvent, render, screen, within,} from "@testing-library/react";
import {http, HttpResponse} from "msw";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";

import {API_BASE_URL} from "@/api/client";
import {GamesPage} from "@/components/games/games-page";
import {server} from "@/test/mocks/server";
import type {GameSummary} from "@/types/game";

vi.mock("next/navigation", () => ({
    useRouter: () => ({push: vi.fn()}),
}));

function gamesResponse(games: GameSummary[]) {
    return {
        success: true,
        errorCode: null,
        message: "경기 목록 조회 성공",
        data: {
            content: games,
            pageNumber: 0,
            pageSize: 100,
            totalElements: games.length,
            totalPages: 1,
            isFirst: true,
            isLast: true,
        },
    };
}

function mockGames(games: GameSummary[]) {
    server.use(
        http.get(`${API_BASE_URL}/games`, () =>
            HttpResponse.json(gamesResponse(games)),
        ),
    );
}

function renderGamesPage() {
    const queryClient = new QueryClient({
        defaultOptions: {
            queries: {retry: false},
            mutations: {retry: false},
        },
    });

    return render(
        <QueryClientProvider client={queryClient}>
            <GamesPage/>
        </QueryClientProvider>,
    );
}

/**
 * 히어로 영역과 일정 영역 모두 같은 선택 경기를 "SELECTED GAME" 문구로 보여줄 수
 * 있어 getByText가 모호해질 수 있다. 히어로에만 있는 eyebrow 문구를 기준으로
 * 히어로 컨테이너를 좁혀서 검증한다.
 */
function heroSection() {
    return screen.getByText("2026 KBO LEAGUE").closest("section")!;
}

const todayOpenGame: GameSummary = {
    gameId: 1,
    title: "오늘 첫 경기",
    homeTeam: {teamId: 1, name: "홈팀 하나"},
    awayTeam: {teamId: 2, name: "원정팀 하나"},
    stadium: {stadiumId: 1, name: "잠실야구장"},
    gameAt: "2026-08-07T18:00:00",
    bookingOpenAt: "2026-08-01T10:00:00",
    bookingCloseAt: "2026-08-07T17:00:00",
    bookingStatus: "OPEN",
};

const todayScheduledGame: GameSummary = {
    ...todayOpenGame,
    gameId: 2,
    title: "오늘 두번째 경기",
    homeTeam: {teamId: 3, name: "홈팀 둘"},
    awayTeam: {teamId: 4, name: "원정팀 둘"},
    gameAt: "2026-08-07T20:00:00",
    bookingStatus: "SCHEDULED",
};

const tomorrowGame: GameSummary = {
    ...todayOpenGame,
    gameId: 3,
    title: "내일 경기",
    homeTeam: {teamId: 5, name: "홈팀 셋"},
    awayTeam: {teamId: 6, name: "원정팀 셋"},
    gameAt: "2026-08-08T18:00:00",
};

describe("홈 화면 히어로", () => {
    beforeEach(() => {
        localStorage.clear();
        // shouldAdvanceTime을 켜서 날짜만 고정하고, MSW 응답을 기다리는 findBy*의
        // 내부 폴링(setTimeout)은 실제 시간처럼 계속 흐르게 한다.
        vi.useFakeTimers({shouldAdvanceTime: true});
        // KST(UTC+9) 기준 2026-08-07 정오. 오늘의 경기 판정이 KST를 기준으로 하는지 검증한다.
        vi.setSystemTime(new Date("2026-08-07T03:00:00Z"));
    });

    afterEach(() => {
        cleanup();
        vi.useRealTimers();
    });

    it("구단·좌석·최대 선택 통계를 표시한다", async () => {
        mockGames([todayOpenGame]);
        renderGamesPage();

        // 통계 행은 경기 목록 로딩과 무관하게 즉시 렌더링된다.
        const hero = within(heroSection());

        expect(hero.getByText("10개")).toBeInTheDocument();
        expect(hero.getByText("구단")).toBeInTheDocument();
        expect(hero.getByText("500")).toBeInTheDocument();
        expect(hero.getByText("데모 좌석/경기")).toBeInTheDocument();
        expect(hero.getByText("2석")).toBeInTheDocument();
        expect(hero.getByText("최대 선택")).toBeInTheDocument();
    });

    it("오늘의 경기 패널에는 KST 기준 오늘 경기만 표시한다", async () => {
        mockGames([todayOpenGame, todayScheduledGame, tomorrowGame]);
        renderGamesPage();

        expect(
            await screen.findByRole("button", {name: /원정팀 하나/}),
        ).toBeInTheDocument();
        expect(
            screen.getByRole("button", {name: /원정팀 둘/}),
        ).toBeInTheDocument();
        expect(
            screen.queryByRole("button", {name: /원정팀 셋/}),
        ).not.toBeInTheDocument();
    });

    it("오늘 예정된 경기가 없으면 안내 문구를 표시한다", async () => {
        mockGames([tomorrowGame]);
        renderGamesPage();

        expect(
            await screen.findByText("오늘 예정된 경기가 없습니다."),
        ).toBeInTheDocument();
    });

    it("오늘의 경기 카드를 클릭하면 히어로의 선택 경기가 바뀐다", async () => {
        mockGames([todayOpenGame, todayScheduledGame]);
        renderGamesPage();

        // chooseInitialGame 규칙상 오늘 경기 중 OPEN 상태가 먼저 선택된다.
        expect(
            await within(heroSection()).findByText(todayOpenGame.title),
        ).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: /원정팀 둘/}));

        expect(
            await within(heroSection()).findByText(todayScheduledGame.title),
        ).toBeInTheDocument();
        expect(
            within(heroSection()).queryByText(todayOpenGame.title),
        ).not.toBeInTheDocument();
    });

    it("로그아웃 상태에서는 예매 버튼에 로그인 후 예매를 표시한다", async () => {
        mockGames([todayOpenGame]);
        renderGamesPage();

        expect(
            await within(heroSection()).findByRole("button", {
                name: /로그인 후 예매/,
            }),
        ).toBeInTheDocument();
    });
});
