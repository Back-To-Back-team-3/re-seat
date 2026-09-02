package com.backtoback.reseat.domain.citydata.controller;

import org.springframework.http.ResponseEntity;

import com.backtoback.reseat.domain.citydata.dto.response.StadiumCongestionResponse;
import com.backtoback.reseat.global.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
    name = "CityData",
    description = "구장 주변 실시간 혼잡도 조회 API"
)
public interface StadiumCongestionControllerDocs {

    @Operation(
        summary = "구장 실시간 혼잡도 조회",
        description = """
            서울시 실시간 도시데이터 OpenAPI를 연동하여 특정 구장 주변의 실시간 인구 혼잡도 정보를 조회한다.
            - 인증 없이 접근 가능한 공개 API
            - 조회 결과는 서버 Redis에 10분간 캐싱
            - 현재는 잠실 야구장(stadiumNum: 1) 혼잡도 정보를 제공
            """,
        security = {}
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "구장 실시간 혼잡도 조회 성공",
                content = @Content(
                    examples = @ExampleObject(
                        name = "혼잡도 조회 성공 예시",
                        value = """
                            {
                                "success": true,
                                "message": "구장 실시간 혼잡도 조회 성공",
                                "data": {
                                    "stadiumNum": 1,
                                    "stadiumName": "서울종합운동장 야구장",
                                    "areaName": "잠실종합운동장",
                                    "congestionLevel": "붐빔",
                                    "congestionMessage": "사람들이 몰려있어 혼잡합니다.",
                                    "populationMin": 24000,
                                    "populationMax": 26000,
                                    "latitude": 37.5121,
                                    "longitude": 127.0719,
                                    "observedAt": "2026-09-01 19:30"
                                }
                            }
                            """
                    )
                )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "STADIUM_NOT_FOUND — 지원하지 않거나 존재하지 않는 구장 ID",
                content = @Content(
                    examples = @ExampleObject(
                        name = "미지원 구장 예시",
                        value = """
                            {
                                "success": false,
                                "errorCode": "STADIUM_NOT_FOUND",
                                "message": "해당 구장의 혼잡도 정보를 찾을 수 없거나 지원하지 않는 구장입니다. 구장ID:99"
                            }
                            """
                    )
                )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "502",
                description = "EXTERNAL_API_ERROR — 서울시 도시데이터 API 통신 장애",
                content = @Content(
                    examples = @ExampleObject(
                        name = "외부 API 연동 실패 예시",
                        value = """
                            {
                                "success": false,
                                "errorCode": "EXTERNAL_API_ERROR",
                                "message": "서울시 도시데이터 API 통신 중 예외가 발생했습니다"
                            }
                            """
                    )
                )
            )
        }
    )
    ResponseEntity<ApiResponse<StadiumCongestionResponse>> getStadiumCongestion(
        @Parameter(
            description = "구장 번호/ID (예: 1 - 서울종합운동장 야구장)",
            example = "1",
            required = true
        ) Long stadiumNum
    );
}
