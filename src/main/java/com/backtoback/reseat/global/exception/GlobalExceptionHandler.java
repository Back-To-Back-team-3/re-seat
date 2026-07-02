//전역 예외

package com.backtoback.reseat.global.exception;

import com.backtoback.reseat.domain.user.exception.DuplicateEmailException;
import com.backtoback.reseat.global.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    //이메일 / 닉네임/ 전화번호 중복 예외처리 - 409Error
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateEmailException(DuplicateEmailException e){
        log.warn("DuplicateEmailException 발생: {}", e.getMessage());

        ErrorCode errorCode = ErrorCode.DUPLICATE_LOGIN_ID;

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.failure(errorCode.getCode(), errorCode.getMessage()));
    }

    //@Valid 유효성 검증 실패 처리 - 400 에러
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e){
        log.warn("MethodArgumentNotvalidException 발생: {}", e.getMessage());

        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.failure(errorCode.getCode(), errorCode.getMessage()));
    }
    //서버 내부 에러 - 500 에러
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllException(Exception e) {
        log.error("예상치 못한 서버 에러 발생: ", e);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("SERVER_ERROR", "서버 내부 에러가 발생했습니다."));
    }

}
