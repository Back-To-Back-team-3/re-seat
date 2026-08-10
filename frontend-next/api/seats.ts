import {apiRequest, unwrap} from "@/api/client";
import type {ApiResponse} from "@/types/api";
import type {GameSeat, GameZone} from "@/types/game";

export async function getGameZones(gameId: number) {
    const response = await apiRequest<ApiResponse<GameZone[]>>(
        `/games/${gameId}/zones`,
    );
    return unwrap(response);
}

export async function getGameSeats(gameId: number, zoneId?: number) {
    const query = zoneId ? `?zoneId=${zoneId}` : "";
    const response = await apiRequest<ApiResponse<GameSeat[]>>(
        `/games/${gameId}/seats${query}`,
    );
    return unwrap(response).sort(
        (left, right) =>
            left.seatRow.localeCompare(right.seatRow, "ko", {numeric: true}) ||
            left.seatNumber.localeCompare(right.seatNumber, "ko", {
                numeric: true,
            }),
    );
}
