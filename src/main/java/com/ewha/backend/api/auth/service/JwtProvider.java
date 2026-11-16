package com.ewha.backend.api.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import lombok.extern.slf4j.Slf4j;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.ewha.backend._core.config.properties.SecurityProperties;
import com.ewha.backend.api.auth.dto.AccessTokenClaims;
import com.ewha.backend.api.auth.dto.IssuedToken;
import com.ewha.backend.api.auth.dto.RefreshTokenClaims;
import com.ewha.backend.entity.auth.exception.ExpiredTokenException;
import com.ewha.backend.entity.auth.exception.InvalidTokenException;
import com.ewha.backend.entity.user.Role;

/**
 * access·refresh JWT 발급과 검증(HS256). access와 refresh는 서로 다른 키로 서명해 섞어 쓸 수 없다.
 * <p>
 * 토큰 값과 라이브러리 예외 메시지는 로그·응답에 남기지 않는다.
 */
@Slf4j
@Component
public class JwtProvider {

    private static final String HMAC_ALGORITHM     = "HmacSHA256";
    private static final int    MIN_KEY_BYTES      = 32;
    private static final String ROLE_CLAIM         = "role";
    private static final String EXPIRED_ERROR_CODE = "token_expired";

    private final Clock      clock;
    private final Duration   accessExpiration;
    private final Duration   refreshExpiration;
    private final JwtEncoder accessEncoder;
    private final JwtDecoder accessDecoder;
    private final JwtEncoder refreshEncoder;
    private final JwtDecoder refreshDecoder;

    public JwtProvider(SecurityProperties securityProperties, Clock clock) {
        SecurityProperties.Jwt jwt = securityProperties.jwt();
        SecretKey accessKey = toKey(jwt.accessSecret(), "JWT_ACCESS_SECRET");
        SecretKey refreshKey = toKey(jwt.refreshSecret(), "JWT_REFRESH_SECRET");
        if (accessKey.equals(refreshKey)) {
            throw new IllegalStateException("JWT_ACCESS_SECRET과 JWT_REFRESH_SECRET은 달라야 합니다.");
        }
        this.clock = clock;
        this.accessExpiration = jwt.accessExpiration();
        this.refreshExpiration = jwt.refreshExpiration();
        this.accessEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(accessKey));
        this.accessDecoder = decoder(accessKey);
        this.refreshEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(refreshKey));
        this.refreshDecoder = decoder(refreshKey);
    }

    public Duration getAccessExpiration() {
        return accessExpiration;
    }

    public Duration getRefreshExpiration() {
        return refreshExpiration;
    }

    public IssuedToken createAccessToken(long userId, Role role) {
        return encode(accessEncoder, userId, accessExpiration, role);
    }

    public IssuedToken createRefreshToken(long userId) {
        return encode(refreshEncoder, userId, refreshExpiration, null);
    }

    /**
     * @throws ExpiredTokenException 만료
     * @throws InvalidTokenException 서명 불일치·형식 오류·필수 클레임 누락
     */
    public AccessTokenClaims parseAccessToken(String token) {
        Jwt jwt = decode(accessDecoder, token);
        String role = jwt.getClaimAsString(ROLE_CLAIM);
        if (role == null) {
            throw new InvalidTokenException();
        }
        try {
            return new AccessTokenClaims(userId(jwt), Role.valueOf(role), jwt.getId(), jwt.getExpiresAt());
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException();
        }
    }

    /**
     * @throws ExpiredTokenException 만료
     * @throws InvalidTokenException 서명 불일치·형식 오류·필수 클레임 누락
     */
    public RefreshTokenClaims parseRefreshToken(String token) {
        Jwt jwt = decode(refreshDecoder, token);
        return new RefreshTokenClaims(userId(jwt), jwt.getId(), jwt.getExpiresAt());
    }

    private IssuedToken encode(JwtEncoder encoder, long userId, Duration expiration, Role role) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(expiration);
        String jti = UUID.randomUUID().toString();

        JwtClaimsSet.Builder claims = JwtClaimsSet
                .builder()
                .subject(Long.toString(userId))
                .id(jti)
                .issuedAt(now)
                .expiresAt(expiresAt);
        if (role != null) {
            claims.claim(ROLE_CLAIM, role.name());
        }

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(value, jti, expiresAt);
    }

    private Jwt decode(JwtDecoder decoder, String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException();
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtValidationException e) {
            boolean expired = e.getErrors().stream().anyMatch(error -> EXPIRED_ERROR_CODE.equals(error.getErrorCode()));
            if (expired) {
                throw new ExpiredTokenException();
            }
            log.debug("[JWT] 검증 실패: {}", e.getClass().getSimpleName());
            throw new InvalidTokenException();
        } catch (JwtException e) {
            log.debug("[JWT] 해석 실패: {}", e.getClass().getSimpleName());
            throw new InvalidTokenException();
        }
        if (jwt.getSubject() == null || jwt.getId() == null || jwt.getExpiresAt() == null) {
            throw new InvalidTokenException();
        }
        return jwt;
    }

    private long userId(Jwt jwt) {
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new InvalidTokenException();
        }
    }

    private JwtDecoder decoder(SecretKey key) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(expirationValidator());
        return decoder;
    }

    // 시계 오차 허용 없이 exp만 검사하고, 만료를 다른 오류와 구분할 수 있게 전용 코드를 쓴다
    private OAuth2TokenValidator<Jwt> expirationValidator() {
        return jwt -> {
            Instant expiresAt = jwt.getExpiresAt();
            if (expiresAt == null || !clock.instant().isBefore(expiresAt)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(EXPIRED_ERROR_CODE));
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    private static SecretKey toKey(String base64Secret, String name) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + "는 Base64 형식이어야 합니다.");
        }
        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(name + "는 256bit 이상이어야 합니다.");
        }
        return new SecretKeySpec(bytes, HMAC_ALGORITHM);
    }
}
