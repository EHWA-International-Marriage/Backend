package com.ewha.backend.api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ewha.backend._core.config.properties.SecurityProperties;
import com.ewha.backend.api.auth.dto.AccessTokenClaims;
import com.ewha.backend.api.auth.dto.IssuedToken;
import com.ewha.backend.api.auth.dto.RefreshTokenClaims;
import com.ewha.backend.entity.auth.exception.ExpiredTokenException;
import com.ewha.backend.entity.auth.exception.InvalidTokenException;
import com.ewha.backend.entity.user.Role;
import com.ewha.backend.support.SecurityPropertiesFixture;

class JwtProviderTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final String             accessSecret  = SecurityPropertiesFixture.randomKey();
    private final String             refreshSecret = SecurityPropertiesFixture.randomKey();
    private final SecurityProperties properties    = SecurityPropertiesFixture
            .create(accessSecret, refreshSecret, null);
    private final JwtProvider        jwtProvider   = providerAt(NOW);

    @Test
    @DisplayName("access 토큰을 발급하고 userId·role·jti·만료 시각을 그대로 읽는다")
    void accessToken() {
        IssuedToken token = jwtProvider.createAccessToken(7L, Role.ADMIN);

        AccessTokenClaims claims = jwtProvider.parseAccessToken(token.value());

        assertThat(claims.userId()).isEqualTo(7L);
        assertThat(claims.role()).isEqualTo(Role.ADMIN);
        assertThat(claims.jti()).isEqualTo(token.jti());
        assertThat(claims.expiresAt()).isEqualTo(NOW.plus(SecurityPropertiesFixture.ACCESS_EXPIRATION));
    }

    @Test
    @DisplayName("refresh 토큰을 발급하고 userId·jti를 읽는다")
    void refreshToken() {
        IssuedToken token = jwtProvider.createRefreshToken(7L);

        RefreshTokenClaims claims = jwtProvider.parseRefreshToken(token.value());

        assertThat(claims.userId()).isEqualTo(7L);
        assertThat(claims.jti()).isEqualTo(token.jti());
        assertThat(claims.expiresAt()).isEqualTo(NOW.plus(SecurityPropertiesFixture.REFRESH_EXPIRATION));
    }

    @Test
    @DisplayName("발급할 때마다 jti가 다르다")
    void uniqueJti() {
        assertThat(jwtProvider.createAccessToken(1L, Role.USER).jti())
                .isNotEqualTo(jwtProvider.createAccessToken(1L, Role.USER).jti());
    }

    @Test
    @DisplayName("만료 시각이 지나면 ExpiredTokenException")
    void expired() {
        IssuedToken access = jwtProvider.createAccessToken(1L, Role.USER);
        IssuedToken refresh = jwtProvider.createRefreshToken(1L);

        JwtProvider afterAccessExpiry = providerAt(NOW.plus(SecurityPropertiesFixture.ACCESS_EXPIRATION));
        JwtProvider afterRefreshExpiry = providerAt(NOW.plus(SecurityPropertiesFixture.REFRESH_EXPIRATION));

        assertThatThrownBy(() -> afterAccessExpiry.parseAccessToken(access.value()))
                .isInstanceOf(ExpiredTokenException.class);
        assertThatThrownBy(() -> afterRefreshExpiry.parseRefreshToken(refresh.value()))
                .isInstanceOf(ExpiredTokenException.class);
    }

    @Test
    @DisplayName("만료 1초 전에는 유효하다")
    void notYetExpired() {
        IssuedToken access = jwtProvider.createAccessToken(1L, Role.USER);

        JwtProvider beforeExpiry = providerAt(NOW.plus(SecurityPropertiesFixture.ACCESS_EXPIRATION).minusSeconds(1));

        assertThat(beforeExpiry.parseAccessToken(access.value()).userId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 InvalidTokenException")
    void forgedSignature() {
        SecurityProperties otherKeys = SecurityPropertiesFixture.create();
        JwtProvider attacker = new JwtProvider(otherKeys, Clock.fixed(NOW, ZoneOffset.UTC));

        String forged = attacker.createAccessToken(1L, Role.ADMIN).value();

        assertThatThrownBy(() -> jwtProvider.parseAccessToken(forged)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("payload를 바꾼 토큰(권한 상승 시도)은 InvalidTokenException")
    void tamperedPayload() {
        String[] parts = jwtProvider.createAccessToken(1L, Role.USER).value().split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        String tamperedPayload = Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.replace("\"USER\"", "\"ADMIN\"").getBytes());
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThatThrownBy(() -> jwtProvider.parseAccessToken(tampered)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("refresh 토큰을 access 토큰으로, access 토큰을 refresh 토큰으로 쓸 수 없다")
    void tokenTypeMismatch() {
        String refresh = jwtProvider.createRefreshToken(1L).value();
        String access = jwtProvider.createAccessToken(1L, Role.USER).value();

        assertThatThrownBy(() -> jwtProvider.parseAccessToken(refresh)).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> jwtProvider.parseRefreshToken(access)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("형식이 잘못됐거나 비어 있는 토큰은 InvalidTokenException")
    void malformed() {
        assertThatThrownBy(() -> jwtProvider.parseAccessToken("not-a-jwt")).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> jwtProvider.parseAccessToken("")).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> jwtProvider.parseAccessToken(null)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("키가 짧거나 access·refresh 키가 같으면 생성 단계에서 실패한다")
    void invalidKeys() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        String key = SecurityPropertiesFixture.randomKey();

        assertThatThrownBy(
                () -> new JwtProvider(
                        SecurityPropertiesFixture.create(shortKey, refreshSecret, null),
                        Clock.systemUTC()
                )
        ).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                () -> new JwtProvider(SecurityPropertiesFixture.create(key, key, null), Clock.systemUTC())
        )
                .isInstanceOf(IllegalStateException.class);
    }

    private JwtProvider providerAt(Instant instant) {
        return new JwtProvider(properties, Clock.fixed(instant, ZoneOffset.UTC));
    }
}
