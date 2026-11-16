package com.ewha.backend.entity.auth.exception;

import com.ewha.backend._core.exception.UnauthorizedException;

public class InvalidTokenException extends UnauthorizedException {

    public static final String MESSAGE = "유효하지 않은 토큰입니다.";

    public InvalidTokenException() {
        super(MESSAGE);
    }
}
