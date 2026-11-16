package com.ewha.backend.entity.auth.exception;

import com.ewha.backend._core.exception.UnauthorizedException;

public class ExpiredTokenException extends UnauthorizedException {

    public static final String MESSAGE = "만료된 토큰입니다.";

    public ExpiredTokenException() {
        super(MESSAGE);
    }
}
