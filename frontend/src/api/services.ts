import { apiRequest, setQueueToken, setTokens, streamSse, unwrap, withMockFallback } from "./client";
import {
  ApiResult,
  ApiResponse,
  GameSeat,
  GameZone,
  GameSummary,
  OrderResponse,
  PageResponse,
  PaymentCreateResponse,
  QueueEnterResponse,
  QueueAdmitEvent,
  QueueStatusResponse,
  HoldTimeResponse,
  ReservationResponse,
  TicketSummary,
  TokenResponse
} from "../types";
import {
  mockTickets
} from "../mocks/mockData";

export async function getGames() {
  const response = await apiRequest<ApiResponse<PageResponse<GameSummary>>>("/games?size=20");
  return { data: unwrap(response).content, source: "api" } satisfies ApiResult<GameSummary[]>;
}

export async function getGame(gameId: number) {
  const response = await apiRequest<ApiResponse<GameSummary>>(`/games/${gameId}`);
  return { data: unwrap(response), source: "api" } satisfies ApiResult<GameSummary>;
}

export async function enterQueue(gameId: number) {
  const response = await apiRequest<ApiResponse<QueueEnterResponse>>(`/queues/${gameId}/enter`, {
    method: "POST"
  });
  const result = { data: unwrap(response), source: "api" } satisfies ApiResult<QueueEnterResponse>;

  if (result.data.queueToken) {
    setQueueToken(result.data.queueToken);
  }
  return result;
}

export async function getQueueStatus(gameId: number) {
  const response = await apiRequest<ApiResponse<QueueStatusResponse>>(`/queues/${gameId}/me`);
  return {
    data: { ...unwrap(response), gameId, queueToken: null, tokenExpiresAt: null },
    source: "api"
  } satisfies ApiResult<QueueEnterResponse>;
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

export async function admitQueue(gameId: number, limit = 20) {
  await apiRequest<ApiResponse<number>>(`/queues/${gameId}/admit?limit=${limit}`, {
    method: "POST"
  });

  const response = await apiRequest<ApiResponse<QueueStatusResponse>>(`/queues/${gameId}/me`);

  return {
    data: { ...unwrap(response), gameId, queueToken: null, tokenExpiresAt: null },
    source: "api"
  } satisfies ApiResult<QueueEnterResponse>;
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
  return { data: unwrap(response), source: "api" } satisfies ApiResult<{
    orderId: number;
    status: OrderResponse["status"];
  }>;
}

export async function requestPayment(
  orderId: number,
  method: PaymentCreateResponse["method"],
  idempotencyKey: string
) {
  const response = await apiRequest<ApiResponse<PaymentCreateResponse>>("/payments", {
    method: "POST",
    headers: {
      "Idempotency-Key": idempotencyKey
    },
    body: JSON.stringify({ orderId, method })
  });
  return { data: unwrap(response), source: "api" } satisfies ApiResult<PaymentCreateResponse>;
}

export async function completePayment(paymentId: number, payload: { paymentKey: string; orderId: string; amount: number }) {
  const response = await apiRequest<ApiResponse<{ paymentId: number; status: string }>>(`/payments/${paymentId}/complete`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
  return unwrap(response);
}

export async function failPayment(paymentId: number, payload: { code: string; message: string; orderId: string }) {
  const response = await apiRequest<ApiResponse<{ paymentId: number; status: string }>>(`/payments/${paymentId}/fail`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
  return unwrap(response);
}

export async function getTickets() {
  return withMockFallback(async () => {
    const response = await apiRequest<ApiResponse<{ tickets: TicketSummary[] }>>("/tickets");
    return unwrap(response).tickets;
  }, mockTickets);
}

export async function verifyIdentity(impUid: string) {
  const response = await apiRequest<ApiResponse<void>>("/users/verification", {
    method: "POST",
    body: JSON.stringify({ impUid })
  });
  return unwrap(response);
}
