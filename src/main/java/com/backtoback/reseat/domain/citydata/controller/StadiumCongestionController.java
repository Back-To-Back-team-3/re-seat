package com.backtoback.reseat.domain.citydata.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backtoback.reseat.domain.citydata.dto.response.StadiumCongestionResponse;
import com.backtoback.reseat.domain.citydata.service.StadiumCongestionService;
import com.backtoback.reseat.global.common.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/congestion")
public class StadiumCongestionController implements StadiumCongestionControllerDocs {

    private final StadiumCongestionService stadiumCongestionService;

    /**
     * 특정 구장의 실시간 혼잡도 정보 조회
     *
     * @Param stadiumNum 구장 번호(예: 1)
     * @return 구장 실시간 혼잡도 응답 DTO
     */
    @Override
    @GetMapping("/stadiums/{stadiumNum}")
    public ResponseEntity<ApiResponse<StadiumCongestionResponse>> getStadiumCongestion(@PathVariable Long stadiumNum) {
        StadiumCongestionResponse response = stadiumCongestionService.getStadiumCongestion(stadiumNum);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("구장 실시간 혼잡도 조회 성공", response));
    }
}
