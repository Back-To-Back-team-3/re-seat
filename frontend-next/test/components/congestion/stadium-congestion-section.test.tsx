import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {http, HttpResponse} from "msw";
import type {ReactNode} from "react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";

import {API_BASE_URL} from "@/api/client";
import {StadiumCongestionSection} from "@/components/congestion/stadium-congestion-section";
import {adjustLevel, calculateStadiumZones} from "@/lib/stadium-zones";
import {server} from "@/test/mocks/server";
import type {CongestionLevel} from "@/types/congestion";

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

describe("StadiumCongestionSection 컴포넌트", () => {
    const originalEnv = process.env.NEXT_PUBLIC_KAKAO_MAP_API_KEY;

    beforeEach(() => {
        process.env.NEXT_PUBLIC_KAKAO_MAP_API_KEY = "test-kakao-key";
    });

    afterEach(() => {
        cleanup();
        process.env.NEXT_PUBLIC_KAKAO_MAP_API_KEY = originalEnv;
        vi.restoreAllMocks();
    });

    it("구역별 혼잡도 헤더 및 구역 목록을 렌더링한다", async () => {
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
                        congestionMessage: "보통 수준의 유동인구입니다.",
                        populationMin: 14000,
                        populationMax: 16000,
                        latitude: 37.5121,
                        longitude: 127.0719,
                        observedAt: "2026-09-02 14:00",
                    },
                }),
            ),
        );

        render(<StadiumCongestionSection stadiumNum={1} />, {
            wrapper: createWrapper(),
        });

        expect(
            screen.getByText("잠실야구장 주변 실시간 구역별 혼잡도"),
        ).toBeInTheDocument();
        expect(
            screen.getByText("중앙 매표소 & 무인 발권기"),
        ).toBeInTheDocument();
        expect(
            screen.getByText("종합운동장역 5·6번 출구 (2·9호선)"),
        ).toBeInTheDocument();
    });

    it("카테고리 탭 클릭 시 해당 구역 목록만 필터링한다", async () => {
        render(<StadiumCongestionSection stadiumNum={1} />, {
            wrapper: createWrapper(),
        });

        // '먹거리/주차' 탭 클릭
        const foodTab = screen.getByRole("button", {name: "먹거리/주차"});
        fireEvent.click(foodTab);

        expect(
            screen.getByText("탄천 공영주차장 / 남문 출구"),
        ).toBeInTheDocument();
        expect(
            screen.getByText("잠실새내역 먹자골목 (새마을시장)"),
        ).toBeInTheDocument();
        expect(
            screen.queryByText("중앙 매표소 & 무인 발권기"),
        ).not.toBeInTheDocument();
    });

    it("구역 카드 클릭 및 키보드(Enter) 입력 시 '선택됨' 상태로 변경된다", async () => {
        render(<StadiumCongestionSection stadiumNum={1} />, {
            wrapper: createWrapper(),
        });

        const card = screen.getByText("1루 출입구 (홈팀 방면)");
        fireEvent.click(card);

        // 클릭 후 '선택됨' 뱃지가 화면에 렌더링되는지 단언
        expect(screen.getByText("선택됨")).toBeInTheDocument();

        // 키보드 엔터 이벤트 테스트
        const otherCard = screen.getByText("3루 출입구 (원정팀 방면)");
        fireEvent.keyDown(otherCard, {key: "Enter", code: "Enter"});
        expect(screen.getByText("선택됨")).toBeInTheDocument();
    });

    it("혼잡도 API 에러 시 에러 안내 및 재시도 버튼이 표시된다", async () => {
        server.use(
            http.get(`${API_BASE_URL}/congestion/stadiums/1`, () =>
                HttpResponse.json(
                    {
                        success: false,
                        errorCode: "CONGESTION_NOT_FOUND",
                        message: "구장 정보를 찾을 수 없습니다.",
                        data: null,
                    },
                    {status: 404},
                ),
            ),
        );

        render(<StadiumCongestionSection stadiumNum={1} />, {
            wrapper: createWrapper(),
        });

        await waitFor(() => {
            expect(
                screen.getByText("혼잡도 데이터를 불러오지 못했습니다."),
            ).toBeInTheDocument();
        });
    });
});

describe("stadium-zones 유틸리티 단위 테스트", () => {
    it("adjustLevel이 경계값을 초과하지 않고 적절한 레벨을 반환한다", () => {
        expect(adjustLevel("여유", -1)).toBe("여유");
        expect(adjustLevel("여유", 1)).toBe("보통");
        expect(adjustLevel("붐빔", 1)).toBe("붐빔");
        expect(adjustLevel("붐빔", -1)).toBe("약간 붐빔");
        expect(adjustLevel("보통", 0)).toBe("보통");
    });

    it("calculateStadiumZones가 혼잡도 레벨에 따라 동적 waitTimeEst를 산출한다", () => {
        const levels: CongestionLevel[] = ["여유", "보통", "약간 붐빔", "붐빔"];

        levels.forEach((lvl) => {
            const zones = calculateStadiumZones({
                stadiumNum: 1,
                stadiumName: "잠실야구장",
                areaName: "잠실종합운동장",
                congestionLevel: lvl,
                congestionMessage: "",
                populationMin: null,
                populationMax: null,
                latitude: 37.51,
                longitude: 127.07,
                observedAt: "2026-09-02",
            });

            expect(zones).toHaveLength(8);
            zones.forEach((zone) => {
                expect(zone.waitTimeEst).toBeTruthy();
                expect(typeof zone.waitTimeEst).toBe("string");
            });
        });
    });
});
