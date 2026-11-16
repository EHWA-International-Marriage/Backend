package com.ewha.backend._core.dto;

import java.util.List;

import lombok.Getter;

@Getter
public class ListResult<T> extends CommonResult {

    private final List<T> data;

    public ListResult(String message, List<T> data) {
        super(false, message);
        this.data = data;
    }
}
