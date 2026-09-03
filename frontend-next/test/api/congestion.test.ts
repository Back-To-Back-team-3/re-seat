import {http, HttpResponse} from "msw";
import {describe, expect, it} from "vitest";

import {API_BASE_URL} from "@/api/client";
import {getStadiumCongestion} from "@/api/congestion";
import {server} from "@/test/mocks/server";
import type {StadiumCongestion} from "@/types/congestion";

const mockCongestion: StadiumCongestion = {
    stadiumNum: 1,
    stadiumName: "서울종합운동장 야구장",
    areaName: "잠실종합운동장",
    congestionLevel: "여유",
    congestionMessage: "사람이 몰려있지 않아 여유롭습니다.",
    populationMin: 14000,
    populationMax: 16000,
    latitude: 37.5121,
    longitude: 127.0719,
    observedAt: "2026-09-02 14:00",
};

describe("구장 혼잡도 API", () => {
    it("정상적으로 구장 혼잡도 데이터를 반환한다", async () => {
        server.use(
            http.get(`${API_BASE_URL}/congestion/stadiums/1`, () =>
                HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "구장 실시간 혼잡도 조회 성공",
                    data: mockCongestion,
                }),
            ),
        );

        const result = await getStadiumCongestion(1);
        expect(result.stadiumName).toBe("서울종합운동장 야구장");
        expect(result.congestionLevel).toBe("여유");
        expect(result.latitude).toBe(37.5121);
        expect(result.longitude).toBe(127.0719);
    });

    it("서버 오류 시 에러를 throw한다", async () => {
        server.use(
            http.get(`${API_BASE_URL}/congestion/stadiums/999`, () =>
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

        await expect(getStadiumCongestion(999)).rejects.toThrow();
    });
});
