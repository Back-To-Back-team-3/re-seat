import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";

import { API_BASE_URL } from "@/api/client";
import PaymentPage from "@/app/(booking)/payments/[paymentId]/page";
import { savePendingPayment } from "@/lib/payment-storage";
import { server } from "@/test/mocks/server";
import type { OrderResponse } from "@/types/order";
import type { PaymentResponse } from "@/types/payment";

const routeParams = vi.hoisted(() => ({ paymentId: "not-a-number" }));
const mocks = vi.hoisted(() => ({
  routerPush: vi.fn(),
  routerBack: vi.fn(),
  refetch: vi.fn(),
  payment: null as PaymentResponse | null,
  paymentError: null as Error | null,
  order: null as OrderResponse | null,
}));

vi.mock("next/navigation", () => ({
  useParams: () => routeParams,
  useRouter: () => ({ back: mocks.routerBack, push: mocks.routerPush }),
}));

vi.mock("@/hooks/use-payment", () => ({
  usePayment: () => ({
    detail: {
      data: mocks.payment,
      error: mocks.paymentError,
      isFetching: false,
    },
  }),
}));

vi.mock("@/hooks/use-order", () => ({
  useOrder: () => ({
    detail: {
      data: mocks.order,
      isFetching: false,
      refetch: mocks.refetch,
    },
  }),
}));

vi.mock("@/providers/booking-store-provider", () => ({
  useBookingStore: (
    selector: (state: { selectedGameId: number | null }) => unknown,
  ) => selector({ selectedGameId: 1 }),
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
          homeTeam: { teamId: 1, name: "두산" },
          awayTeam: { teamId: 2, name: "LG" },
          stadium: { stadiumId: 1, name: "잠실야구장" },
          gameAt: "2026-08-10T18:30:00",
          bookingOpenAt: "2026-08-01T00:00:00",
          bookingCloseAt: "2026-08-10T18:00:00",
          bookingStatus: "OPEN",
        },
      }),
    ),
  );
}

function makePayment(overrides: Partial<PaymentResponse> = {}): PaymentResponse {
  return {
    paymentId: 1,
    paymentNo: "P-1",
    orderId: 2,
    amount: 15000,
    method: null,
    status: "READY",
    pgProvider: "TOSS",
    failReason: null,
    approvedAt: null,
    failedAt: null,
    ...overrides,
  };
}

function makeOrder(overrides: Partial<OrderResponse> = {}): OrderResponse {
  return {
    orderId: 2,
    orderNo: "O-2",
    totalAmount: 15000,
    status: "CREATED",
    paymentDeadline: new Date(Date.now() + 60_000).toISOString(),
    holdExpiresAt: new Date(Date.now() + 60_000).toISOString(),
    orderItems: [],
    ...overrides,
  };
}

function renderPaymentPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <PaymentPage />
    </QueryClientProvider>,
  );
}

describe("결제 상세 페이지", () => {
  afterEach(() => {
    cleanup();
    routeParams.paymentId = "not-a-number";
    mocks.payment = null;
    mocks.paymentError = null;
    mocks.order = null;
    mocks.refetch.mockClear();
    mocks.routerPush.mockClear();
    mocks.routerBack.mockClear();
    sessionStorage.clear();
  });

  it("숫자가 아닌 결제 ID로 진입하면 잘못된 주소임을 안내한다", () => {
    render(<PaymentPage />);

    expect(screen.getByText("올바르지 않은 결제 주소입니다.")).toBeInTheDocument();
    expect(
      screen.queryByText("결제 정보를 불러오고 있습니다."),
    ).not.toBeInTheDocument();
  });

  it("READY 결제는 결제 안내와 영수증을 보여준다", async () => {
    routeParams.paymentId = "1";
    mocks.payment = makePayment();
    mocks.order = makeOrder();
    mockGameDetail();

    renderPaymentPage();

    expect(await screen.findByText("O-2")).toBeInTheDocument();
    expect(screen.getByText("P-1")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Toss 결제창 열기 →" }),
    ).toBeEnabled();
  });

  it("결제가 승인되면 완료 화면을 보여준다", async () => {
    routeParams.paymentId = "1";
    mocks.payment = makePayment({ status: "APPROVED" });
    mocks.order = makeOrder({ status: "PAID" });
    mockGameDetail();

    renderPaymentPage();

    expect(await screen.findByText("예매가 완료되었습니다!")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "내 티켓 확인 →" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Toss 결제창 열기 →" }),
    ).not.toBeInTheDocument();
  });

  it("주문으로 돌아가면 pending 결제의 주문 route로 이동한다", async () => {
    routeParams.paymentId = "1";
    mocks.payment = makePayment();
    mocks.order = makeOrder();
    mockGameDetail();
    savePendingPayment({
      orderId: 2,
      gameId: 1,
      payment: {
        paymentId: 1,
        paymentNo: "P-1",
        orderId: 2,
        amount: 15000,
        method: null,
        status: "READY",
        pgProvider: "TOSS",
        pgOrderId: "PG-1",
      },
      idempotencyKey: "test-key",
    });

    renderPaymentPage();
    fireEvent.click(
      await screen.findByRole("button", { name: "← 주문으로 돌아가기" }),
    );

    expect(mocks.routerPush).toHaveBeenCalledWith("/orders/2");
    expect(mocks.routerBack).not.toHaveBeenCalled();
  });
});
