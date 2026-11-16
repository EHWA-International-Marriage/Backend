package com.ewha.backend._core.exception;

import org.springframework.http.HttpStatus;

/**
 * 도메인별 NotFound 예외(예: NotFoundBoardException)는 이 클래스를 상속해 대상 이름만 넘긴다.
 */
public class NotFoundException extends ApiException {

    private static final String FORMAT = "%s을(를) 찾을 수 없습니다.";

    public NotFoundException(String name) {
        super(HttpStatus.NOT_FOUND, String.format(FORMAT, name));
    }
}
