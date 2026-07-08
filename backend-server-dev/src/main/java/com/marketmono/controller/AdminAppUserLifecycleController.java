package com.marketmono.controller;

import com.marketmono.dto.admin.AdminAppUserHardDeleteChallengeResponse;
import com.marketmono.dto.admin.AdminAppUserLifecycleResponse;
import com.marketmono.service.auth.AdminAppUserLifecycleService;
import com.marketmono.util.PrincipalUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/app-users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL_ADMIN')")
public class AdminAppUserLifecycleController {

    private final AdminAppUserLifecycleService lifecycleService;

    @PostMapping("/{userId}/soft-delete")
    public ResponseEntity<AdminAppUserLifecycleResponse> softDeleteUser(
            @PathVariable UUID userId,
            @RequestBody(required = false) LifecycleRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(lifecycleService.softDeleteUser(
            userId,
            PrincipalUtils.requireInternalAdminActorId(userDetails),
            request != null ? request.reason() : null
        ));
    }

    @PostMapping("/{userId}/restore")
    public ResponseEntity<AdminAppUserLifecycleResponse> restoreUser(
            @PathVariable UUID userId,
            @RequestBody(required = false) LifecycleRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(lifecycleService.restoreUser(
            userId,
            PrincipalUtils.requireInternalAdminActorId(userDetails),
            request != null ? request.reason() : null
        ));
    }

    @PostMapping("/{userId}/ban-email")
    public ResponseEntity<AdminAppUserLifecycleResponse> banEmail(
            @PathVariable UUID userId,
            @RequestBody(required = false) LifecycleRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(lifecycleService.banEmail(
            userId,
            PrincipalUtils.requireInternalAdminActorId(userDetails),
            request != null ? request.reason() : null
        ));
    }

    @PostMapping("/{userId}/unban-email")
    public ResponseEntity<AdminAppUserLifecycleResponse> unbanEmail(
            @PathVariable UUID userId,
            @RequestBody(required = false) LifecycleRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(lifecycleService.unbanEmail(
            userId,
            PrincipalUtils.requireInternalAdminActorId(userDetails),
            request != null ? request.reason() : null
        ));
    }

    @PostMapping("/{userId}/hard-delete-challenge")
    public ResponseEntity<AdminAppUserHardDeleteChallengeResponse> createHardDeleteChallenge(
            @PathVariable UUID userId,
            @RequestBody(required = false) LifecycleRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(lifecycleService.createHardDeleteChallenge(
            userId,
            PrincipalUtils.requireInternalAdminActorId(userDetails),
            request != null ? request.reason() : null
        ));
    }

    @PostMapping("/{userId}/hard-delete")
    public ResponseEntity<AdminAppUserLifecycleResponse> hardDeleteUser(
            @PathVariable UUID userId,
            @RequestBody HardDeleteRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(lifecycleService.hardDeleteUser(
            userId,
            PrincipalUtils.requireInternalAdminActorId(userDetails),
            request.challengeId(),
            request.challengeAnswer(),
            request.reason()
        ));
    }

    public record LifecycleRequest(String reason) {}

    public record HardDeleteRequest(UUID challengeId, String challengeAnswer, String reason) {}
}
