package com.backtoback.reseat.domain.admin.game.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backtoback.reseat.domain.admin.game.dto.request.GameRegisterRequest;
import com.backtoback.reseat.domain.admin.game.dto.response.GameRegisterResponse;
import com.backtoback.reseat.domain.admin.game.service.AdminGameRegisterService;
import com.backtoback.reseat.global.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 관리자 전용 경기 등록 API.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/games")
public class AdminGameRegisterController implements AdminGameRegisterControllerDocs {

    private final AdminGameRegisterService adminGameRegisterService;

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<GameRegisterResponse>> registerGame(
        @Valid @RequestBody GameRegisterRequest request
    ) {
        GameRegisterResponse response = adminGameRegisterService.registerGame(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("경기 등록 완료", response));
    }
}
