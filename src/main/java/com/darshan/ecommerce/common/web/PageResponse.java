package com.darshan.ecommerce.common.web;

import org.springframework.data.domain.Page;

import java.util.List;

// A stable, documented page shape instead of Spring's Page JSON, whose structure is not an API contract.
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
