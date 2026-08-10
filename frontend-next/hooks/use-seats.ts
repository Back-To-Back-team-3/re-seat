"use client";

import {useQuery} from "@tanstack/react-query";

import {gameKeys} from "@/api/query-keys/games";
import {getGameSeats, getGameZones} from "@/api/seats";

/**
 * 구역 목록과 현재 구역의 좌석 현황을 함께 제공합니다.
 *
 * 기존 화면은 좌석 단계에 들어오면 첫 구역을 자동으로 선택해 그 구역의 좌석을
 * 보여줍니다. 사용자가 아직 아무 구역도 고르지 않았다면 여기서 첫 구역으로
 * 대신 채워 같은 진입 경험을 유지하고, 실제 선택은 화면이 스토어에 기록합니다.
 *
 * @param zoneId 사용자가 명시적으로 고른 구역. 아직 없으면 null
 * @returns 구역 목록과 좌석 목록, 그리고 실제로 표시 중인 구역 ID
 */
export function useSeats(gameId: number, zoneId: number | null) {
    const zones = useQuery({
        queryKey: gameKeys.zones(gameId),
        queryFn: () => getGameZones(gameId),
        enabled: Number.isFinite(gameId),
    });

    const activeZoneId = zoneId ?? zones.data?.[0]?.zoneId ?? null;

    const seats = useQuery({
        queryKey: gameKeys.seats(gameId, activeZoneId ?? undefined),
        queryFn: () => getGameSeats(gameId, activeZoneId as number),
        // 구역을 지정하지 않고 조회하면 백엔드가 전 구역 좌석을 한 번에 돌려주고,
        // 화면은 서로 다른 구역의 같은 열 번호를 한 줄에 섞어 보여준다.
        enabled: Number.isFinite(gameId) && activeZoneId != null,
    });

    return {zones, seats, activeZoneId};
}
