package com.ewha.backend._core.dto;

import java.util.List;

import lombok.Getter;

import org.springframework.data.domain.Page;

@Getter
public class PageResult<T> extends CommonResult {

    private final List<T>  data;
    private final PageInfo pageInfo;

    public PageResult(String message, Page<T> page) {
        super(false, message);
        this.data = page.getContent();
        this.pageInfo = PageInfo.from(page);
    }

    /**
     * @param page 0부터 시작하는 현재 페이지 번호
     */
    public record PageInfo(
            long totalElements,
            int totalPages,
            int page,
            int size,
            boolean hasPrevious,
            boolean hasNext
    ) {

        public static PageInfo from(Page<?> page) {
            return new PageInfo(
                    page.getTotalElements(),
                    page.getTotalPages(),
                    page.getNumber(),
                    page.getSize(),
                    page.hasPrevious(),
                    page.hasNext()
            );
        }
    }
}
