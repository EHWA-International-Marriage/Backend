package com.ewha.backend.support;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ewha.backend._core.dto.ApiResponse;
import com.ewha.backend._core.dto.SingleResult;
import com.ewha.backend.api.auth.dto.JwtAuthentication;

/**
 * 보안 통합 테스트 전용 컨트롤러. test 소스에만 있으므로 실제 애플리케이션에는 포함되지 않는다.
 */
@RestController
public class SecurityTestController {

    @GetMapping("/test/public")
    public ResponseEntity<SingleResult<Map<String, Object>>> publicApi() {
        return ApiResponse.ok(Map.of("loggedIn", JwtAuthentication.findUserId().isPresent()));
    }

    @GetMapping("/auth/test/me")
    public ResponseEntity<SingleResult<Long>> me() {
        return ApiResponse.ok(JwtAuthentication.getUserId());
    }

    @GetMapping("/admin/test/ping")
    public ResponseEntity<SingleResult<String>> admin() {
        return ApiResponse.ok("pong");
    }
}
