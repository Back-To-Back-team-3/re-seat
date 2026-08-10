"use client";

import {environmentManager, QueryClient, QueryClientProvider,} from "@tanstack/react-query";
import type {ReactNode} from "react";

function createQueryClient() {
    return new QueryClient({
        defaultOptions: {
            queries: {
                // 1차 마이그레이션에서는 기존 Vite 앱과 API 호출 시점을 비교할 수 있도록
                // 데이터를 즉시 stale 상태로 두고 자동 재요청 기능을 사용하지 않는다.
                staleTime: 0,
                retry: false,
                refetchOnWindowFocus: false,
                refetchOnReconnect: false,
            },
            mutations: {
                retry: false,
            },
        },
    });
}

let browserQueryClient: QueryClient | undefined;

function getQueryClient() {
    if (environmentManager.isServer()) {
        // 서버에서는 서로 다른 요청의 캐시가 섞이지 않도록 렌더링마다 새 client를 만든다.
        return createQueryClient();
    }

    // 브라우저에서는 렌더링이나 Suspense가 발생해도 기존 캐시를 잃지 않도록
    // 최초 한 번 만든 client를 이후 렌더링에서도 계속 사용한다.
    if (!browserQueryClient) {
        browserQueryClient = createQueryClient();
    }

    return browserQueryClient;
}

/**
 * 앱 전체에서 TanStack Query 캐시와 조회 상태를 사용할 수 있게 한다.
 */
export function QueryProvider({children}: { children: ReactNode }) {
    const queryClient = getQueryClient();

    return (
        <QueryClientProvider client={queryClient}>
            {children}
        </QueryClientProvider>
    );
}
