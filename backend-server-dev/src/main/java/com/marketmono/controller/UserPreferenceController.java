package com.marketmono.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.marketmono.config.security.UserPrincipal;
import com.marketmono.dto.UserPreferenceDTO;
import com.marketmono.entity.UserPreferenceCategory;
import com.marketmono.service.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user-preferences")
@RequiredArgsConstructor
@Slf4j
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;

    @GetMapping
    public ResponseEntity<List<UserPreferenceDTO>> getPreferences(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        var userId = principal.getUser().getId();
        log.debug("GET /api/v1/user-preferences - Retrieving preferences for user {}", userId);
        return ResponseEntity.ok(userPreferenceService.getPreferences(userId));
    }

    @PutMapping("/{category}")
    public ResponseEntity<UserPreferenceDTO> upsertPreference(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UserPreferenceCategory category,
        @RequestBody(required = false) JsonNode preferences
    ) {
        var userId = principal.getUser().getId();
        log.debug("PUT /api/v1/user-preferences/{} - Updating preferences for user {}", category, userId);
        return ResponseEntity.ok(userPreferenceService.upsertPreference(userId, category, preferences));
    }
}
