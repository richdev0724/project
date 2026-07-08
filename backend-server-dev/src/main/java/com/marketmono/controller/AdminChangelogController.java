package com.marketmono.controller;

import com.marketmono.dto.AdminChangelogEntryDto;
import com.marketmono.dto.CreateChangelogEntryRequest;
import com.marketmono.dto.UpdateChangelogEntryRequest;
import com.marketmono.service.ChangelogService;
import com.marketmono.util.HttpUtils;
import com.marketmono.util.PrincipalUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin CRUD controller for changelog entries.
 */
@RestController
@RequestMapping("/api/v1/admin/changelog")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminChangelogController {

    private final ChangelogService changelogService;

    @GetMapping
    public ResponseEntity<List<AdminChangelogEntryDto>> getEntries(
            @RequestParam(required = false, defaultValue = "ALL") String visibility) {
        log.debug("GET /api/v1/admin/changelog - visibility={}", visibility);
        return ResponseEntity.ok(changelogService.getAdminEntries(visibility));
    }

    @PostMapping
    public ResponseEntity<AdminChangelogEntryDto> createEntry(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateChangelogEntryRequest request,
            HttpServletRequest httpRequest) {
        UUID adminId = PrincipalUtils.requireChangelogActorId(userDetails);
        String ipAddress = HttpUtils.getClientIpWithRealIpFallback(httpRequest);

        log.info("POST /api/v1/admin/changelog - adminId: {}, releaseVersion: {}, ip: {}",
            adminId, request.releaseVersion(), ipAddress);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(changelogService.createEntry(request, adminId));
    }

    @PutMapping("/{entryId}")
    public ResponseEntity<AdminChangelogEntryDto> updateEntry(
            @PathVariable UUID entryId,
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateChangelogEntryRequest request,
            HttpServletRequest httpRequest) {
        UUID adminId = PrincipalUtils.requireChangelogActorId(userDetails);
        String ipAddress = HttpUtils.getClientIpWithRealIpFallback(httpRequest);

        log.info("PUT /api/v1/admin/changelog/{} - adminId: {}, releaseVersion: {}, ip: {}",
            entryId, adminId, request.releaseVersion(), ipAddress);

        return ResponseEntity.ok(changelogService.updateEntry(entryId, request, adminId));
    }

    @DeleteMapping("/{entryId}")
    public ResponseEntity<Void> deleteEntry(
            @PathVariable UUID entryId,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        UUID adminId = PrincipalUtils.requireChangelogActorId(userDetails);
        String ipAddress = HttpUtils.getClientIpWithRealIpFallback(httpRequest);

        log.info("DELETE /api/v1/admin/changelog/{} - adminId: {}, ip: {}", entryId, adminId, ipAddress);

        changelogService.deleteEntry(entryId);
        return ResponseEntity.noContent().build();
    }

}
