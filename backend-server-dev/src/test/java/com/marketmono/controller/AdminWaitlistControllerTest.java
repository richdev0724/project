package com.marketmono.controller;

import com.marketmono.config.security.InternalAdminPrincipal;
import com.marketmono.dto.waitlist.BulkDeleteWaitlistRequest;
import com.marketmono.dto.waitlist.BulkDeleteWaitlistResponse;
import com.marketmono.dto.waitlist.BetaWaitlistInviteRequest;
import com.marketmono.dto.waitlist.BetaWaitlistInvitePreviewRequest;
import com.marketmono.dto.waitlist.BetaWaitlistInvitePreviewResponse;
import com.marketmono.dto.waitlist.BetaWaitlistInviteResponse;
import com.marketmono.dto.waitlist.BetaWaitlistSelectionMode;
import com.marketmono.dto.waitlist.WaitlistedUserDto;
import com.marketmono.entity.auth.UserRegistrationStatus;
import com.marketmono.service.auth.BetaWaitlistInviteService;
import com.marketmono.service.auth.WaitlistService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminWaitlistControllerTest {

    @Mock
    private WaitlistService waitlistService;

    @Mock
    private BetaWaitlistInviteService betaWaitlistInviteService;

    @InjectMocks
    private AdminWaitlistController adminWaitlistController;

    @Test
    void shouldBulkDeleteWaitlistedUsers() {
        UUID userId = UUID.randomUUID();
        BulkDeleteWaitlistRequest request = new BulkDeleteWaitlistRequest(List.of(userId));
        BulkDeleteWaitlistResponse serviceResponse = new BulkDeleteWaitlistResponse(
            1,
            List.of(waitlistedUser(userId))
        );
        when(waitlistService.deleteWaitlistedUsers(request.ids())).thenReturn(serviceResponse);

        ResponseEntity<BulkDeleteWaitlistResponse> response = adminWaitlistController.deleteWaitlistedUsers(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(serviceResponse);
        verify(waitlistService).deleteWaitlistedUsers(List.of(userId));
    }

    @Test
    void shouldResendWaitlistConfirmation() {
        UUID userId = UUID.randomUUID();
        WaitlistedUserDto serviceResponse = waitlistedUser(userId);
        when(waitlistService.resendWaitlistConfirmation(userId)).thenReturn(serviceResponse);

        ResponseEntity<WaitlistedUserDto> response = adminWaitlistController.resendConfirmation(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(serviceResponse);
        verify(waitlistService).resendWaitlistConfirmation(userId);
    }

    @Test
    void shouldSendBetaWaitlistInvites() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        BetaWaitlistInviteRequest request = new BetaWaitlistInviteRequest(
            List.of(userId),
            BetaWaitlistSelectionMode.IDS,
            null,
            null,
            null,
            false,
            24
        );
        BetaWaitlistInviteResponse serviceResponse = new BetaWaitlistInviteResponse(
            1,
            1,
            0,
            List.of(waitlistedUser(userId)),
            List.of()
        );
        when(betaWaitlistInviteService.invite(request, actorId)).thenReturn(serviceResponse);

        ResponseEntity<BetaWaitlistInviteResponse> response =
            adminWaitlistController.sendBetaInvites(request, new InternalAdminPrincipal(actorId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(serviceResponse);
        verify(betaWaitlistInviteService).invite(request, actorId);
    }

    @Test
    void shouldPreviewAllEligibleBetaWaitlistInvites() {
        BetaWaitlistInvitePreviewResponse serviceResponse = new BetaWaitlistInvitePreviewResponse(
            BetaWaitlistSelectionMode.ALL_ELIGIBLE,
            2,
            List.of(waitlistedUser(UUID.randomUUID()), waitlistedUser(UUID.randomUUID())),
            0,
            List.of()
        );
        when(betaWaitlistInviteService.preview(BetaWaitlistSelectionMode.ALL_ELIGIBLE, List.of()))
            .thenReturn(serviceResponse);

        var response = adminWaitlistController.previewBetaInvites(
            new BetaWaitlistInvitePreviewRequest(List.of(), BetaWaitlistSelectionMode.ALL_ELIGIBLE)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(serviceResponse);
        verify(betaWaitlistInviteService).preview(BetaWaitlistSelectionMode.ALL_ELIGIBLE, List.of());
    }

    private WaitlistedUserDto waitlistedUser(UUID userId) {
        return new WaitlistedUserDto(
            userId,
            "lead@example.com",
            "Lead Trader",
            UserRegistrationStatus.WAITLISTED,
            LocalDateTime.of(2026, 5, 10, 9, 0),
            "landing",
            LocalDateTime.of(2026, 5, 10, 9, 1),
            LocalDateTime.of(2026, 5, 10, 8, 59)
        );
    }
}
