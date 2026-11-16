package com.ewha.backend.entity.auth.repository;

import java.time.Duration;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.ewha.backend.entity.user.Role;

/**
 * 유효한 refresh 토큰 목록. 토큰 원문은 저장하지 않고 jti만 키로 쓴다.
 * <p>
 * key = {@code refresh:{jti}}, value = {@code {userId}:{role}}, TTL = refresh 토큰 남은 수명
 */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh:";
    private static final String SEPARATOR  = ":";

    private final StringRedisTemplate redisTemplate;

    public void save(String jti, long userId, Role role, Duration ttl) {
        redisTemplate.opsForValue().set(KEY_PREFIX + jti, userId + SEPARATOR + role.name(), ttl);
    }

    /**
     * 꺼내는 동시에 삭제한다(GETDEL). 같은 refresh 토큰으로 동시에 재발급을 요청해도 한 번만 성공한다.
     */
    public Optional<Owner> consume(String jti) {
        return Optional.ofNullable(redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + jti)).map(Owner::parse);
    }

    public void delete(String jti) {
        redisTemplate.delete(KEY_PREFIX + jti);
    }

    public record Owner(
            long userId,
            Role role
    ) {

        private static Owner parse(String value) {
            String[] parts = value.split(SEPARATOR, 2);
            return new Owner(Long.parseLong(parts[0]), Role.valueOf(parts[1]));
        }
    }
}
