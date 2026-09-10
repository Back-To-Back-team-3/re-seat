import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {
    cleanup,
    fireEvent,
    render,
    screen,
    waitFor,
    within,
} from "@testing-library/react";
import {http, HttpResponse} from "msw";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";

import {API_BASE_URL} from "@/api/client";
import {GamesPage} from "@/components/games/games-page";
import {server} from "@/test/mocks/server";
import type {GameSummary} from "@/types/game";
import type {TicketSummary} from "@/types/ticket";

const mocks = vi.hoisted(() => ({
    routerPush: vi.fn(),
    auth: {
        isAuthed: false,
        isVerified: false,
        busy: false,
        message: null,
        messageVariant: "success" as const,
        login: vi.fn(),
        logout: vi.fn(),
        verify: vi.fn(),
        dismissMessage: vi.fn(),
    },
    tickets: [] as TicketSummary[],
}));

vi.mock("next/navigation", () => ({
    useRouter: () => ({push: mocks.routerPush}),
}));

vi.mock("@/hooks/use-auth", () => ({
    useAuth: () => mocks.auth,
}));

vi.mock("@/hooks/use-tickets", () => ({
    useTickets: () => ({
        data: mocks.tickets,
        error: null,
        isLoading: false,
    }),
}));

vi.mock("@/components/congestion/stadium-congestion-section", () => ({
    StadiumCongestionSection: () => <div>혼잡도</div>,
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

function mockGamesNetworkError() {
    server.use(
        http.get(`${API_BASE_URL}/games`, () => HttpResponse.error()),
    );
}

function renderGamesPage(view: "home" | "booking" = "home") {
    const queryClient = new QueryClient({
        defaultOptions: {
            queries: {retry: false},
            mutations: {retry: false},
        },
    });

    return render(
        <QueryClientProvider client={queryClient}>
            <GamesPage view={view}/>
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
        mocks.auth.isAuthed = false;
        mocks.auth.isVerified = false;
        mocks.tickets = [];
        vi.clearAllMocks();
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

    it("히어로에서 데모 통계를 표시하지 않는다", async () => {
        mockGames([todayOpenGame]);
        renderGamesPage();

        const hero = within(heroSection());

        expect(hero.queryByText("10개")).not.toBeInTheDocument();
        expect(hero.queryByText("데모 좌석/경기")).not.toBeInTheDocument();
        expect(hero.queryByText("최대 선택")).not.toBeInTheDocument();
    });

    it("홈에서는 전체 경기 일정을 표시하지 않는다", async () => {
        mockGames([todayOpenGame]);
        renderGamesPage();

        await screen.findByText(todayOpenGame.title);

        expect(
            screen.queryByRole("heading", {name: "경기 일정"}),
        ).not.toBeInTheDocument();
    });

    it("오늘의 경기 패널에는 KST 기준 오늘 경기만 표시한다", async () => {
        mockGames([todayOpenGame, todayScheduledGame, tomorrowGame]);
        renderGamesPage();

        const todayGamesSection = (await screen.findByText(/오늘의 경기/)).closest(
            "section",
        );
        const todayGamesList = screen.getByRole("list", {
            name: "오늘 경기 목록",
        });

        expect(heroSection()).not.toContainElement(todayGamesSection);
        expect(todayGamesList).toHaveTextContent("원정팀 하나");
        expect(todayGamesList).toHaveTextContent("원정팀 둘");
        expect(todayGamesList).not.toHaveTextContent("원정팀 셋");
    });

    it("오늘 예정된 경기가 없으면 안내 문구를 표시한다", async () => {
        mockGames([tomorrowGame]);
        renderGamesPage();

        expect(
            await screen.findByText("오늘 예정된 경기가 없습니다."),
        ).toBeInTheDocument();
    });

    it("오늘의 경기에서 히어로와 별개로 바로 예매를 시작한다", async () => {
        mocks.auth.isAuthed = true;
        mocks.auth.isVerified = true;
        mockGames([todayOpenGame]);
        renderGamesPage();

        const todayGamesSection = (await screen.findByText(/오늘의 경기/)).closest(
            "section",
        )!;

        fireEvent.click(
            within(todayGamesSection).getByRole("button", {
                name: "예매하기",
            }),
        );

        expect(mocks.routerPush).toHaveBeenCalledWith("/games/1/queue");
    });

    it("로그아웃 상태에서도 예매 가능한 경기는 예매하기로 표시한다", async () => {
        mockGames([todayOpenGame]);
        renderGamesPage();

        expect(
            await within(heroSection()).findByRole("button", {
                name: /예매하기/,
            }),
        ).toBeInTheDocument();
    });

    it("브라우저에 과거 완료 기록이 남아도 서버 티켓이 없으면 예매를 허용한다", async () => {
        localStorage.setItem("completedGameIds", JSON.stringify([1]));
        mocks.auth.isAuthed = true;
        mocks.auth.isVerified = true;
        mockGames([todayOpenGame]);

        renderGamesPage();

        const bookingButton = await within(heroSection()).findByRole("button", {
            name: /예매하기/,
        });
        expect(bookingButton).toBeEnabled();
    });

    it("서버에 환불 완료되지 않은 티켓이 있으면 해당 경기를 예매 완료로 표시한다", async () => {
        mocks.auth.isAuthed = true;
        mocks.auth.isVerified = true;
        mocks.tickets = [
            {
                ticketId: 10,
                ticketNo: "TKT-10",
                gameId: 1,
                seat: "1루 101-A-1",
                status: "ISSUED",
                qrToken: "qr-10",
                gameAt: todayOpenGame.gameAt,
            },
        ];
        mockGames([todayOpenGame]);

        renderGamesPage();

        expect(
            await within(heroSection()).findByRole("button", {
                name: "예매 완료",
            }),
        ).toBeDisabled();
    });

    it("경기 조회 실패 알림을 닫으면 화면에서 제거한다", async () => {
        mockGamesNetworkError();
        renderGamesPage();

        const alert = await screen.findByRole("status");
        expect(within(alert).getByText("Failed to fetch")).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "알림 닫기"}));

        await waitFor(() => {
            expect(screen.queryByRole("status")).not.toBeInTheDocument();
        });
    });
});

