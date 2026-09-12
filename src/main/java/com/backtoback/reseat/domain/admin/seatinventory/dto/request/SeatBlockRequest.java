package com.backtoback.reseat.domain.admin.seatinventory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 좌석 판매 차단·해제 요청 DTO.
 * <p>
 * reason은 서버에 별도 테이블로 저장하지 않는다.
 * 값이 있는지만 검증하고 {@link com.backtoback.reseat.domain.admin.seatinventory.service.AdminGameSeatStatusService}에서 로그로만 남긴다.
 * reason 길이·제어 문자 검증 추가: 로그 행 위조(CR/LF 삽입)를 막기 위해 길이·개행 문자를 제한한다.
 */
@Schema(description = "좌석 판매 차단·해제 요청")
public record SeatBlockRequest(

    @NotBlank(message = "차단·해제 사유는 필수입니다.")
    @Size(
        max = 255,
        message = "차단·해제 사유는 255자를 넘을 수 없습니다."
    )
    @Pattern(
        regexp = "^[^\\r\\n]*$",
        message = "차단·해제 사유에 줄바꿈 문자를 포함할 수 없습니다."
    )
    @Schema(
        description = "차단·해제 사유",
        example = "매크로 의심 좌석 임시 차단",
        requiredMode = Schema.RequiredMode.REQUIRED
    ) String reason
) {
}
