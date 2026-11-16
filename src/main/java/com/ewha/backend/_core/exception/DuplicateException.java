package com.ewha.backend._core.exception;

import org.springframework.http.HttpStatus;

public class DuplicateException extends ApiException {

    private static final String FORMAT            = "이미 존재하는 %s입니다.";
    private static final String FORMAT_WITH_VALUE = "'%s'은(는) 이미 존재하는 %s입니다.";

    public DuplicateException(String name) {
        super(HttpStatus.CONFLICT, String.format(FORMAT, name));
    }

    /**
     * value는 응답에 그대로 노출된다. 이메일·연락처 등 개인정보는 넘기지 않는다.
     */
    public DuplicateException(String name, String value) {
        super(HttpStatus.CONFLICT, String.format(FORMAT_WITH_VALUE, value, name));
    }
}
