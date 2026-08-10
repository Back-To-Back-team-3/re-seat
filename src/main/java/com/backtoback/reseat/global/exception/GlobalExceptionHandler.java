//전역 예외

package com.backtoback.reseat.global.exception;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.backtoback.reseat.global.common.ApiResponse;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    //@Valid 유효성 검증 실패 처리 - 400 에러
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("MethodArgumentNotvalidException 발생: {}", e.getMessage());

        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(ApiResponse.failure(errorCode.getCode(), errorCode.getMessage()));
    }

    //도메인 에서 던지는 비즈니스 예외 처리
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("{} 발생: {}", e.getClass().getSimpleName(), e.getMessage());

        return ResponseEntity
            .status(e.getErrorCode().getHttpStatus())
            .body(ApiResponse.failure(e.getErrorCode().getCode(), e.getMessage()));
    }

    //서버 내부 에러 - 500 에러
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllException(Exception e) {
        log.error("예상치 못한 서버 에러 발생: ", e);

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(ApiResponse.failure(errorCode.getCode(), errorCode.getMessage()));
    }

    //HTTP 요청 파라미터 타입 불일치
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumenTpyeMismatchException(
        MethodArgumentTypeMismatchException e) {
        log.warn("MethodArgumentTypeMismatchException 발생: {} ", e.getMessage());

        String requiredTypeName = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "Unknown";
        String detailMessage = String.format("'%s' 파라미터의 타입이 잘못되었습니다. (입력값: '%s', 기대타입: %s)",
            e.getName(), e.getValue(), requiredTypeName);

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.failure(ErrorCode.INVALID_REQUEST.getCode(), detailMessage));
    }

    // 필수 쿼리 파라미터 누락 처리
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(
        MissingServletRequestParameterException e) {
        log.warn("MissingServletRequestParameterException 발생: {}", e.getMessage());

        String detailMessage = String.format("필수 쿼리 파라미터 '%s'(타입: %s)가 누락되었습니다.",
            e.getParameterName(), e.getParameterType());

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.failure(ErrorCode.INVALID_REQUEST.getCode(), detailMessage));
    }

    //파라미터 제약 조건 위반 처리 (@RequestParam 유효성 검증 실패 등)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException e) {
        log.warn("ConstraintViolationException 발생: {}", e.getMessage());

        String detailMessage = e.getConstraintViolations().stream()
            .map(violation -> String.format("[%s] %s",
                violation.getPropertyPath(),
                violation.getMessage()))
            .collect(Collectors.joining(", "));

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.failure(ErrorCode.INVALID_REQUEST.getCode(), detailMessage));
    }

    // HTTP 요청 바디(JSON) 파싱 실패 처리 (JSON 문법 오류 등)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("HttpMessageNotReadableException 발생: {}", e.getMessage());

        String detailMessage = "HTTP 요청 바디(JSON)를 읽을 수 없거나 형식이 올바르지 않습니다.";

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.failure(ErrorCode.INVALID_REQUEST.getCode(), detailMessage));
    }

}
