package com.ewha.backend._core.infrastructure.handler;

import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.ewha.backend._core.dto.ApiResponse;
import com.ewha.backend._core.dto.CommonResult;
import com.ewha.backend._core.exception.ApiException;

/**
 * 예외 → 응답 변환은 이 클래스에서만 한다. 로그와 응답에 요청 값(개인정보 포함 가능)을 남기지 않는다.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    static final String BAD_REQUEST_MESSAGE           = "잘못된 요청입니다.";
    static final String NOT_FOUND_MESSAGE             = "요청한 리소스를 찾을 수 없습니다.";
    static final String METHOD_NOT_ALLOWED_MESSAGE    = "허용되지 않은 HTTP 메서드입니다.";
    static final String INTERNAL_SERVER_ERROR_MESSAGE = "서버에서 알 수 없는 오류가 발생했습니다.";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<CommonResult> handleApiException(ApiException e) {
        if (e.getStatus().is5xxServerError()) {
            log.error("[ApiException] {}", e.getMessage(), e);
        } else {
            log.info("[ApiException] {} {}", e.getStatus().value(), e.getMessage());
        }
        return ApiResponse.failedOf(e);
    }

    // @Valid 검증 실패: "필드: 메시지" 형태로 응답. 거부된 값은 응답·로그에 넣지 않는다.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResult> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .map(ApiExceptionHandler::formatFieldError)
                .collect(Collectors.joining(", "));
        log
                .info(
                        "[Validation] fields={}",
                        e
                                .getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .map(FieldError::getField)
                                .toList()
                );
        return ApiResponse.failedOf(HttpStatus.BAD_REQUEST, message);
    }

    // JSON 파싱 실패: 파서 메시지에 요청 본문 일부가 섞일 수 있어 고정 메시지로 응답
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CommonResult> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.info("[NotReadable] {}", e.getClass().getSimpleName());
        return ApiResponse.failedOf(HttpStatus.BAD_REQUEST, BAD_REQUEST_MESSAGE);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<CommonResult> handleMissingParameter(MissingServletRequestParameterException e) {
        log.info("[MissingParameter] {}", e.getParameterName());
        return ApiResponse.failedOf(HttpStatus.BAD_REQUEST, e.getParameterName() + ": 필수 값입니다.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CommonResult> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.info("[TypeMismatch] {}", e.getName());
        return ApiResponse.failedOf(HttpStatus.BAD_REQUEST, e.getName() + ": 형식이 올바르지 않습니다.");
    }

    // 없는 경로는 반드시 404 (soft 404 방지)
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<CommonResult> handleNoResourceFound(NoResourceFoundException e) {
        return ApiResponse.failedOf(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<CommonResult> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.info("[MethodNotSupported] {}", e.getMethod());
        return ApiResponse.failedOf(HttpStatus.METHOD_NOT_ALLOWED, METHOD_NOT_ALLOWED_MESSAGE);
    }

    // 처리하지 못한 예외: 상세는 로그에만, 응답은 고정 메시지
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResult> handleUnexpected(Exception e) {
        log.error("[Unhandled] {}", e.getClass().getName(), e);
        return ApiResponse.failedOf(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR_MESSAGE);
    }

    private static String formatFieldError(FieldError fieldError) {
        String message = fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "";
        return fieldError.getField() + ": " + message;
    }
}
