"use client";

import {useQuery} from "@tanstack/react-query";

import {getGames} from "@/api/games";
import {gameKeys} from "@/api/query-keys/games";

/**
 * 서버가 정본인 전체 경기 목록을 TanStack Query 캐시로 조회합니다.
 *
 * 공통 QueryProvider의 보수적인 재조회 정책을 그대로 사용하여 기존 Vite와
 * 비교하는 동안 포커스 이동이나 네트워크 재연결이 목록을 임의로 바꾸지 않게 합니다.
 */
export function useGames() {
    return useQuery({
        queryKey: gameKeys.lists(),
        queryFn: getGames,
    });
}
