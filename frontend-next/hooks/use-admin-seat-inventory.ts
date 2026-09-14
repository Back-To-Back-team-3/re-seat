"use client";

import {useMutation, useQuery, useQueryClient} from "@tanstack/react-query";

import {
    getAdminGameSeats,
    getAdminGameSeatSummary,
    updateAdminGameSeatStatus,
} from "@/api/admin";
import {adminKeys} from "@/api/query-keys/admin";
import {gameKeys} from "@/api/query-keys/games";
import type {GameSeatStatus} from "@/types/game";

export function useAdminSeatInventory(
    gameId: number,
    status?: GameSeatStatus,
) {
    const queryClient = useQueryClient();
    const seats = useQuery({
        queryKey: [...adminKeys.gameSeats(gameId), status ?? "ALL"],
        queryFn: () => getAdminGameSeats(gameId, status),
    });
    const summary = useQuery({
        queryKey: adminKeys.gameSeatSummary(gameId),
        queryFn: () => getAdminGameSeatSummary(gameId),
    });
    const statusMutation = useMutation({
        mutationFn: ({
            gameSeatId,
            action,
            reason,
        }: {
            gameSeatId: number;
            action: "block" | "unblock";
            reason: string;
        }) => updateAdminGameSeatStatus(gameSeatId, action, reason),
        onSuccess: async () => {
            await Promise.all([
                queryClient.invalidateQueries({
                    queryKey: adminKeys.gameSeats(gameId),
                }),
                queryClient.invalidateQueries({
                    queryKey: adminKeys.gameSeatSummary(gameId),
                }),
                queryClient.invalidateQueries({queryKey: gameKeys.seats(gameId)}),
            ]);
        },
    });

    return {
        seats: seats.data ?? [],
        summary: summary.data ?? null,
        isLoading: seats.isLoading || summary.isLoading,
        error: seats.error ?? summary.error ?? statusMutation.error,
        updateStatus: (
            gameSeatId: number,
            action: "block" | "unblock",
            reason: string,
        ) => statusMutation.mutateAsync({gameSeatId, action, reason}),
        isUpdating: statusMutation.isPending,
    };
}
