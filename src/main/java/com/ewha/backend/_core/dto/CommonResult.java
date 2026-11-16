package com.ewha.backend._core.dto;

import lombok.Getter;

@Getter
public class CommonResult {

    private final boolean error;
    private final String  message;

    public CommonResult(boolean error, String message) {
        this.error = error;
        this.message = message;
    }

    public CommonResult(String message) {
        this(false, message);
    }
}
