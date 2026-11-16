package com.ewha.backend._core.infrastructure.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.Cookie;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ewha.backend.api.auth.dto.IssuedToken;
import com.ewha.backend.api.auth.dto.TokenPair;
import com.ewha.backend.support.SecurityPropertiesFixture;

class AuthCookieManagerTest {

    @Test
    @DisplayName("토큰 쿠키는 HttpOnly·Secure·SameSite·Path=/ 이고 수명은 토큰 만료와 같다")
    void cookieAttributes() {
        AuthCookieManager manager = new AuthCookieManager(
                SecurityPropertiesFixture
                        .create(
                                SecurityPropertiesFixture.randomKey(),
                                SecurityPropertiesFixture.randomKey(),
                                "example.com"
                        )
        );

        ResponseCookie cookie = manager
                .build(AuthCookieManager.ACCESS_TOKEN, "value", SecurityPropertiesFixture.ACCESS_EXPIRATION);

        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getDomain()).isEqualTo("example.com");
        assertThat(cookie.getMaxAge()).isEqualTo(SecurityPropertiesFixture.ACCESS_EXPIRATION);
    }

    @Test
    @DisplayName("COOKIE_DOMAIN이 비어 있으면 Domain 속성 없이 발급한다")
    void hostOnly() {
        AuthCookieManager manager = new AuthCookieManager(SecurityPropertiesFixture.create());

        assertThat(manager.build(AuthCookieManager.ACCESS_TOKEN, "v", Duration.ofMinutes(1)).getDomain()).isNull();
    }

    @Test
    @DisplayName("addTokens는 access·refresh 쿠키를, clearTokens는 Max-Age=0 쿠키를 내린다")
    void addAndClear() {
        AuthCookieManager manager = new AuthCookieManager(SecurityPropertiesFixture.create());
        Instant exp = Instant.now();
        TokenPair tokens = new TokenPair(new IssuedToken("a", "1", exp), new IssuedToken("r", "2", exp));

        MockHttpServletResponse added = new MockHttpServletResponse();
        manager.addTokens(added, tokens);
        List<String> addedHeaders = added.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(addedHeaders).hasSize(2);
        assertThat(addedHeaders.get(0)).startsWith("access_token=a;").contains("HttpOnly", "Secure", "SameSite=Lax");
        assertThat(addedHeaders.get(1)).startsWith("refresh_token=r;");

        MockHttpServletResponse cleared = new MockHttpServletResponse();
        manager.clearTokens(cleared);
        assertThat(cleared.getHeaders(HttpHeaders.SET_COOKIE)).allSatisfy(h -> assertThat(h).contains("Max-Age=0"));
    }

    @Test
    @DisplayName("요청 쿠키에서 토큰을 읽고, 비어 있으면 empty")
    void find() {
        AuthCookieManager manager = new AuthCookieManager(SecurityPropertiesFixture.create());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request
                .setCookies(
                        new Cookie(AuthCookieManager.ACCESS_TOKEN, "a"),
                        new Cookie(AuthCookieManager.REFRESH_TOKEN, "")
                );

        assertThat(manager.findAccessToken(request)).contains("a");
        assertThat(manager.findRefreshToken(request)).isEmpty();
    }
}
