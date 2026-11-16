package com.ewha.backend.api.auth.dto;

import java.time.Instant;

/**
 * 발급한 토큰. value는 쿠키로만 내보내고 로그에 남기지 않는다.
 */
public record IssuedToken(
        String value,
        String jti,
        Instant expiresAt
) {

    @Override
    public String toString() {
        return "IssuedToken[jti=" + jti + ", expiresAt=" + expiresAt + "]";
    }
}
