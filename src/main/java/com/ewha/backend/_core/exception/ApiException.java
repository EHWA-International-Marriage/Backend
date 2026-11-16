package com.ewha.backend._core.exception;

import lombok.Getter;

import org.springframework.http.HttpStatus;

/**
 * 모든 비즈니스 예외의 부모. 메시지는 사용자에게 그대로 노출되므로 개인정보·내부 정보 없이 한국어 문장으로 작성한다.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
