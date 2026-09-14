"use client";

import {useMutation, useQuery, useQueryClient} from "@tanstack/react-query";

import {openGameSeatInventory, searchAdminGames, updateGameBookingStatus} from "@/api/admin";
import {adminKeys} from "@/api/query-keys/admin";
import {gameKeys} from "@/api/query-keys/games";
import type {AdminGameSearchCondition} from "@/types/admin";
import type {GameSummary} from "@/types/game";

type MutableBookingStatus = Exclude<GameSummary["bookingStatus"], "SCHEDULED">;

/** 관리자 경기 목록을 서버 조건·페이지로 조회하고 운영 작업 후 목록을 갱신합니다. */
export function useAdminGames(condition: AdminGameSearchCondition, page: number, size = 10) {
    const queryClient = useQueryClient();
    const games = useQuery({
        queryKey: adminKeys.gameList(condition, page, size),
        queryFn: () => searchAdminGames(condition, page, size),
    });

    const statusMutation = useMutation({
        mutationFn: ({gameId, status, reason}: {gameId: number; status: MutableBookingStatus; reason: string}) =>
            updateGameBookingStatus(gameId, status, reason),
        onSuccess: async () => {
            await Promise.all([
                queryClient.invalidateQueries({queryKey: adminKeys.games()}),
                queryClient.invalidateQueries({queryKey: gameKeys.lists()}),
            ]);
        },
    });
    const inventoryMutation = useMutation({
        mutationFn: openGameSeatInventory,
        onSuccess: async () => {
            await queryClient.invalidateQueries({queryKey: adminKeys.games()});
        },
    });

    return {
        games: games.data?.content ?? [],
        page: games.data ?? null,
        isLoading: games.isLoading,
        error: games.error ?? statusMutation.error ?? inventoryMutation.error,
        updateStatus: (gameId: number, status: MutableBookingStatus, reason: string) =>
            statusMutation.mutateAsync({gameId, status, reason}),
        openInventory: (gameId: number) => inventoryMutation.mutateAsync(gameId),
        isUpdatingStatus: statusMutation.isPending,
        isOpeningInventory: inventoryMutation.isPending,
    };
}
