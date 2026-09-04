import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {cleanup, render, screen, waitFor} from "@testing-library/react";
import {http, HttpResponse} from "msw";
import type {ReactNode} from "react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";

import {API_BASE_URL} from "@/api/client";
import {StadiumCongestionMap} from "@/components/congestion/stadium-congestion-map";
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

describe("StadiumCongestionMap 컴포넌트", () => {
    const originalEnv = process.env.NEXT_PUBLIC_KAKAO_MAP_API_KEY;

    beforeEach(() => {
        process.env.NEXT_PUBLIC_KAKAO_MAP_API_KEY = "test-kakao-key";
    });

    afterEach(() => {
        cleanup();
        process.env.NEXT_PUBLIC_KAKAO_MAP_API_KEY = originalEnv;
        vi.restoreAllMocks();
    });

    it("카카오 API 키가 설정되지 않은 경우 정적 이미지 폴백을 렌더링한다", () => {
        process.env.NEXT_PUBLIC_KAKAO_MAP_API_KEY = "";

        render(<StadiumCongestionMap stadiumNum={1} />, {
            wrapper: createWrapper(),
        });

        expect(screen.getByAltText("잠실야구장 경기 전경")).toBeInTheDocument();
        expect(screen.getByText("실시간 지도 준비 중")).toBeInTheDocument();
    });

    it("혼잡도 API 조회 중일 때 로딩 안내 문구를 표시한다", () => {
        server.use(
            http.get(`${API_BASE_URL}/congestion/stadiums/1`, async () => {
                await new Promise((resolve) => setTimeout(resolve, 200));
                return HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "조회 성공",
                    data: {
                        stadiumNum: 1,
                        stadiumName: "서울종합운동장 야구장",
                        areaName: "잠실종합운동장",
                        congestionLevel: "보통",
                        congestionMessage: "보통 수준입니다.",
                        populationMin: 15000,
                        populationMax: 18000,
                        latitude: 37.5121,
                        longitude: 127.0719,
                        observedAt: "2026-09-02 14:00",
                    },
                });
            }),
        );

        render(<StadiumCongestionMap stadiumNum={1} />, {
            wrapper: createWrapper(),
        });

        expect(
            screen.getByText("혼잡도 정보 로딩 중..."),
        ).toBeInTheDocument();
    });

    it("혼잡도 API 호출 실패 시 에러 안내 메시지와 재시도 버튼을 표시한다", async () => {
        server.use(
            http.get(`${API_BASE_URL}/congestion/stadiums/1`, () =>
                HttpResponse.json(
                    {
                        success: false,
                        errorCode: "CONGESTION_NOT_FOUND",
                        message: "구장 혼잡도 정보를 찾을 수 없습니다.",
                        data: null,
                    },
                    {status: 404},
                ),
            ),
        );

        render(<StadiumCongestionMap stadiumNum={1} />, {
            wrapper: createWrapper(),
        });

        await waitFor(() => {
            expect(screen.getByText("혼잡도 조회 실패")).toBeInTheDocument();
            expect(screen.getByRole("button", {name: "재시도"})).toBeInTheDocument();
        });
    });

    it("혼잡도 데이터가 정상 로드되면 실시간 혼잡도 뱃지가 표시된다", async () => {
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
                        congestionLevel: "여유",
                        congestionMessage: "여유롭습니다.",
                        populationMin: 10000,
                        populationMax: 12000,
                        latitude: 37.5121,
                        longitude: 127.0719,
                        observedAt: "2026-09-02 14:00",
                    },
                }),
            ),
        );

        render(<StadiumCongestionMap stadiumNum={1} />, {
            wrapper: createWrapper(),
        });

        await waitFor(() => {
            expect(screen.getByText("실시간 혼잡도")).toBeInTheDocument();
            expect(screen.getByText("여유")).toBeInTheDocument();
        });
    });
});
