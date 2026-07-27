import { apiRequest, setQueueToken, streamSse, unwrap, withMockFallback } from "./client";
import type {
  ApiResult,
  ApiResponse,
  GameSeat,
  GameSummary,
  GameZone,
  HoldTimeResponse,
  OrderResponse,
  PageResponse,
  PaymentActionResponse,
  PaymentCreateResponse,
  PaymentResponse,
  QueueAdmitEvent,
  QueueCancelResponse,
  QueueStatusResponse,
  ReservationResponse,
  TicketSummary,
  UserProfile
} from "../types";
import { mockTickets } from "../mocks/mockData";

type UserProfilePayload = Omit<UserProfile, "isVerified"> & {
  isVerified?: boolean;
  verified?: boolean;
};

const gameStatuses: GameSummary["bookingStatus"][] = [
  "SCHEDULED",
  "OPEN",
  "CLOSED",
  "CANCELLED"
];

async function getGamePage(bookingStatus: GameSummary["bookingStatus"], page: number) {
  const response = await apiRequest<ApiResponse<PageResponse<GameSummary>>>(
    `/games?bookingStatus=${bookingStatus}&page=${page}&size=100&sort=gameAt,asc`
  );
  return unwrap(response);
}

export async function getGames() {
  const firstPages = await Promise.all(
    gameStatuses.map((bookingStatus) => getGamePage(bookingStatus, 0))
  );
  const remainingPages = await Promise.all(
    firstPages.flatMap((firstPage, statusIndex) =>
      Array.from(
        { length: Math.max(0, firstPage.totalPages - 1) },
        (_, pageIndex) => getGamePage(gameStatuses[statusIndex], pageIndex + 1)
      )
    )
  );
  const games = [...firstPages, ...remainingPages].flatMap((page) => page.content);
  const uniqueGames = Array.from(
    new Map(games.map((game) => [game.gameId, game])).values()
  ).sort((left, right) =>
    left.gameAt.localeCompare(right.gameAt) || left.gameId - right.gameId
  );

  return { data: uniqueGames, source: "api" } satisfies ApiResult<GameSummary[]>;
}

export async function getGame(gameId: number) {
  const response = await apiRequest<ApiResponse<GameSummary>>(`/games/${gameId}`);
  return { data: unwrap(response), source: "api" } satisfies ApiResult<GameSummary>;
}

export async function getMyProfile() {
  const response = await apiRequest<ApiResponse<UserProfilePayload>>("/users/me");
  const { isVerified, verified, ...profile } = unwrap(response);
  return {
    ...profile,
    isVerified: isVerified ?? verified ?? false
  } satisfies UserProfile;
}

export async function enterQueue(gameId: number) {
  const response = await apiRequest<ApiResponse<void>>(`/queues/${gameId}/enter`, {
    method: "POST"
  });
  return response.message;
}

export async function getQueueStatus(gameId: number) {
  const response = await apiRequest<ApiResponse<QueueStatusResponse>>(`/queues/${gameId}/me`);
  return unwrap(response);
}

export async function cancelQueue(gameId: number) {
  const response = await apiRequest<ApiResponse<QueueCancelResponse>>(`/queues/${gameId}/me`, {
    method: "DELETE"
  });
  return unwrap(response);
}

export function streamQueue(
  gameId: number,
  handlers: {
    onRank: (status: QueueStatusResponse) => void;
    onAdmit: (event: QueueAdmitEvent) => void;
  },
  signal: AbortSignal
) {
  return streamSse(`/queues/${gameId}/stream`, (event, data) => {
    if (event === "rank") handlers.onRank(data as QueueStatusResponse);
    if (event === "admit") {
      const admitEvent = data as QueueAdmitEvent;
      setQueueToken(admitEvent.queueToken);
      handlers.onAdmit(admitEvent);
    }
  }, signal);
}

