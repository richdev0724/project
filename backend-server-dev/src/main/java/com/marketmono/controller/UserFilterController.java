package com.marketmono.controller;

import com.marketmono.config.security.UserPrincipal;
import com.marketmono.dto.CreateUserFilterRequest;
import com.marketmono.dto.UpdateUserFilterRequest;
import com.marketmono.dto.UserFilterDTO;
import com.marketmono.service.UserFilterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/userfilter")
public class UserFilterController {
    
    private static final Logger log = LoggerFactory.getLogger(UserFilterController.class);
    
    private final UserFilterService userFilterService;
    
    public UserFilterController(UserFilterService userFilterService) {
        this.userFilterService = userFilterService;
    }
    
    @GetMapping
    public ResponseEntity<List<UserFilterDTO>> getAllFilters(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(name = "filter_type", required = false) String filterType
    ) {
        var userId = principal.getUser().getId();
        log.debug("GET /api/v1/userfilter - Retrieving {} filters for user {}", filterType != null ? filterType : "default", userId);
        List<UserFilterDTO> filters = userFilterService.getAllFilters(userId, filterType);
        return ResponseEntity.ok(filters);
    }
    
    @PostMapping
    public ResponseEntity<UserFilterDTO> createFilter(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestBody CreateUserFilterRequest request
    ) {
        var userId = principal.getUser().getId();
        log.debug("POST /api/v1/userfilter - Creating filter '{}' for user {}", request.name(), userId);
        UserFilterDTO created = userFilterService.createFilter(userId, request);
        return ResponseEntity.ok(created);
    }
    
    @PatchMapping("/{id}")
    public ResponseEntity<UserFilterDTO> updateFilter(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long id,
        @RequestBody UpdateUserFilterRequest request
    ) {
        var userId = principal.getUser().getId();
        log.debug("PATCH /api/v1/userfilter/{} - Updating filter for user {}", id, userId);
        UserFilterDTO updated = userFilterService.updateFilter(userId, id, request);
        return ResponseEntity.ok(updated);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFilter(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long id
    ) {
        var userId = principal.getUser().getId();
        log.debug("DELETE /api/v1/userfilter/{} - Deleting filter for user {}", id, userId);
        userFilterService.deleteFilter(userId, id);
        return ResponseEntity.noContent().build();
    }
}
