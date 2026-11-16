package com.ewha.backend._core.infrastructure.servlet;

import java.time.Duration;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

import com.ewha.backend._core.config.properties.SecurityProperties;
import com.ewha.backend.api.auth.dto.TokenPair;

/**
 * 인증 토큰 쿠키를 읽고 쓴다. 모든 토큰 쿠키는 HttpOnly·Secure·SameSite로 발급한다.
 */
@Component
public class AuthCookieManager {

    public static final String ACCESS_TOKEN  = "access_token";
    public static final String REFRESH_TOKEN = "refresh_token";

    private final SecurityProperties.Cookie cookieProperties;
    private final Duration                  accessMaxAge;
    private final Duration                  refreshMaxAge;

    public AuthCookieManager(SecurityProperties securityProperties) {
        this.cookieProperties = securityProperties.cookie();
        this.accessMaxAge = securityProperties.jwt().accessExpiration();
        this.refreshMaxAge = securityProperties.jwt().refreshExpiration();
    }

    public Optional<String> findAccessToken(HttpServletRequest request) {
        return find(request, ACCESS_TOKEN);
    }

    public Optional<String> findRefreshToken(HttpServletRequest request) {
        return find(request, REFRESH_TOKEN);
    }

    public void addTokens(HttpServletResponse response, TokenPair tokens) {
        add(response, build(ACCESS_TOKEN, tokens.accessToken().value(), accessMaxAge));
        add(response, build(REFRESH_TOKEN, tokens.refreshToken().value(), refreshMaxAge));
    }

    public void clearTokens(HttpServletResponse response) {
        add(response, build(ACCESS_TOKEN, "", Duration.ZERO));
        add(response, build(REFRESH_TOKEN, "", Duration.ZERO));
    }

    ResponseCookie build(String name, String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie
                .from(name, value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path("/")
                .maxAge(maxAge);
        if (StringUtils.hasText(cookieProperties.domain())) {
            builder.domain(cookieProperties.domain());
        }
        return builder.build();
    }

    private static Optional<String> find(HttpServletRequest request, String name) {
        Cookie cookie = WebUtils.getCookie(request, name);
        return Optional.ofNullable(cookie).map(Cookie::getValue).filter(StringUtils::hasText);
    }

    private static void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
