package com.backtoback.reseat.domain.seatinventory.dto;

import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.stadium.entity.SeatGrade;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 경기 좌석 현황 응답.
 *
 * <p>프론트 좌석 배치도 렌더링용. gameSeatId 단위로 상태를 표시한다.
 * 500건 리스트로 반환되며, zoneId 필터 적용 시 구역 단위 부분 조회가 가능하다.
 */
@Schema(description = "경기 좌석 현황 응답")
public record SeatStatusResponse(

	@Schema(description = "경기 좌석 재고 ID", example = "1001")
	Long gameSeatId,

	@Schema(description = "구역 ID", example = "1")
	Long zoneId,

	@Schema(description = "구역명", example = "1루 101")
	String zoneName,

	@Schema(description = "좌석 등급", example = "INFIELD")
	SeatGrade grade,

	@Schema(description = "블록(구역 번호)", example = "101")
	String seatBlock,

	@Schema(description = "행", example = "A")
	String seatRow,

	@Schema(description = "열", example = "1")
	String seatNumber,

	@Schema(description = "판매 가격 (성인 정가, 원)", example = "18000")
	int price,

	@Schema(description = "좌석 상태", example = "AVAILABLE")
	GameSeatStatus status) {

	/**
	 * GameSeat 엔티티로부터 응답을 만든다.
	 *
	 * <p>seat·zone이 fetch join으로 로딩되어 있어야 한다.
	 * LAZY 상태에서 호출하면 N+1이 발생한다.
	 */
	public static SeatStatusResponse from(GameSeat gameSeat) {
		var seat = gameSeat.getSeat();
		var zone = seat.getZone();

		return new SeatStatusResponse(
			gameSeat.getId(),
			zone.getId(),
			zone.getName(),
			zone.getGrade(),
			seat.getSeatBlock(),
			seat.getSeatRow(),
			seat.getSeatNumber(),
			gameSeat.getPrice(),
			gameSeat.getStatus());
	}
}