describe("예매 화면 경기 일정", () => {
    beforeEach(() => {
        localStorage.clear();
        mocks.auth.isAuthed = true;
        mocks.auth.isVerified = true;
        mocks.tickets = [];
        vi.clearAllMocks();
        vi.useFakeTimers({shouldAdvanceTime: true});
        vi.setSystemTime(new Date("2026-08-07T03:00:00Z"));
    });

    afterEach(() => {
        cleanup();
        vi.useRealTimers();
    });

    it("전체 경기 일정만 표시한다", async () => {
        mockGames([todayOpenGame]);
        renderGamesPage("booking");

        expect(
            await screen.findByRole("heading", {name: "경기 일정"}),
        ).toBeInTheDocument();
        expect(screen.queryByText("2026 KBO LEAGUE")).not.toBeInTheDocument();
        expect(screen.queryByText(/오늘의 경기/)).not.toBeInTheDocument();
        expect(screen.queryByText("혼잡도")).not.toBeInTheDocument();
    });

    it("경기 카드에서 예매를 시작하면 해당 경기 대기열로 이동한다", async () => {
        mockGames([todayOpenGame]);
        renderGamesPage("booking");

        fireEvent.click(
            await screen.findByRole("button", {name: "예매하기"}),
        );

        expect(mocks.routerPush).toHaveBeenCalledWith("/games/1/queue");
    });

    it("로그아웃 상태에서 예매를 시작하면 로그인을 요청한다", async () => {
        mocks.auth.isAuthed = false;
        mockGames([todayOpenGame]);
        renderGamesPage("booking");

        fireEvent.click(
            await screen.findByRole("button", {name: "예매하기"}),
        );

        expect(mocks.auth.login).toHaveBeenCalledTimes(1);
        expect(mocks.routerPush).not.toHaveBeenCalled();
    });
});
