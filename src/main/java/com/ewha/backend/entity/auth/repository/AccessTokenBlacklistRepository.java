package com.ewha.backend.entity.auth.repository;

import java.time.Duration;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 로그아웃한 access 토큰. 토큰 원문 대신 jti를 저장하고, 토큰이 만료되면 Redis에서도 사라진다.
 */
@Repository
@RequiredArgsConstructor
public class AccessTokenBlacklistRepository {

    private static final String KEY_PREFIX = "blacklist:";

    private final StringRedisTemplate redisTemplate;

    public void add(String jti, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            return; // 이미 만료된 토큰은 막을 필요가 없다
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
    }

    public boolean contains(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    }
}
