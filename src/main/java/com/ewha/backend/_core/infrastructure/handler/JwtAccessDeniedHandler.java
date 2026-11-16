package com.ewha.backend._core.infrastructure.handler;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.ewha.backend._core.exception.ForbiddenException;

/**
 * 로그인했지만 권한이 부족하면 403.
 */
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private static final String MESSAGE = new ForbiddenException().getMessage();

    private final SecurityErrorResponder responder;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        responder.write(response, HttpStatus.FORBIDDEN, MESSAGE);
    }
}
