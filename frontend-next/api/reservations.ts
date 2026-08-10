import { apiRequest, unwrap } from "@/api/client";
import type { ApiResponse } from "@/types/api";
import type {
  HoldTimeResponse,
  ReservationResponse,
} from "@/types/reservation";

export async function createReservation(
  gameId: number,
  gameSeatIds: number[],
) {
  const response = await apiRequest<ApiResponse<ReservationResponse>>(
    "/reservations",
    {
      method: "POST",
      body: JSON.stringify({ gameId, gameSeatIds }),
    },
  );
  return unwrap(response);
}

export async function cancelReservation(reservationId: number) {
  const response = await apiRequest<
    ApiResponse<{ reservationId: number; status: ReservationResponse["status"] }>
  >(`/reservations/${reservationId}`, { method: "DELETE" });
  return unwrap(response);
}

export async function getReservationHoldTime(reservationId: number) {
  const response = await apiRequest<ApiResponse<HoldTimeResponse>>(
    `/reservations/${reservationId}/hold-time`,
  );
  return unwrap(response);
}
