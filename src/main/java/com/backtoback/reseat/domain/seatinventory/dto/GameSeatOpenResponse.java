package com.backtoback.reseat.domain.seatinventory.dto;

import java.util.IntSummaryStatistics;
import java.util.List;

import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 경기 좌석 재고 오픈 결과 응답.
 * - <p>{@code priceRange}: 가격 책정이 의도대로 적용됐는지 관리자가 즉시 확인한다.
 */
@Schema(description = "좌석 재고 오픈 응답")
public record GameSeatOpenResponse(

	@Schema(description = "경기 ID", example = "1146")
	Long gameId,

	@Schema(description = "생성된 좌석 재고 수", example = "500")
	int createdCount,

	@Schema(description = "생성된 재고의 가격 범위")
	PriceRange priceRange) {

	/**
	 * 생성된 좌석 재고 목록으로부터 응답을 만든다.
	 *
	 * @param gameId    경기 ID
	 * @param gameSeats 생성된 좌석 재고 (비어 있으면 안 됨)
	 * @throws IllegalArgumentException gameSeats가 비어 있는 경우
	 */
	public static GameSeatOpenResponse from(Long gameId, List<GameSeat> gameSeats) {
		if (gameSeats.isEmpty()) {
			throw new IllegalArgumentException("생성된 좌석 재고가 없어 가격 범위를 계산할 수 없습니다.");
		}

		IntSummaryStatistics stats = gameSeats.stream()
			.mapToInt(GameSeat::getPrice)
			.summaryStatistics();

		return new GameSeatOpenResponse(
			gameId,
			gameSeats.size(),
			new PriceRange(stats.getMin(), stats.getMax()));
	}

	@Schema(description = "가격 범위")
	public record PriceRange(

		@Schema(description = "최저가", example = "16000")
		int min,

		@Schema(description = "최고가", example = "18000")
		int max) {
	}
}
