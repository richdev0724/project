package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.marketmono.entity.UserFilterType;

import java.time.Instant;

public record UserFilterDTO(
    Long id,
    String name,
    @JsonProperty("filter_type") UserFilterType filterType,
    @JsonProperty("filter_query") String filterQuery,
    @JsonProperty("updated_at") Instant updatedAt
) {}
