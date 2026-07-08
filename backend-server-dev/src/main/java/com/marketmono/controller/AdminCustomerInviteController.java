package com.marketmono.controller;

import com.marketmono.dto.admin.CustomerInviteActionRequest;
import com.marketmono.dto.admin.CustomerInviteCreateRequest;
import com.marketmono.dto.admin.CustomerInviteDto;
import com.marketmono.service.auth.CustomerInviteService;
import com.marketmono.util.PrincipalUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/customer-invites")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL_ADMIN')")
public class AdminCustomerInviteController {

    private final CustomerInviteService customerInviteService;

    @GetMapping
    public ResponseEntity<List<CustomerInviteDto>> listInvites() {
        return ResponseEntity.ok(customerInviteService.listInvites());
    }

    @PostMapping
    public ResponseEntity<CustomerInviteDto> createInvite(
            @Valid @RequestBody CustomerInviteCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(customerInviteService.createInvite(request, actorId(userDetails)));
    }

    @PostMapping("/{userId}/revoke")
    public ResponseEntity<CustomerInviteDto> revokeInvite(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(customerInviteService.revokeInvite(userId, actorId(userDetails)));
    }

    @PostMapping("/{userId}/extend-ttl")
    public ResponseEntity<CustomerInviteDto> extendInviteTtl(
            @PathVariable UUID userId,
            @Valid @RequestBody CustomerInviteActionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(customerInviteService.extendInviteTtl(userId, request, actorId(userDetails)));
    }

    @PostMapping("/{userId}/regenerate")
    public ResponseEntity<CustomerInviteDto> regenerateInvite(
            @PathVariable UUID userId,
            @Valid @RequestBody CustomerInviteActionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(customerInviteService.regenerateInvite(userId, request, actorId(userDetails)));
    }

    private UUID actorId(UserDetails userDetails) {
        return PrincipalUtils.requireInternalAdminActorId(userDetails);
    }
}