export async function getGameSeats(gameId: number, zoneId?: number) {
  const query = zoneId ? `?zoneId=${zoneId}` : "";
  const response = await apiRequest<ApiResponse<GameSeat[]>>(`/games/${gameId}/seats${query}`);
  const seats = unwrap(response).sort((left, right) =>
    left.seatRow.localeCompare(right.seatRow, "ko", { numeric: true })
      || left.seatNumber.localeCompare(right.seatNumber, "ko", { numeric: true })
  );
  return { data: seats, source: "api" } satisfies ApiResult<GameSeat[]>;
}

export async function getGameZones(gameId: number) {
  const response = await apiRequest<ApiResponse<GameZone[]>>(`/games/${gameId}/zones`);
  return { data: unwrap(response), source: "api" } satisfies ApiResult<GameZone[]>;
}

export async function createReservation(gameId: number, gameSeatIds: number[]) {
  const response = await apiRequest<ApiResponse<ReservationResponse>>("/reservations", {
    method: "POST",
    body: JSON.stringify({ gameId, gameSeatIds })
  });
  return { data: unwrap(response), source: "api" } satisfies ApiResult<ReservationResponse>;
}

export async function cancelReservation(reservationId: number) {
  const response = await apiRequest<ApiResponse<{ reservationId: number; status: ReservationResponse["status"] }>>(
    `/reservations/${reservationId}`,
    { method: "DELETE" }
  );
  return unwrap(response);
}

export async function getReservationHoldTime(reservationId: number) {
  const response = await apiRequest<ApiResponse<HoldTimeResponse>>(
    `/reservations/${reservationId}/hold-time`
  );
  return unwrap(response);
}

export async function createOrder(reservationId: number, deliveryType: "MOBILE" | "PAPER") {
  const response = await apiRequest<ApiResponse<OrderResponse>>("/orders", {
    method: "POST",
    body: JSON.stringify({ reservationId, discountCode: "", deliveryType })
  });
  return { data: unwrap(response), source: "api" } satisfies ApiResult<OrderResponse>;
}

export async function getOrder(orderId: number) {
  const response = await apiRequest<ApiResponse<OrderResponse>>(`/orders/${orderId}`);
  return { data: unwrap(response), source: "api" } satisfies ApiResult<OrderResponse>;
}

export async function cancelOrder(orderId: number) {
  const response = await apiRequest<ApiResponse<{ orderId: number; status: OrderResponse["status"] }>>(
    `/orders/${orderId}/cancel`,
    { method: "POST" }
  );
  return unwrap(response);
}

export async function requestPayment(orderId: number, idempotencyKey: string) {
  const response = await apiRequest<ApiResponse<PaymentCreateResponse>>("/payments", {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify({ orderId })
  });
  return { data: unwrap(response), source: "api" } satisfies ApiResult<PaymentCreateResponse>;
}

export async function completePayment(
  paymentId: number,
  idempotencyKey: string,
  payload: { paymentKey: string; orderId: string; amount: number }
) {
  const response = await apiRequest<ApiResponse<PaymentActionResponse>>(`/payments/${paymentId}/complete`, {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify(payload)
  });
  return unwrap(response);
}

export async function failPayment(
  paymentId: number,
  idempotencyKey: string,
  payload: { code: string; message: string; orderId: string }
) {
  const response = await apiRequest<ApiResponse<PaymentActionResponse>>(`/payments/${paymentId}/fail`, {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify(payload)
  });
  return unwrap(response);
}

export async function getPayment(paymentId: number) {
  const response = await apiRequest<ApiResponse<PaymentResponse>>(`/payments/${paymentId}`);
  return unwrap(response);
}

export async function getTickets() {
  return withMockFallback(async () => {
    const response = await apiRequest<ApiResponse<PageResponse<TicketSummary>>>("/tickets");
    return unwrap(response).content;
  }, mockTickets);
}

export async function verifyIdentity(impUid: string) {
  await apiRequest<ApiResponse<void>>("/users/verification", {
    method: "POST",
    body: JSON.stringify({ impUid })
  });
}
