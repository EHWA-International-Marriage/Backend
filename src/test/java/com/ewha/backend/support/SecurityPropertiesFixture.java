package com.ewha.backend.support;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import com.ewha.backend._core.config.properties.SecurityProperties;

public final class SecurityPropertiesFixture {

    public static final Duration ACCESS_EXPIRATION  = Duration.ofMinutes(30);
    public static final Duration REFRESH_EXPIRATION = Duration.ofDays(14);

    private SecurityPropertiesFixture() {
    }

    public static String randomKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static SecurityProperties create() {
        return create(randomKey(), randomKey(), null);
    }

    public static SecurityProperties create(String accessSecret, String refreshSecret, String cookieDomain) {
        return new SecurityProperties(
                new SecurityProperties.Jwt(accessSecret, refreshSecret, ACCESS_EXPIRATION, REFRESH_EXPIRATION),
                new SecurityProperties.Cors(List.of("http://localhost:3000")),
                new SecurityProperties.Cookie(cookieDomain, true, "Lax"),
                new SecurityProperties.Encryption(randomKey())
        );
    }
}
