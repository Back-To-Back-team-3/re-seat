import type { GameSummary } from "@/types/game";

export type GameListFilters = {
  bookingStatus: GameSummary["bookingStatus"];
  page: number;
};

export const gameKeys = {
  all: ["games"] as const,
  lists: () => [...gameKeys.all, "list"] as const,
  list: (filters: GameListFilters) =>
    [...gameKeys.lists(), filters] as const,
  detail: (gameId: number) =>
    [...gameKeys.all, "detail", gameId] as const,
  zones: (gameId: number) =>
    [...gameKeys.detail(gameId), "zones"] as const,
  seats: (gameId: number, zoneId?: number) =>
    [...gameKeys.detail(gameId), "seats", zoneId] as const,
};
