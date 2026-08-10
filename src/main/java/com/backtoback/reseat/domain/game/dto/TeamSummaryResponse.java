package com.backtoback.reseat.domain.game.dto;

import com.backtoback.reseat.domain.team.entity.Team;

/**
 * 경기 응답에 포함되는 구단 요약 정보.
 *
 * <p>프론트가 팀 상세·로고로 이동할 수 있도록 name과 함께 teamId를 노출한다.</p>
 */
public record TeamSummaryResponse(Long teamId, String name) {

	public static TeamSummaryResponse from(Team team) {
		return new TeamSummaryResponse(team.getId(), team.getName());
	}
}
