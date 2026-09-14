"use client";

import {useQuery} from "@tanstack/react-query";

import {
    getAdminQueueAdmissionMetrics,
    getAdminQueueOverview,
} from "@/api/admin";
import {adminKeys} from "@/api/query-keys/admin";
import type {AdmissionMetricPeriod} from "@/types/admin";

export function useAdminQueue(
    gameId: number,
    period: AdmissionMetricPeriod,
    from: string,
    to: string,
) {
    const overview = useQuery({
        queryKey: [...adminKeys.gameQueue(gameId), "overview"],
        queryFn: () => getAdminQueueOverview(gameId),
    });
    const metrics = useQuery({
        queryKey: [...adminKeys.gameQueue(gameId), "metrics", period, from, to],
        queryFn: () => getAdminQueueAdmissionMetrics(gameId, period, from, to),
    });

    return {
        overview: overview.data ?? null,
        metrics: metrics.data ?? null,
        isLoading: overview.isLoading || metrics.isLoading,
        isRefreshing: overview.isFetching || metrics.isFetching,
        error: overview.error ?? metrics.error,
        refresh: async () => {
            await Promise.all([overview.refetch(), metrics.refetch()]);
        },
    };
}
