package com.ewha.backend._core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import jakarta.servlet.http.Cookie;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ewha.backend._core.config.properties.SecurityProperties;
import com.ewha.backend._core.infrastructure.servlet.AuthCookieManager;
import com.ewha.backend.api.auth.dto.TokenPair;
import com.ewha.backend.api.auth.service.JwtProvider;
import com.ewha.backend.api.auth.service.TokenService;
import com.ewha.backend.entity.auth.exception.ExpiredTokenException;
import com.ewha.backend.entity.auth.exception.InvalidTokenException;
import com.ewha.backend.entity.user.Role;
import com.ewha.backend.support.SecurityPropertiesFixture;

/**
 * 실제 필터 체인 + Redis(docker compose)로 경로 권한, 블랙리스트, refresh 회전, 보안 헤더, CORS를 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    private static final String PUBLIC_URL = "/test/public";
    private static final String AUTH_URL   = "/auth/test/me";
    private static final String ADMIN_URL  = "/admin/test/ping";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TokenService tokenService;

    @Autowired
    SecurityProperties securityProperties;

    @Nested
    @DisplayName("경로별 권한")
    class Authorization {

        @Test
        @DisplayName("공개 경로는 토큰 없이 200, findUserId()는 empty")
        void publicWithoutToken() throws Exception {
            mockMvc
                    .perform(get(PUBLIC_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.loggedIn").value(false));
        }

        @Test
        @DisplayName("공개 경로는 잘못된 토큰이 있어도 막지 않는다")
        void publicWithInvalidToken() throws Exception {
            mockMvc
                    .perform(get(PUBLIC_URL).cookie(accessCookie("garbage")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.loggedIn").value(false));
        }

        @Test
        @DisplayName("공개 경로에서도 로그인했으면 findUserId()로 유저를 알 수 있다")
        void publicWithToken() throws Exception {
            mockMvc
                    .perform(get(PUBLIC_URL).cookie(accessCookie(issue(1L, Role.USER))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.loggedIn").value(true));
        }

        @Test
        @DisplayName("/auth/** 는 토큰이 없으면 401 + 공통 응답 형식")
        void authWithoutToken() throws Exception {
            expectError(mockMvc.perform(get(AUTH_URL)), 401, "로그인이 필요합니다.");
        }

        @Test
        @DisplayName("/auth/** 는 USER·ADMIN 모두 200, getUserId()는 토큰의 유저")
        void authWithToken() throws Exception {
            mockMvc
                    .perform(get(AUTH_URL).cookie(accessCookie(issue(10L, Role.USER))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").value(false))
                    .andExpect(jsonPath("$.data").value(10));
            mockMvc.perform(get(AUTH_URL).cookie(accessCookie(issue(11L, Role.ADMIN)))).andExpect(status().isOk());
        }

        @Test
        @DisplayName("/admin/** 는 토큰이 없으면 401, USER면 403, ADMIN이면 200")
        void admin() throws Exception {
            expectError(mockMvc.perform(get(ADMIN_URL)), 401, "로그인이 필요합니다.");
            expectError(
                    mockMvc.perform(get(ADMIN_URL).cookie(accessCookie(issue(1L, Role.USER)))),
                    403,
                    "접근 권한이 없습니다."
            );
            mockMvc
                    .perform(get(ADMIN_URL).cookie(accessCookie(issue(2L, Role.ADMIN))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value("pong"));
        }

        @Test
        @DisplayName("만료된 토큰은 401 '만료된 토큰입니다.'")
        void expiredToken() throws Exception {
            Instant past = Instant.now().minus(SecurityPropertiesFixture.ACCESS_EXPIRATION).minusSeconds(60);
            String expired = new JwtProvider(securityProperties, Clock.fixed(past, ZoneOffset.UTC))
                    .createAccessToken(1L, Role.USER)
                    .value();

            expectError(
                    mockMvc.perform(get(AUTH_URL).cookie(accessCookie(expired))),
                    401,
                    ExpiredTokenException.MESSAGE
            );
        }

        @Test
        @DisplayName("위조된 토큰, refresh 토큰을 access로 쓴 경우는 401 '유효하지 않은 토큰입니다.'")
        void forgedToken() throws Exception {
            String forged = new JwtProvider(SecurityPropertiesFixture.create(), Clock.systemUTC())
                    .createAccessToken(1L, Role.ADMIN)
                    .value();
            String refresh = tokenService.issue(1L, Role.USER).refreshToken().value();

            expectError(
                    mockMvc.perform(get(ADMIN_URL).cookie(accessCookie(forged))),
                    401,
                    InvalidTokenException.MESSAGE
            );
            expectError(
                    mockMvc.perform(get(AUTH_URL).cookie(accessCookie(refresh))),
                    401,
                    InvalidTokenException.MESSAGE
            );
        }
    }

    @Nested
    @DisplayName("없는 경로")
    class NotFound {

        @Test
        @DisplayName("없는 공개 경로는 404 + 공통 응답 형식 (soft 404 방지)")
        void unknownPublicPath() throws Exception {
            expectError(mockMvc.perform(get("/no-such-path")), 404, "요청한 리소스를 찾을 수 없습니다.");
        }

        @Test
        @DisplayName("로그인한 상태로 없는 /auth/** 경로를 요청해도 404 + 공통 응답 형식")
        void unknownAuthPath() throws Exception {
            expectError(
                    mockMvc.perform(get("/auth/no-such-path").cookie(accessCookie(issue(1L, Role.USER)))),
                    404,
                    "요청한 리소스를 찾을 수 없습니다."
            );
        }
    }

    @Nested
    @DisplayName("로그아웃·재발급")
    class Revocation {

        @Test
        @DisplayName("로그아웃한 access 토큰은 만료 전이어도 401")
        void blacklist() throws Exception {
            TokenPair tokens = tokenService.issue(20L, Role.USER);
            String access = tokens.accessToken().value();
            mockMvc.perform(get(AUTH_URL).cookie(accessCookie(access))).andExpect(status().isOk());

            tokenService.revoke(access, tokens.refreshToken().value());

            expectError(
                    mockMvc.perform(get(AUTH_URL).cookie(accessCookie(access))),
                    401,
                    InvalidTokenException.MESSAGE
            );
            assertThatThrownBy(() -> tokenService.reissue(tokens.refreshToken().value()))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        @DisplayName("재발급하면 새 토큰 쌍이 나오고, 쓴 refresh 토큰은 다시 쓸 수 없다(회전)")
        void rotation() throws Exception {
            TokenPair first = tokenService.issue(30L, Role.ADMIN);

            TokenPair second = tokenService.reissue(first.refreshToken().value());

            assertThat(second.refreshToken().jti()).isNotEqualTo(first.refreshToken().jti());
            assertThat(second.accessToken().jti()).isNotEqualTo(first.accessToken().jti());
            mockMvc
                    .perform(get(ADMIN_URL).cookie(accessCookie(second.accessToken().value())))
                    .andExpect(status().isOk());

            assertThatThrownBy(() -> tokenService.reissue(first.refreshToken().value()))
                    .isInstanceOf(InvalidTokenException.class);
            assertThat(tokenService.reissue(second.refreshToken().value())).isNotNull();
        }

        @Test
        @DisplayName("만료·위조된 토큰으로 로그아웃해도 예외 없이 무시한다")
        void revokeInvalid() {
            tokenService.revoke("garbage", "garbage");
            tokenService.revoke(null, null);
        }
    }

    @Nested
    @DisplayName("응답 헤더")
    class Headers {

        @Test
        @DisplayName("보안 헤더가 붙는다 (HSTS는 HTTPS 요청에만)")
        void securityHeaders() throws Exception {
            mockMvc
                    .perform(get(PUBLIC_URL).secure(true))
                    .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                    .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                    .andExpect(header().exists("Permissions-Policy"));
        }

        @Test
        @DisplayName("CORS: 허용 origin은 credentials와 함께 허용, 그 외 origin은 403")
        void cors() throws Exception {
            String allowed = securityProperties.cors().origins().get(0);

            mockMvc
                    .perform(
                            options(AUTH_URL)
                                    .header(HttpHeaders.ORIGIN, allowed)
                                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                    )
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowed))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

            mockMvc
                    .perform(
                            options(AUTH_URL)
                                    .header(HttpHeaders.ORIGIN, "https://evil.example")
                                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                    )
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }
    }

    private String issue(long userId, Role role) {
        return tokenService.issue(userId, role).accessToken().value();
    }

    private static Cookie accessCookie(String value) {
        return new Cookie(AuthCookieManager.ACCESS_TOKEN, value);
    }

    private static void expectError(ResultActions result, int status, String message) throws Exception {
        result
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.error").value(true))
                .andExpect(jsonPath("$.message").value(message));
    }
}
