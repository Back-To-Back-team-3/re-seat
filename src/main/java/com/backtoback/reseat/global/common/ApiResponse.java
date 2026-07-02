package com.backtoback.reseat.global.common;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    private final boolean success;   // 명세서 규격: true / false
    private final String errorCode;  // 명세서 규격: "errorCode"
    private final String message;    // 명세서 규격: "message"
    private final T data;            // 명세서 규격: "data"

    // 1. 성공 응답 (데이터만 보낼 때)
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null, "요청이 성공했습니다.", data);
    }

    // 2. 성공 응답 (메시지와 데이터를 함께 보낼 때)
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, null, message, data);
    }

    // 3. 단순 에러 응답
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, "SERVER_ERROR", message, null);
    }

    public static ApiResponse<Void> failure(String errorCode, String message) {
        return new ApiResponse<>(false, errorCode, message, null);
    }
}