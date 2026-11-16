package com.ewha.backend._core.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ApiException {

    private static final String DEFAULT_MESSAGE = "로그인이 필요합니다.";

    public UnauthorizedException() {
        this(DEFAULT_MESSAGE);
    }

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
