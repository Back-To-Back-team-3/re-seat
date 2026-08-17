package com.backtoback.reseat.domain.user.admin.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import com.backtoback.reseat.domain.user.admin.dto.request.AdminUserRoleUpdateRequest;
import com.backtoback.reseat.domain.user.admin.dto.request.AdminUserStatusUpdateRequest;
import com.backtoback.reseat.domain.user.admin.dto.request.UserSearchCondition;
import com.backtoback.reseat.domain.user.admin.dto.response.AdminUserResponse;
import com.backtoback.reseat.global.common.ApiResponse;
import com.backtoback.reseat.global.common.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
    name = "Admin - User",
    description = "관리자 회원 관리 API (ROLE_ADMIN 전용)"
)
@SecurityRequirement(name = "JWT Bearer Token")
public interface AdminUserControllerDocs {

    @Operation(
        summary = "회원 목록 검색 조회",
        description = "검색 조건(이메일, 이름, 전화번호, 권한, 상태 등)과 페이징 정보를 기반으로 회원 목록을 조회합니다."
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "회원 목록 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "UNAUTHORIZED — 미인증",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "FORBIDDEN — 관리자 권한 없음",
                content = @Content
            )
        }
    )
    ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> searchUsers(
        UserSearchCondition condition,
        Pageable pageable
    );

    @Operation(
        summary = "회원 상세 정보 조회",
        description = "특정 회원의 PK ID로 상세 프로필 및 계정 정보를 조회합니다."
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "회원 상세 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "USER_NOT_FOUND — 존재하지 않는 회원",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "FORBIDDEN — 관리자 권한 없음",
                content = @Content
            )
        }
    )
    ResponseEntity<ApiResponse<AdminUserResponse>> getUserDetail(
        @Parameter(
            description = "회원 PK ID",
            example = "1",
            required = true
        ) Long userId
    );

    @Operation(
        summary = "회원 권한 변경",
        description = "특정 회원의 권한(USER, ADMIN 등)을 변경합니다."
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "회원 권한 변경 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "INVALID_REQUEST — 잘못된 권한 값",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "USER_NOT_FOUND — 존재하지 않는 회원",
                content = @Content
            )
        }
    )
    ResponseEntity<ApiResponse<Void>> updateUserRole(
        @Parameter(
            description = "회원 PK ID",
            example = "1",
            required = true
        ) Long userId,
        AdminUserRoleUpdateRequest request
    );

    @Operation(
        summary = "회원 상태 변경",
        description = "특정 회원의 상태(ACTIVE, SUSPENDED, DELETED 등)를 변경합니다."
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "회원 상태 변경 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "INVALID_REQUEST — 잘못된 상태 값",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "USER_NOT_FOUND — 존재하지 않는 회원",
                content = @Content
            )
        }
    )
    ResponseEntity<ApiResponse<Void>> updateUserStatus(
        @Parameter(
            description = "회원 PK ID",
            example = "1",
            required = true
        ) Long userId,
        AdminUserStatusUpdateRequest request
    );
}
