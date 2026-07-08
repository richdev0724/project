package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateUserFilterRequest(
    @JsonProperty("filter_query") String filterQuery,
    @JsonProperty("filter_type") String filterType
) {}
