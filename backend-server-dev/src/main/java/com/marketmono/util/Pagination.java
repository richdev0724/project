package com.marketmono.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class Pagination {

    private Pagination() {
    }

    public static PageRequest cappedPageRequest(int page, int pageSize, int maxPageSize, Sort sort) {
        int clampedSize = Math.min(Math.max(1, pageSize), maxPageSize);
        return PageRequest.of(pageIndex(page), clampedSize, sort);
    }

    public static PageRequest pageRequest(int page, int pageSize, Sort sort) {
        return PageRequest.of(pageIndex(page), pageSize, sort);
    }

    private static int pageIndex(int page) {
        return Math.max(0, page - 1);
    }
}
