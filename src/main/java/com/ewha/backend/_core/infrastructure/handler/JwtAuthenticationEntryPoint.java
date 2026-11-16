package com.ewha.backend._core.infrastructure.handler;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.ewha.backend._core.exception.UnauthorizedException;
import com.ewha.backend._core.infrastructure.filter.JwtAuthenticationFilter;
import com.ewha.backend.entity.auth.exception.ExpiredTokenException;
import com.ewha.backend.entity.auth.exception.InvalidTokenException;

/**
 * 인증이 필요한 경로에 인증 없이 접근하면 401. 만료는 메시지를 구분해 프론트가 재발급을 시도할 수 있게 한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String LOGIN_REQUIRED = new UnauthorizedException().getMessage();

    private final SecurityErrorResponder responder;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        Object failure = request.getAttribute(JwtAuthenticationFilter.FAILURE_ATTRIBUTE);
        String message = LOGIN_REQUIRED;
        if (failure == JwtAuthenticationFilter.Failure.EXPIRED) {
            message = ExpiredTokenException.MESSAGE;
        } else if (failure == JwtAuthenticationFilter.Failure.INVALID) {
            message = InvalidTokenException.MESSAGE;
        }
        responder.write(response, HttpStatus.UNAUTHORIZED, message);
    }
}
