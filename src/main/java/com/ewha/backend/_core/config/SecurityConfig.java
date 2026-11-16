package com.ewha.backend._core.config;

import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.ewha.backend._core.config.properties.SecurityProperties;
import com.ewha.backend._core.infrastructure.filter.JwtAuthenticationFilter;
import com.ewha.backend._core.infrastructure.handler.JwtAccessDeniedHandler;
import com.ewha.backend._core.infrastructure.handler.JwtAuthenticationEntryPoint;
import com.ewha.backend._core.infrastructure.servlet.AuthCookieManager;
import com.ewha.backend.api.auth.service.JwtProvider;
import com.ewha.backend.entity.auth.repository.AccessTokenBlacklistRepository;
import com.ewha.backend.entity.user.Role;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    private static final long HSTS_MAX_AGE_SECONDS = 31_536_000L; // 1년

    private final SecurityProperties             securityProperties;
    private final JwtProvider                    jwtProvider;
    private final AccessTokenBlacklistRepository blacklistRepository;
    private final AuthCookieManager              authCookieManager;
    private final JwtAuthenticationEntryPoint    authenticationEntryPoint;
    private final JwtAccessDeniedHandler         accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // 쿠키는 SameSite + CORS(허용 origin만)로 보호한다. CSRF 토큰은 쓰지 않는다.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(
                        headers -> headers
                                .contentTypeOptions(Customizer.withDefaults())
                                .frameOptions(frame -> frame.deny())
                                .httpStrictTransportSecurity(
                                        hsts -> hsts
                                                .includeSubDomains(true)
                                                .maxAgeInSeconds(HSTS_MAX_AGE_SECONDS)
                                )
                                .referrerPolicy(
                                        referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                                )
                                // JSON API 서버이므로 문서 로드·프레임 삽입을 모두 막는다
                                .contentSecurityPolicy(
                                        csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")
                                )
                                .addHeaderWriter(
                                        new StaticHeadersWriter(
                                                "Permissions-Policy",
                                                "camera=(), microphone=(), geolocation=()"
                                        )
                                )
                )
                .authorizeHttpRequests(
                        auth -> auth
                                .requestMatchers(HttpMethod.OPTIONS, "/**")
                                .permitAll()
                                .requestMatchers("/admin/**")
                                .hasRole(Role.ADMIN.name())
                                .requestMatchers("/auth/**")
                                .hasAnyRole(Role.USER.name(), Role.ADMIN.name())
                                .anyRequest()
                                .permitAll()
                )
                .exceptionHandling(
                        exception -> exception
                                .authenticationEntryPoint(authenticationEntryPoint)
                                .accessDeniedHandler(accessDeniedHandler)
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtProvider, blacklistRepository, authCookieManager),
                        AnonymousAuthenticationFilter.class
                )
                .build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(securityProperties.cors().origins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
