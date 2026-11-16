package com.ewha.backend.api.auth.dto;

import java.time.Instant;

import com.ewha.backend.entity.user.Role;

public record AccessTokenClaims(
        long userId,
        Role role,
        String jti,
        Instant expiresAt
) {
}
