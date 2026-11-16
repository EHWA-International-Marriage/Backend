package com.ewha.backend.api.auth.dto;

import java.time.Instant;

public record RefreshTokenClaims(
        long userId,
        String jti,
        Instant expiresAt
) {
}
