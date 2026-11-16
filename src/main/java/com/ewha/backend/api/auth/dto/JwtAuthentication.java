package com.ewha.backend.api.auth.dto;

import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ewha.backend._core.exception.UnauthorizedException;
import com.ewha.backend.entity.user.Role;

/**
 * JWT로 인증된 사용자. 유저 ID는 반드시 {@link #getUserId()} / {@link #findUserId()}로만 얻는다.
 */
public class JwtAuthentication extends AbstractAuthenticationToken {

    private final long userId;
    private final Role role;

    private JwtAuthentication(long userId, Role role) {
        super(List.of(new SimpleGrantedAuthority(role.authority())));
        this.userId = userId;
        this.role = role;
        setAuthenticated(true);
    }

    public static JwtAuthentication from(AccessTokenClaims claims) {
        return new JwtAuthentication(claims.userId(), claims.role());
    }

    /**
     * 로그인한 유저 ID. 로그인하지 않았으면 401.
     */
    public static long getUserId() {
        return findUserId().orElseThrow(UnauthorizedException::new);
    }

    /**
     * 로그인한 유저 ID. 로그인하지 않았으면 empty (공개 API에서 "내 글" 표시 등에 사용).
     */
    public static Optional<Long> findUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthentication jwtAuthentication) {
            return Optional.of(jwtAuthentication.userId);
        }
        return Optional.empty();
    }

    public Role getRole() {
        return role;
    }

    @Override
    public Long getPrincipal() {
        return userId;
    }

    @Override
    public Object getCredentials() {
        return null; // 토큰을 인증 객체에 보관하지 않는다
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JwtAuthentication that && userId == that.userId && role == that.role;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(userId) * 31 + role.hashCode();
    }
}
