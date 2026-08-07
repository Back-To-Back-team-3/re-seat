import { apiRequest, unwrap } from "@/api/client";
import { streamSse } from "@/api/sse";
import type { ApiResponse } from "@/types/api";
import type {
  QueueAdmitEvent,
  QueueCancelResponse,
  QueueStatusResponse,
} from "@/types/game";

export async function enterQueue(gameId: number, signal?: AbortSignal) {
  await apiRequest<ApiResponse<void>>(`/queues/${gameId}/enter`, {
    method: "POST",
    signal,
  });
}

export async function getQueueStatus(gameId: number) {
  const response = await apiRequest<ApiResponse<QueueStatusResponse>>(
    `/queues/${gameId}/me`,
  );
  return unwrap(response);
}

export async function cancelQueue(gameId: number) {
  const response = await apiRequest<ApiResponse<QueueCancelResponse>>(
    `/queues/${gameId}/me`,
    { method: "DELETE" },
  );
  return unwrap(response);
}

export function streamQueue(
  gameId: number,
  handlers: {
    onRank: (status: QueueStatusResponse) => void;
    onAdmit: (event: QueueAdmitEvent) => void;
  },
  signal: AbortSignal,
) {
  return streamSse(
    `/queues/${gameId}/stream`,
    (event, data) => {
      if (event === "rank") {
        handlers.onRank(data as QueueStatusResponse);
      }
      if (event === "admit") {
        handlers.onAdmit(data as QueueAdmitEvent);
      }
    },
    signal,
  );
}
