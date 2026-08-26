import {http, HttpResponse} from "msw";
import {describe, expect, it} from "vitest";

import {API_BASE_URL} from "@/api/client";
import {getGames} from "@/api/games";
import {server} from "@/test/mocks/server";
import type {GameSummary} from "@/types/game";

const games: GameSummary[] = [
    {
        gameId: 2,
        title: "두 번째 경기",
        homeTeam: {teamId: 1, name: "홈팀"},
        awayTeam: {teamId: 2, name: "원정팀"},
        stadium: {stadiumId: 1, name: "테스트 구장"},
        gameAt: "2026-08-09T18:00:00",
        bookingOpenAt: "2026-08-01T10:00:00",
        bookingCloseAt: "2026-08-09T17:00:00",
        bookingStatus: "OPEN",
    },
    {
        gameId: 1,
        title: "첫 번째 경기",
        homeTeam: {teamId: 1, name: "홈팀"},
        awayTeam: {teamId: 3, name: "다른 원정팀"},
        stadium: {stadiumId: 1, name: "테스트 구장"},
        gameAt: "2026-08-08T18:00:00",
        bookingOpenAt: "2026-08-01T10:00:00",
        bookingCloseAt: "2026-08-08T17:00:00",
        bookingStatus: "SCHEDULED",
    },
];

describe("경기 목록 API", () => {
    it("상태별 모든 페이지를 합치고 중복 경기를 제거한 뒤 경기 시각순으로 정렬한다", async () => {
        server.use(
            http.get(`${API_BASE_URL}/games`, ({request}) => {
                const url = new URL(request.url);
                const status = url.searchParams.get("bookingStatus");
                const page = Number(url.searchParams.get("page"));

                // OPEN의 첫 페이지에 다음 페이지가 있다고 알려 pagination 병합 경로를 실행한다.
                const content =
                    status === "OPEN" && page === 0
                        ? [games[0]]
                        : status === "OPEN" && page === 1
                            ? [games[1]]
                            : status === "SCHEDULED"
                                ? [games[1]]
                                : [];
                const totalPages = status === "OPEN" ? 2 : 1;

                return HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "경기 목록 조회 성공",
                    data: {
                        content,
                        pageNumber: page,
                        pageSize: 100,
                        totalElements: content.length,
                        totalPages,
                        isFirst: page === 0,
                        isLast: page === totalPages - 1,
                    },
                });
            }),
        );

        await expect(getGames()).resolves.toEqual([games[1], games[0]]);
    });
});
