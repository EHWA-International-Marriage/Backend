package com.ewha.backend._core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 생성·수정 시각 자동 기록. 작성자(@CreatedBy) 감사는 인증 구현 후 AuditorAware를 추가한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
