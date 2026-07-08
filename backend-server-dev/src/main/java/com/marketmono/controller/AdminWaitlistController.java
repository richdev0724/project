package com.marketmono.controller;

import com.marketmono.dto.waitlist.BulkDeleteWaitlistRequest;
import com.marketmono.dto.waitlist.BulkDeleteWaitlistResponse;
import com.marketmono.dto.waitlist.BetaWaitlistInvitePreviewRequest;
import com.marketmono.dto.waitlist.BetaWaitlistInvitePreviewResponse;
import com.marketmono.dto.waitlist.BetaWaitlistInviteRequest;
import com.marketmono.dto.waitlist.BetaWaitlistInviteResponse;
import com.marketmono.dto.waitlist.WaitlistedUserDto;
import com.marketmono.service.auth.BetaWaitlistInviteService;
import com.marketmono.service.auth.WaitlistService;
import com.marketmono.util.PrincipalUtils;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/admin/waitlist")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminWaitlistController {

    private final WaitlistService waitlistService;
    private final BetaWaitlistInviteService betaWaitlistInviteService;

    @PostMapping("/delete")
    public ResponseEntity<BulkDeleteWaitlistResponse> deleteWaitlistedUsers(
            @Valid @RequestBody BulkDeleteWaitlistRequest request) {
        return ResponseEntity.ok(waitlistService.deleteWaitlistedUsers(request.ids()));
    }

    @PostMapping("/{userId}/resend-confirmation")
    public ResponseEntity<WaitlistedUserDto> resendConfirmation(@PathVariable UUID userId) {
        return ResponseEntity.ok(waitlistService.resendWaitlistConfirmation(userId));
    }

    @PostMapping("/beta-invites/preview")
    public ResponseEntity<BetaWaitlistInvitePreviewResponse> previewBetaInvites(
            @Valid @RequestBody BetaWaitlistInvitePreviewRequest request) {
        return ResponseEntity.ok(betaWaitlistInviteService.preview(request.selectionMode(), request.ids()));
    }

    @PostMapping("/beta-invites")
    public ResponseEntity<BetaWaitlistInviteResponse> sendBetaInvites(
            @Valid @RequestBody BetaWaitlistInviteRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(betaWaitlistInviteService.invite(
            request,
            PrincipalUtils.requireInternalAdminActorId(userDetails)
        ));
    }
}
