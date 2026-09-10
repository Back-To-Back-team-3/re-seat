"use client";

import {useMutation, useQueryClient} from "@tanstack/react-query";

import {openGameSeatInventory, updateGameBookingStatus} from "@/api/admin";
import {gameKeys} from "@/api/query-keys/games";
import {useGames} from "@/hooks/use-games";
import type {GameSummary} from "@/types/game";

type MutableBookingStatus = Exclude<GameSummary["bookingStatus"], "SCHEDULED">;

/**
 * 공개 경기 목록을 관리자 작업 대상으로 사용하고 상태 변경 API를 연결합니다.
 * 현재 백엔드에는 별도의 관리자 경기 목록 API가 없어 공개 목록을 정본으로 사용합니다.
 */
export function useAdminGames() {
    const queryClient = useQueryClient();
    const games = useGames();

    const statusMutation = useMutation({
        mutationFn: ({gameId, status, reason}: {gameId: number; status: MutableBookingStatus; reason: string}) =>
            updateGameBookingStatus(gameId, status, reason),
        onSuccess: async () => {
            await queryClient.invalidateQueries({queryKey: gameKeys.lists()});
        },
    });
    const inventoryMutation = useMutation({
        mutationFn: openGameSeatInventory,
        onSuccess: async () => {
            await queryClient.invalidateQueries({queryKey: gameKeys.lists()});
        },
    });

    return {
        games: games.data ?? [],
        isLoading: games.isLoading,
        error: games.error ?? statusMutation.error ?? inventoryMutation.error,
        updateStatus: (gameId: number, status: MutableBookingStatus, reason: string) =>
            statusMutation.mutateAsync({gameId, status, reason}),
        openInventory: (gameId: number) => inventoryMutation.mutateAsync(gameId),
        isUpdatingStatus: statusMutation.isPending,
        isOpeningInventory: inventoryMutation.isPending,
    };
}
