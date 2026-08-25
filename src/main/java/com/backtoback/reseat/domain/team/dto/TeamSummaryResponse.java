package com.backtoback.reseat.domain.team.dto;

import com.backtoback.reseat.domain.team.entity.Team;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 경기 응답에 포함되는 구단 요약 정보.
 * <p>프론트가 팀 상세·로고로 이동할 수 있도록 name과 함께 teamId를 노출한다.
 */
@Schema(description = "구단 요약 정보")
public record TeamSummaryResponse(
    @Schema(
        description = "구단 식별자",
        example = "1"
    ) Long teamId,
    @Schema(
        description = "구단명",
        example = "두산 베어스"
    ) String name
) {
    public static TeamSummaryResponse from(Team team) {
        return new TeamSummaryResponse(team.getId(), team.getName());
    }
}
