"use client";

import {useQuery} from "@tanstack/react-query";

import {getStadiumCongestion} from "@/api/congestion";
import {congestionKeys} from "@/api/query-keys/congestion";

/**
 * 구장 실시간 혼잡도 데이터를 조회하는 React Query 훅입니다.
 * 백엔드 Redis 캐시 TTL(10분)을 고려해 staleTime을 5분으로 설정합니다.
 */
export function useStadiumCongestion(stadiumNum: number = 1) {
    return useQuery({
        queryKey: congestionKeys.stadium(stadiumNum),
        queryFn: () => getStadiumCongestion(stadiumNum),
        staleTime: 1000 * 60 * 5, // 5분
        refetchOnWindowFocus: false,
    });
}
