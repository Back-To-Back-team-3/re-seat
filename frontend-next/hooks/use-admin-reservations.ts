"use client";

import {useQuery} from "@tanstack/react-query";

import {getAdminGameReservations} from "@/api/admin";
import {adminKeys} from "@/api/query-keys/admin";
import type {ReservationStatus} from "@/types/reservation";

export function useAdminReservations(
    gameId: number,
    status: ReservationStatus | undefined,
    page: number,
    size: number,
) {
    return useQuery({
        queryKey: [...adminKeys.gameReservations(gameId), status ?? "ALL", page, size],
        queryFn: () => getAdminGameReservations(gameId, status, page, size),
    });
}
