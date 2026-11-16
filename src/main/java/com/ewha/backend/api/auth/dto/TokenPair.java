package com.ewha.backend.api.auth.dto;

public record TokenPair(
        IssuedToken accessToken,
        IssuedToken refreshToken
) {
}
