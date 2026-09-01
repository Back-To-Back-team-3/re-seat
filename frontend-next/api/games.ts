import {apiRequest, unwrap} from "@/api/client";
import type {ApiResponse, PageResponse} from "@/types/api";
import type {GameDetail, GameSummary} from "@/types/game";

const GAME_STATUSES: GameSummary["bookingStatus"][] = [
    "SCHEDULED",
    "OPEN",
    "CLOSED",
    "CANCELLED",
];

async function getGamePage(
    bookingStatus: GameSummary["bookingStatus"],
    page: number,
) {
    const params = new URLSearchParams({
        bookingStatus,
        page: String(page),
        size: "100",
        sort: "gameAt,asc",
    });
    const response = await apiRequest<ApiResponse<PageResponse<GameSummary>>>(
        `/games?${params}`,
    );

    return unwrap(response);
}

/**
 * 네 가지 예매 상태의 경기 페이지를 모두 조회해 하나의 시간순 목록으로 합칩니다.
 *
 * 백엔드 목록 API는 예매 상태를 하나씩 받아 페이지 단위로 반환합니다. 먼저 각
 * 상태의 첫 페이지를 병렬 조회하고, 응답의 totalPages를 기준으로 남은 페이지를
 * 조회합니다. 상태가 변경되는 순간 같은 경기가 두 응답에 포함될 수 있으므로
 * gameId로 중복을 제거한 뒤 기존 Vite와 같은 gameAt, gameId 순서로 정렬합니다.
 */
export async function getGames() {
    const firstPages = await Promise.all(
        GAME_STATUSES.map((status) => getGamePage(status, 0)),
    );
    const remainingPages = await Promise.all(
        firstPages.flatMap((firstPage, statusIndex) =>
            Array.from(
                {length: Math.max(0, firstPage.totalPages - 1)},
                (_, pageIndex) =>
                    getGamePage(GAME_STATUSES[statusIndex], pageIndex + 1),
            ),
        ),
    );
    const games = [...firstPages, ...remainingPages].flatMap(
        (page) => page.content,
    );

    return Array.from(
        new Map(games.map((game) => [game.gameId, game])).values(),
    ).sort(
        (left, right) =>
            left.gameAt.localeCompare(right.gameAt) ||
            left.gameId - right.gameId,
    );
}

export async function getGame(gameId: number) {
    const response = await apiRequest<ApiResponse<GameDetail>>(
        `/games/${gameId}`,
    );

    return unwrap(response);
}
