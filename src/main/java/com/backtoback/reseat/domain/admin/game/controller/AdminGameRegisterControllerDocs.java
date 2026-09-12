package com.backtoback.reseat.domain.admin.game.controller;

import org.springframework.http.ResponseEntity;

import com.backtoback.reseat.domain.admin.game.dto.request.GameRegisterRequest;
import com.backtoback.reseat.domain.admin.game.dto.response.GameRegisterResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 관리자용 경기 등록 API Swagger 문서화 인터페이스.
 */
@Tag(
    name = "Admin - Game Register",
    description = "관리자 경기 등록 API (ROLE_ADMIN 전용)"
)
public interface AdminGameRegisterControllerDocs {

    @Operation(
        summary = "경기 등록",
        description = """
            ROLE_ADMIN 필요. 포스트시즌 일정 추가, 우천취소 재경기 등록 등
            예외 상황에 대응하기 위해 신규 경기를 등록한다.

            등록 시 bookingStatus는 SCHEDULED로 고정된다.
            좌석 재고는 이 API에서 생성하지 않는다(좌석 재고 오픈 API 별도 호출 필요).
            """,
        security = @SecurityRequirement(name = "JWT Bearer Token")
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "경기 등록 성공",
                content = @Content(
                    examples = @ExampleObject(
                        name = "등록 성공 예시",
                        value = """
                            {
                                "success": true,
                                "errorCode": null,
                                "message": "경기 등록 완료",
                                "data": {
                                    "gameId": 10,
                                    "bookingStatus": "SCHEDULED"
                                }
                            }
                            """
                    )
                )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "INVALID_REQUEST(필수값 누락·형식 오류·예매 시각 순서 오류) " + "/ SAME_TEAM_MATCH(홈팀·원정팀 동일)",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "UNAUTHORIZED — 미인증",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "FORBIDDEN — ADMIN 권한 없음",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "TEAM_NOT_FOUND(미존재 팀) / STADIUM_NOT_FOUND(미존재 구장)",
                content = @Content(
                    examples = @ExampleObject(
                        name = "구장 없음 예시",
                        value = """
                            {
                                "success": false,
                                "errorCode": "STADIUM_NOT_FOUND",
                                "message": "구장을 찾을 수 없습니다. stadiumId=999"
                            }
                            """
                    )
                )
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<GameRegisterResponse>> registerGame(
        GameRegisterRequest request
    );
}
