import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {http, HttpResponse} from "msw";
import {afterEach, describe, expect, it, vi} from "vitest";

import {API_BASE_URL} from "@/api/client";
import OrderPage from "@/app/(booking)/orders/[orderId]/page";
import {server} from "@/test/mocks/server";
import type {GameSeat} from "@/types/game";
import type {OrderResponse} from "@/types/order";

const routeParams = vi.hoisted(() => ({orderId: "1"}));
const mocks = vi.hoisted(() => ({
    routerPush: vi.fn(),
    routerBack: vi.fn(),
    cancelMutate: vi.fn(),
    refetch: vi.fn(),
    prepareMutate: vi.fn(),
    order: null as OrderResponse | null,
    orderError: null as Error | null,
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
}));

vi.mock("next/navigation", () => ({
    useParams: () => routeParams,
    useRouter: () => ({back: mocks.routerBack, push: mocks.routerPush}),
}));

vi.mock("@/hooks/use-order", () => ({
    useOrder: () => ({
        detail: {
            data: mocks.order,
            error: mocks.orderError,
            isError: Boolean(mocks.orderError),
            isFetching: false,
            refetch: mocks.refetch,
        },
        cancel: {isPending: false, mutate: mocks.cancelMutate},
    }),
}));

vi.mock("@/hooks/use-payment", () => ({
    usePayment: () => ({
        prepare: {isPending: false, mutate: mocks.prepareMutate},
    }),
}));

vi.mock("@/providers/booking-store-provider", () => ({
    useBookingStore: (
        selector: (state: {
            selectedGameId: number | null;
            selectedSeats: GameSeat[];
        }) => unknown,
    ) => selector({selectedGameId: 1, selectedSeats: [mocks.seat]}),
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
                    title: "두산 vs LG",
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

function makeOrder(overrides: Partial<OrderResponse> = {}): OrderResponse {
    return {
        orderId: 1,
        orderNo: "O-1",
        totalAmount: 15000,
        status: "CREATED",
        paymentDeadline: new Date(Date.now() + 60_000).toISOString(),
        holdExpiresAt: new Date(Date.now() + 60_000).toISOString(),
        orderItems: [{orderItemId: 1, gameSeatId: 1, price: 15000}],
        ...overrides,
    };
}

function renderOrderPage() {
    const queryClient = new QueryClient({
        defaultOptions: {
            queries: {retry: false},
            mutations: {retry: false},
        },
    });

    return render(
        <QueryClientProvider client={queryClient}>
            <OrderPage/>
        </QueryClientProvider>,
    );
}

describe("주문 상세 페이지", () => {
    afterEach(() => {
        cleanup();
        routeParams.orderId = "1";
        mocks.order = null;
        mocks.orderError = null;
        mocks.cancelMutate.mockClear();
        mocks.refetch.mockClear();
        mocks.prepareMutate.mockClear();
        mocks.routerPush.mockClear();
        mocks.routerBack.mockClear();
    });

    it("주문 조회가 실패하면 로딩 대신 오류 원인을 표시한다", () => {
        mocks.orderError = new Error("인증 정보가 유효하지 않거나 만료되었습니다.");

        render(
            <QueryClientProvider
                client={
                    new QueryClient({
                        defaultOptions: {queries: {retry: false}},
                    })
                }
            >
                <OrderPage/>
            </QueryClientProvider>,
        );

        expect(
            screen.getByText("인증 정보가 유효하지 않거나 만료되었습니다."),
        ).toBeInTheDocument();
        expect(
            screen.queryByText("주문을 불러오고 있습니다."),
        ).not.toBeInTheDocument();
    });

    it("숫자가 아닌 주문 ID로 진입하면 잘못된 주소임을 안내한다", () => {
        routeParams.orderId = "not-a-number";

        renderOrderPage();

        expect(screen.getByText("올바르지 않은 주문 주소입니다.")).toBeInTheDocument();
        expect(
            screen.queryByText("주문을 불러오고 있습니다."),
        ).not.toBeInTheDocument();
    });

    it("주문이 있으면 결제 남은 시간과 주문 상태 액션을 보여준다", async () => {
        mocks.order = makeOrder();
        mockGameDetail();

        renderOrderPage();

        expect(await screen.findByText("두산", {exact: false})).toBeInTheDocument();
        expect(screen.getByText("결제 남은 시간")).toBeInTheDocument();
        expect(screen.getByText("O-1")).toBeInTheDocument();
        expect(
            screen.getByRole("button", {name: "15,000원 결제 준비 →"}),
        ).toBeEnabled();
    });

    it("주문 취소를 누르면 주문 취소 mutate를 호출한다", async () => {
        mocks.order = makeOrder();
        mockGameDetail();

        renderOrderPage();

        const cancelButton = await screen.findByRole("button", {name: "주문 취소"});
        fireEvent.click(cancelButton);

        expect(mocks.cancelMutate).toHaveBeenCalledWith(1);
    });

    it("주문 상태 확인을 누르면 주문을 다시 조회한다", async () => {
        mocks.order = makeOrder();
        mockGameDetail();

        renderOrderPage();

        const refreshButton = await screen.findByRole("button", {
            name: "주문 상태 확인",
        });
        fireEvent.click(refreshButton);

        expect(mocks.refetch).toHaveBeenCalled();
    });

    it("좌석 선택으로 돌아가면 현재 경기의 좌석 route로 이동한다", async () => {
        mocks.order = makeOrder();
        mockGameDetail();
        renderOrderPage();

        fireEvent.click(
            await screen.findByRole("button", {name: "← 좌석 선택으로"}),
        );

        expect(mocks.routerPush).toHaveBeenCalledWith("/games/1/seats");
        expect(mocks.routerBack).not.toHaveBeenCalled();
    });
});
