import {apiRequest, unwrap} from "@/api/client";
import type {ApiResponse} from "@/types/api";
import type {StadiumCongestion} from "@/types/congestion";

/**
 * 특정 구장 번호의 실시간 혼잡도 정보를 조회합니다.
 * (잠실야구장 stadiumNum = 1)
 */
export async function getStadiumCongestion(
    stadiumNum: number = 1,
): Promise<StadiumCongestion> {
    const response = await apiRequest<ApiResponse<StadiumCongestion>>(
        `/congestion/stadiums/${stadiumNum}`,
    );

    return unwrap(response);
}
