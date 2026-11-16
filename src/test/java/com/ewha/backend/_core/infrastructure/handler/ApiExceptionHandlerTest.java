package com.ewha.backend._core.infrastructure.handler;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ewha.backend._core.dto.ApiResponse;
import com.ewha.backend._core.dto.CommonResult;
import com.ewha.backend._core.dto.PageResult;
import com.ewha.backend._core.dto.SingleResult;
import com.ewha.backend._core.dto.response.CreateRes;
import com.ewha.backend._core.exception.ApiException;
import com.ewha.backend._core.exception.BadRequestException;
import com.ewha.backend._core.exception.DuplicateException;
import com.ewha.backend._core.exception.ForbiddenException;
import com.ewha.backend._core.exception.NotFoundException;
import com.ewha.backend._core.exception.UnauthorizedException;

class ApiExceptionHandlerTest {

    private static final String SECRET = "jdbc-password-1234";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("ApiException 하위 예외는 지정한 상태 코드와 메시지로 응답한다")
    void apiException() throws Exception {
        expectError("/test/bad-request", 400, "잘못된 값입니다.");
        expectError("/test/unauthorized", 401, "로그인이 필요합니다.");
        expectError("/test/forbidden", 403, "접근 권한이 없습니다.");
        expectError("/test/not-found", 404, "게시글을(를) 찾을 수 없습니다.");
        expectError("/test/duplicate", 409, "이미 존재하는 slug입니다.");
    }

    @Test
    @DisplayName("5xx ApiException도 같은 형식으로 응답한다")
    void apiException5xx() throws Exception {
        expectError("/test/api-5xx", 503, "잠시 후 다시 시도해 주세요.");
    }

    @Test
    @DisplayName("@Valid 검증 실패는 400과 '필드: 메시지'로 응답하고 입력값은 노출하지 않는다")
    void validation() throws Exception {
        mockMvc
                .perform(post("/test/valid").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(true))
                .andExpect(jsonPath("$.message").value("name: 이름은 필수입니다."));
    }

    @Test
    @DisplayName("JSON 파싱 실패는 400과 고정 메시지로 응답한다")
    void notReadable() throws Exception {
        mockMvc
                .perform(post("/test/valid").contentType(MediaType.APPLICATION_JSON).content("{\"name\": " + SECRET))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(true))
                .andExpect(jsonPath("$.message").value(ApiExceptionHandler.BAD_REQUEST_MESSAGE))
                .andExpect(content().string(not(containsString(SECRET))));
    }

    @Test
    @DisplayName("필수 파라미터 누락과 타입 불일치는 400으로 응답한다")
    void parameter() throws Exception {
        expectError("/test/param", 400, "page: 필수 값입니다.");
        expectError("/test/type/abc", 400, "id: 형식이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 405로 응답한다")
    void methodNotAllowed() throws Exception {
        mockMvc
                .perform(post("/test/bad-request"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value(true))
                .andExpect(jsonPath("$.message").value(ApiExceptionHandler.METHOD_NOT_ALLOWED_MESSAGE));
    }

    @Test
    @DisplayName("처리하지 못한 예외는 500과 고정 메시지로 응답하고 내부 메시지는 노출하지 않는다")
    void unexpected() throws Exception {
        mockMvc
                .perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value(true))
                .andExpect(jsonPath("$.message").value(ApiExceptionHandler.INTERNAL_SERVER_ERROR_MESSAGE))
                .andExpect(content().string(not(containsString(SECRET))));
    }

    @Test
    @DisplayName("성공 응답은 error=false와 data, 페이지 정보를 담는다")
    void successFormat() throws Exception {
        mockMvc
                .perform(post("/test/create"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.error").value(false))
                .andExpect(jsonPath("$.data.id").value(1));

        mockMvc
                .perform(get("/test/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(false))
                .andExpect(jsonPath("$.data[0]").value("a"))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(3))
                .andExpect(jsonPath("$.pageInfo.totalPages").value(2))
                .andExpect(jsonPath("$.pageInfo.page").value(0))
                .andExpect(jsonPath("$.pageInfo.hasNext").value(true));
    }

    private void expectError(String url, int status, String message) throws Exception {
        mockMvc
                .perform(get(url))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.error").value(true))
                .andExpect(jsonPath("$.message").value(message));
    }

    record TestReq(@NotBlank(message = "이름은 필수입니다.") String name) {
    }

    @RestController
    static class TestController {

        @GetMapping("/test/bad-request")
        void badRequest() {
            throw new BadRequestException("잘못된 값입니다.");
        }

        @GetMapping("/test/unauthorized")
        void unauthorized() {
            throw new UnauthorizedException();
        }

        @GetMapping("/test/forbidden")
        void forbidden() {
            throw new ForbiddenException();
        }

        @GetMapping("/test/not-found")
        void notFound() {
            throw new NotFoundException("게시글");
        }

        @GetMapping("/test/duplicate")
        void duplicate() {
            throw new DuplicateException("slug");
        }

        @GetMapping("/test/api-5xx")
        void api5xx() {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "잠시 후 다시 시도해 주세요.");
        }

        @PostMapping("/test/valid")
        ResponseEntity<CommonResult> valid(@Valid @RequestBody TestReq req) {
            return ApiResponse.ok();
        }

        @GetMapping("/test/param")
        ResponseEntity<CommonResult> param(@RequestParam int page) {
            return ApiResponse.ok();
        }

        @GetMapping("/test/type/{id}")
        ResponseEntity<CommonResult> type(@PathVariable Long id) {
            return ApiResponse.ok();
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException(SECRET);
        }

        @PostMapping("/test/create")
        ResponseEntity<SingleResult<CreateRes>> create() {
            return ApiResponse.create(1L);
        }

        @GetMapping("/test/page")
        ResponseEntity<PageResult<String>> page() {
            return ApiResponse.ok(new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 2), 3));
        }
    }
}
