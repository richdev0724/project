package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.marketmono.entity.UserPreferenceCategory;

import java.time.Instant;

public record UserPreferenceDTO(
    UserPreferenceCategory category,
    JsonNode preferences,
    @JsonProperty("updated_at") Instant updatedAt
) {}
