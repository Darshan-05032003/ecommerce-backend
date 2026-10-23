package com.darshan.ecommerce.common.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

// Query parameters page, size and sort, bound by constructor. Missing values fall back to the
// defaults here; values outside the limits are rejected by Bean Validation with a 400.
public record PageParams(@Min(0) Integer page,
                         @Min(1) @Max(PageParams.MAX_SIZE) Integer size,
                         String sort) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public Pageable toPageable(SortWhitelist sortWhitelist) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        return PageRequest.of(pageNumber, pageSize, sortWhitelist.parse(sort));
    }
}
