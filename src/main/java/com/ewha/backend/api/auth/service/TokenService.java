package com.ewha.backend.api.auth.service;

import java.time.Clock;
import java.time.Duration;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import com.ewha.backend._core.exception.ApiException;
import com.ewha.backend.api.auth.dto.AccessTokenClaims;
import com.ewha.backend.api.auth.dto.IssuedToken;
import com.ewha.backend.api.auth.dto.RefreshTokenClaims;
import com.ewha.backend.api.auth.dto.TokenPair;
import com.ewha.backend.entity.auth.exception.InvalidTokenException;
import com.ewha.backend.entity.auth.repository.AccessTokenBlacklistRepository;
import com.ewha.backend.entity.auth.repository.RefreshTokenRepository;
import com.ewha.backend.entity.auth.repository.RefreshTokenRepository.Owner;
import com.ewha.backend.entity.user.Role;

/**
 * 토큰 발급·재발급(refresh 회전)·폐기. 로그인·회원 API가 이 서비스를 호출한다.
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtProvider                    jwtProvider;
    private final RefreshTokenRepository         refreshTokenRepository;
    private final AccessTokenBlacklistRepository blacklistRepository;
    private final Clock                          clock;

    public TokenPair issue(long userId, Role role) {
        IssuedToken accessToken = jwtProvider.createAccessToken(userId, role);
        IssuedToken refreshToken = jwtProvider.createRefreshToken(userId);
        refreshTokenRepository.save(refreshToken.jti(), userId, role, jwtProvider.getRefreshExpiration());
        return new TokenPair(accessToken, refreshToken);
    }

    /**
     * refresh 토큰을 한 번 쓰면 폐기하고 새 쌍을 발급한다. 이미 쓴(또는 로그아웃한) refresh 토큰은 401.
     */
    public TokenPair reissue(String refreshToken) {
        RefreshTokenClaims claims = jwtProvider.parseRefreshToken(refreshToken);
        Owner owner = refreshTokenRepository.consume(claims.jti()).orElseThrow(InvalidTokenException::new);
        if (owner.userId() != claims.userId()) {
            throw new InvalidTokenException();
        }
        return issue(owner.userId(), owner.role());
    }

    /**
     * access 토큰은 만료 전까지 블랙리스트에 올리고, refresh 토큰은 삭제한다. 이미 만료·위조된 토큰은 무시한다.
     */
    public void revoke(String accessToken, String refreshToken) {
        if (accessToken != null) {
            try {
                AccessTokenClaims claims = jwtProvider.parseAccessToken(accessToken);
                blacklistRepository.add(claims.jti(), Duration.between(clock.instant(), claims.expiresAt()));
            } catch (ApiException ignored) {
                // 만료·위조된 access 토큰은 어차피 인증에 쓸 수 없다
            }
        }
        if (refreshToken != null) {
            try {
                refreshTokenRepository.delete(jwtProvider.parseRefreshToken(refreshToken).jti());
            } catch (ApiException ignored) {
                // 만료·위조된 refresh 토큰은 Redis에도 없거나 곧 사라진다
            }
        }
    }
}
