package com.ewha.backend._core.config.properties;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 인증·보안 설정. 값은 .env → application.yaml(security.*)로 주입한다. 비어 있으면 기동 시 실패한다.
 */
@Validated
@ConfigurationProperties(prefix = "security")
public record SecurityProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Cookie cookie,
        @Valid @NotNull Encryption encryption
) {

    /**
     * @param accessSecret      Base64, 256bit 이상
     * @param refreshSecret     Base64, 256bit 이상. access와 다른 값이어야 한다
     * @param accessExpiration  숫자만 쓰면 밀리초
     * @param refreshExpiration 숫자만 쓰면 밀리초
     */
    public record Jwt(
            @NotBlank String accessSecret,
            @NotBlank String refreshSecret,
            @NotNull Duration accessExpiration,
            @NotNull Duration refreshExpiration
    ) {
    }

    public record Cors(@NotEmpty List<String> origins) {
    }

    /**
     * @param domain   비어 있으면 Domain 속성 없이(host-only) 발급한다
     * @param sameSite Strict / Lax / None
     */
    public record Cookie(
            String domain,
            boolean secure,
            @NotBlank String sameSite
    ) {
    }

    /**
     * @param aesKey Base64, 256bit
     */
    public record Encryption(@NotBlank String aesKey) {
    }
}
