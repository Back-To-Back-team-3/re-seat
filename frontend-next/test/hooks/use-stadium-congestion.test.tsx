import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {renderHook, waitFor} from "@testing-library/react";
import {http, HttpResponse} from "msw";
import type {ReactNode} from "react";
import {describe, expect, it} from "vitest";

import {API_BASE_URL} from "@/api/client";
import {useStadiumCongestion} from "@/hooks/use-stadium-congestion";
import {server} from "@/test/mocks/server";

function createWrapper() {
    const queryClient = new QueryClient({
        defaultOptions: {
            queries: {
                retry: false,
            },
        },
    });
    return function Wrapper({children}: {children: ReactNode}) {
        return (
            <QueryClientProvider client={queryClient}>
                {children}
            </QueryClientProvider>
        );
    };
}


describe("useStadiumCongestion 훅", () => {
    it("구장 혼잡도 데이터를 성공적으로 가져온다", async () => {
        server.use(
            http.get(`${API_BASE_URL}/congestion/stadiums/1`, () =>
                HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "조회 성공",
                    data: {
                        stadiumNum: 1,
                        stadiumName: "서울종합운동장 야구장",
                        areaName: "잠실종합운동장",
                        congestionLevel: "보통",
                        congestionMessage: "보통 수준의 혼잡도입니다.",
                        populationMin: 15000,
                        populationMax: 18000,
                        latitude: 37.5121,
                        longitude: 127.0719,
                        observedAt: "2026-09-02 14:00",
                    },
                }),
            ),
        );

        const {result} = renderHook(() => useStadiumCongestion(1), {
            wrapper: createWrapper(),
        });

        await waitFor(() => expect(result.current.isSuccess).toBe(true));
        expect(result.current.data?.stadiumName).toBe("서울종합운동장 야구장");
        expect(result.current.data?.congestionLevel).toBe("보통");
    });
});
