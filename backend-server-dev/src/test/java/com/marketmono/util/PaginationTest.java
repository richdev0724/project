package com.marketmono.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginationTest {

    @Test
    void cappedPageRequestNormalizesOneBasedPagesAndCapsPageSize() {
        PageRequest pageRequest = Pagination.cappedPageRequest(
            0,
            1000,
            500,
            Sort.by(Sort.Direction.DESC, "executedAt")
        );

        assertThat(pageRequest.getPageNumber()).isZero();
        assertThat(pageRequest.getPageSize()).isEqualTo(500);
        assertThat(pageRequest.getSort().getOrderFor("executedAt")).isNotNull();
    }

    @Test
    void cappedPageRequestKeepsMinimumPageSizeAtOne() {
        PageRequest pageRequest = Pagination.cappedPageRequest(
            2,
            -10,
            500,
            Sort.by(Sort.Direction.DESC, "executedAt")
        );

        assertThat(pageRequest.getPageNumber()).isEqualTo(1);
        assertThat(pageRequest.getPageSize()).isEqualTo(1);
    }

    @Test
    void uncappedPageRequestDoesNotCapFlowAlertPageSize() {
        PageRequest pageRequest = Pagination.pageRequest(
            2,
            1000,
            Sort.by(Sort.Direction.DESC, "tradeStartTime")
        );

        assertThat(pageRequest.getPageNumber()).isEqualTo(1);
        assertThat(pageRequest.getPageSize()).isEqualTo(1000);
        assertThat(pageRequest.getSort().getOrderFor("tradeStartTime")).isNotNull();
    }

    @Test
    void uncappedPageRequestPreservesInvalidPageSizeFailure() {
        assertThatThrownBy(() -> Pagination.pageRequest(
            1,
            0,
            Sort.by(Sort.Direction.DESC, "tradeStartTime")
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
