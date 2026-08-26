import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {http, HttpResponse} from "msw";
import {afterEach, describe, expect, it, vi} from "vitest";

import {API_BASE_URL} from "@/api/client";
import SeatsPage from "@/app/(booking)/games/[gameId]/seats/page";
import {server} from "@/test/mocks/server";
import type {GameSeat, GameZone} from "@/types/game";
import type {ReservationResponse} from "@/types/reservation";

const routeParams = vi.hoisted(() => ({gameId: "1"}));
const mocks = vi.hoisted(() => ({
    routerPush: vi.fn(),
    createMutate: vi.fn(),
    cancelMutate: vi.fn(),
    reservation: null as ReservationResponse | null,
    createError: null as Error | null,
    cancelSucceeded: false,
    zone: {
        zoneId: 1,
        zoneName: "1루 내야",
        grade: "INFIELD",
        basePrice: 15000,
        totalCount: 100,
        availableCount: 80,
    } as GameZone,
    seat: {
        gameSeatId: 1,
        zoneId: 1,
        zoneName: "1루 내야",
        grade: "INFIELD",
        seatBlock: "A",
        seatRow: "1",
        seatNumber: "1",
        price: 15000,
        status: "AVAILABLE",
    } as GameSeat,
    queueTokenExpiresAt: new Date(
        Date.now() + 5 * 60_000,
    ).toISOString() as string | null,
}));

vi.mock("next/navigation", () => ({
    useParams: () => routeParams,
    useRouter: () => ({push: mocks.routerPush}),
}));

vi.mock("@/hooks/use-reservation", () => ({
    useReservation: () => ({
        selectedSeats: [mocks.seat],
        reservation: mocks.reservation,
        create: {
            isPending: false,
            mutate: mocks.createMutate,
            error: mocks.createError,
        },
        cancel: {
            isPending: false,
            mutate: mocks.cancelMutate,
            error: null,
            isSuccess: mocks.cancelSucceeded,
        },
    }),
}));

vi.mock("@/hooks/use-seats", () => ({
    useSeats: () => ({
        zones: {data: [mocks.zone]},
        seats: {data: [mocks.seat]},
    }),
}));

vi.mock("@/providers/booking-store-provider", () => ({
    useBookingStore: (
        selector: (state: {
            selectedZoneId: number | null;
            setZone: () => void;
            toggleSeat: () => void;
            selectedSeats: GameSeat[];
            queueTokenExpiresAt: string | null;
        }) => unknown,
    ) =>
        selector({
            selectedZoneId: mocks.zone.zoneId,
            setZone: vi.fn(),
            toggleSeat: vi.fn(),
            selectedSeats: [mocks.seat],
            queueTokenExpiresAt: mocks.queueTokenExpiresAt,
        }),
}));

function mockGameDetail() {
    server.use(
        http.get(`${API_BASE_URL}/games/1`, () =>
            HttpResponse.json({
                success: true,
                errorCode: null,
                message: "조회 성공",
                data: {
                    gameId: 1,
                    title: "",
                    homeTeam: {teamId: 1, name: "두산"},
                    awayTeam: {teamId: 2, name: "LG"},
                    stadium: {stadiumId: 1, name: "잠실야구장"},
                    gameAt: "2026-08-10T18:30:00",
                    bookingOpenAt: "2026-08-01T00:00:00",
                    bookingCloseAt: "2026-08-10T18:00:00",
                    bookingStatus: "OPEN",
                },
            }),
        ),
    );
}

function renderSeatsPage() {
    const queryClient = new QueryClient({
        defaultOptions: {
            queries: {retry: false},
            mutations: {retry: false},
        },
    });

    return render(
        <QueryClientProvider client={queryClient}>
            <SeatsPage/>
        </QueryClientProvider>,
    );
}

describe("좌석 선택 화면", () => {
    afterEach(() => {
        cleanup();
        mocks.reservation = null;
        mocks.createError = null;
        mocks.cancelSucceeded = false;
        mocks.queueTokenExpiresAt = new Date(Date.now() + 5 * 60_000).toISOString();
        mocks.createMutate.mockClear();
        mocks.cancelMutate.mockClear();
        mocks.routerPush.mockClear();
    });

    it("예약 생성이 실패하면 원인을 화면에 표시한다", async () => {
        mocks.createError = new Error("입장 토큰이 필요합니다.");
        mockGameDetail();
        renderSeatsPage();

        expect(
            await screen.findByText("입장 토큰이 필요합니다."),
        ).toBeInTheDocument();
    });

    it("선점을 해제하면 입장 토큰이 소비되었음을 안내하고 좌석 선택을 잠근다", async () => {
        // 예약을 만들면 입장 토큰이 소비되므로 취소 후에도 만료 시각이 비어 있다.
        mocks.cancelSucceeded = true;
        mocks.queueTokenExpiresAt = null;
        mockGameDetail();
        renderSeatsPage();

        expect(
            await screen.findByText(
                "좌석 선점을 해제했습니다. 현재 입장 토큰은 사용되어 새 선점은 다시 예매해야 합니다.",
            ),
        ).toBeInTheDocument();
        expect(
            screen.getByRole("button", {name: "1석 선점하기 →"}),
        ).toBeDisabled();
    });

    it("경기 헤더와 좌석 범례, 선점 버튼을 보여준다", async () => {
        mockGameDetail();
        renderSeatsPage();

        expect(await screen.findByText("두산", {exact: false})).toBeInTheDocument();
        expect(screen.getByText("선택 가능")).toBeInTheDocument();
        expect(screen.getByText("선택 좌석")).toBeInTheDocument();
        expect(screen.getByText("선택 불가")).toBeInTheDocument();
        expect(
            screen.getByRole("button", {name: "1석 선점하기 →"}),
        ).toBeInTheDocument();
    });

    it("선점하기를 누르면 예약 생성 mutate를 호출한다", async () => {
        mockGameDetail();
        renderSeatsPage();

        const reserveButton = await screen.findByRole("button", {
            name: "1석 선점하기 →",
        });
        fireEvent.click(reserveButton);

        expect(mocks.createMutate).toHaveBeenCalled();
    });

    it("예약이 있으면 주문 이동과 선점 해제 버튼을 보여준다", async () => {
        mocks.reservation = {
            reservationId: 1,
            reservationNo: "R-1",
            status: "HOLDING",
            gameSeats: [],
            holdExpiresAt: new Date(Date.now() + 60_000).toISOString(),
            gameAt: "2026-08-10T18:30:00",
        };
        mockGameDetail();
        renderSeatsPage();

        const continueButton = await screen.findByRole("button", {
            name: "주문 정보 입력 →",
        });
        fireEvent.click(continueButton);
        expect(mocks.routerPush).toHaveBeenCalledWith("/checkout");

        fireEvent.click(screen.getByRole("button", {name: "선점 해제"}));
        expect(mocks.cancelMutate).toHaveBeenCalled();
    });
});
