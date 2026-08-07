"use client";

import { useQuery } from "@tanstack/react-query";

import { gameKeys } from "@/api/query-keys/games";
import { getGameSeats, getGameZones } from "@/api/seats";

export function useSeats(gameId: number, zoneId: number | null) {
  const zones = useQuery({
    queryKey: gameKeys.zones(gameId),
    queryFn: () => getGameZones(gameId),
    enabled: Number.isFinite(gameId),
  });
  const seats = useQuery({
    queryKey: gameKeys.seats(gameId, zoneId ?? undefined),
    queryFn: () => getGameSeats(gameId, zoneId ?? undefined),
    enabled: Number.isFinite(gameId),
  });
  return { zones, seats };
}
