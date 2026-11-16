package com.ewha.backend._core.infrastructure.filter;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ewha.backend._core.infrastructure.servlet.AuthCookieManager;
import com.ewha.backend.api.auth.dto.AccessTokenClaims;
import com.ewha.backend.api.auth.dto.JwtAuthentication;
import com.ewha.backend.api.auth.service.JwtProvider;
import com.ewha.backend.entity.auth.exception.ExpiredTokenException;
import com.ewha.backend.entity.auth.exception.InvalidTokenException;
import com.ewha.backend.entity.auth.repository.AccessTokenBlacklistRepository;

/**
 * access_token 쿠키가 유효하면 SecurityContext에 {@link JwtAuthentication}을 넣는다.
 * <p>
 * 토큰이 없거나 잘못돼도 여기서 막지 않는다. 공개 경로는 그대로 통과하고, 보호 경로는 인가 단계에서 401이 된다.
 * 실패 이유는 {@link #FAILURE_ATTRIBUTE}에 담아 EntryPoint가 응답 메시지를 고르게 한다.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String FAILURE_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".FAILURE";

    private final JwtProvider                    jwtProvider;
    private final AccessTokenBlacklistRepository blacklistRepository;
    private final AuthCookieManager              authCookieManager;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Optional<String> token = authCookieManager.findAccessToken(request);
        if (token.isPresent()) {
            authenticate(request, token.get());
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        try {
            AccessTokenClaims claims = jwtProvider.parseAccessToken(token);
            if (blacklistRepository.contains(claims.jti())) {
                request.setAttribute(FAILURE_ATTRIBUTE, Failure.INVALID);
                return;
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(JwtAuthentication.from(claims));
            SecurityContextHolder.setContext(context);
        } catch (ExpiredTokenException e) {
            request.setAttribute(FAILURE_ATTRIBUTE, Failure.EXPIRED);
        } catch (InvalidTokenException e) {
            request.setAttribute(FAILURE_ATTRIBUTE, Failure.INVALID);
        } catch (DataAccessException e) {
            // 블랙리스트를 확인할 수 없으면 인증하지 않는다(fail closed)
            log.error("[JWT] 블랙리스트 조회 실패: {}", e.getClass().getSimpleName());
            request.setAttribute(FAILURE_ATTRIBUTE, Failure.INVALID);
        }
    }

    public enum Failure {
        EXPIRED,
        INVALID
    }
}
