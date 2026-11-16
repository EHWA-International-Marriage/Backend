package com.ewha.backend._core.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends ApiException {

    private static final String DEFAULT_MESSAGE = "접근 권한이 없습니다.";

    public ForbiddenException() {
        this(DEFAULT_MESSAGE);
    }

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
