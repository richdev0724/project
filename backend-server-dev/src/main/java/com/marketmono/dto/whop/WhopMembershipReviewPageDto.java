package com.marketmono.dto.whop;

import org.springframework.data.domain.Page;

import java.util.List;

public record WhopMembershipReviewPageDto(
    List<WhopMembershipReviewItemDto> content,
    long totalElements,
    int totalPages,
    int page,
    int size
) {
    public static WhopMembershipReviewPageDto from(Page<WhopMembershipReviewItemDto> source) {
        return new WhopMembershipReviewPageDto(
            source.getContent(),
            source.getTotalElements(),
            source.getTotalPages(),
            source.getNumber(),
            source.getSize()
        );
    }
}
