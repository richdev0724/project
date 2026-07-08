package com.marketmono.service.auth;

import com.marketmono.dto.waitlist.BetaWaitlistInviteRequest;
import com.marketmono.dto.waitlist.BetaWaitlistSelectionMode;
import com.marketmono.entity.auth.User;
import com.marketmono.entity.auth.UserBetaAccess;
import com.marketmono.entity.auth.UserRegistrationStatus;
import com.marketmono.repository.auth.UserRepository;
import com.marketmono.service.AdminAuditLogService;
import com.marketmono.service.StripeConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BetaWaitlistInviteServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-03T09:30:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    @Mock
    private StripeConfigService stripeConfigService;

    private BetaWaitlistInviteService service;

    @BeforeEach
    void setUp() {
        service = new BetaWaitlistInviteService(
            userRepository,
            emailService,
            adminAuditLogService,
            stripeConfigService,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void inviteSelectedWaitlistedUserSendsBetaInviteWithoutCreatingTemporaryPasswordAccount() {
        stubBetaConfig();
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        User waitlisted = waitlistedUser(userId, "lead@example.com");
        when(userRepository.findBetaWaitlistCandidatesByIdInForUpdate(List.of(userId)))
            .thenReturn(List.of(waitlisted));
        when(userRepository.save(waitlisted)).thenReturn(waitlisted);

        var response = service.invite(
            new BetaWaitlistInviteRequest(
                List.of(userId),
                BetaWaitlistSelectionMode.IDS,
                null,
                null,
                null,
                false,
                24
            ),
            actorId
        );

        assertThat(response.requestedCount()).isEqualTo(1);
        assertThat(response.invitedCount()).isEqualTo(1);
        assertThat(response.skippedCount()).isZero();
        assertThat(waitlisted.getRegistrationStatus()).isEqualTo(UserRegistrationStatus.WAITLISTED);
        assertThat(waitlisted.getPasswordHash()).isNull();
        assertThat(waitlisted.getEnabled()).isFalse();
        assertThat(waitlisted.getEmailVerified()).isFalse();
        assertThat(waitlisted.getRequiresPasswordChange()).isFalse();
        assertThat(waitlisted.getTempPasswordExpiresAt()).isNull();
        assertThat(waitlisted.getCustomerInviteStatus()).isNull();
        assertThat(waitlisted.getCustomerInvitedAt()).isNull();
        assertThat(waitlisted.getCustomerInviteLastSentAt()).isNull();
        assertThat(waitlisted.getBetaUser()).isTrue();
        UserBetaAccess betaAccess = waitlisted.getBetaAccess();
        assertThat(betaAccess).isNotNull();
        assertThat(betaAccess.getBetaSignedUpAt()).isNull();
        assertThat(betaAccess.getBetaInvitedAt()).isEqualTo(LocalDateTime.of(2026, 6, 3, 9, 30));
        assertThat(betaAccess.getBetaInviteLastSentAt()).isEqualTo(LocalDateTime.of(2026, 6, 3, 9, 30));
        assertThat(betaAccess.getBetaInvitedBy()).isEqualTo(actorId);
        assertThat(betaAccess.getBetaPromoCode()).isEqualTo("BETAMM2026");
        assertThat(response.invitedUsers().getFirst().betaInviteStatus())
            .isEqualTo(com.marketmono.dto.waitlist.BetaWaitlistInviteStatus.BETA_INVITE_SENT);

        verify(emailService).sendBetaWaitlistInviteEmail(
            eq(waitlisted),
            eq("BETAMM2026"),
            eq(3)
        );
        verify(adminAuditLogService).recordEvent(
            eq(actorId),
            eq("BETA_WAITLIST_INVITE_SENT"),
            eq("BETA_WAITLIST_INVITE"),
            eq(userId.toString()),
            any(Map.class)
        );
    }

    @Test
    void invitePreservesOriginalBetaSignupTimeWhenResending() {
        stubBetaConfig();
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        LocalDateTime originalSignedUpAt = LocalDateTime.of(2026, 6, 1, 8, 0);
        User betaUser = waitlistedUser(userId, "lead@example.com");
        betaUser.setRegistrationStatus(UserRegistrationStatus.REGISTERED);
        betaUser.setBetaUser(true);
        betaUser.setBetaAccess(UserBetaAccess.builder()
            .betaSignedUpAt(originalSignedUpAt)
            .betaInvitedAt(originalSignedUpAt)
            .build());
        when(userRepository.findBetaWaitlistCandidatesByIdInForUpdate(List.of(userId)))
            .thenReturn(List.of(betaUser));
        when(userRepository.save(betaUser)).thenReturn(betaUser);

        service.invite(
            new BetaWaitlistInviteRequest(
                List.of(userId),
                BetaWaitlistSelectionMode.IDS,
                null,
                null,
                null,
                false,
                null
            ),
            actorId
        );

        assertThat(betaUser.getBetaAccess().getBetaSignedUpAt()).isEqualTo(originalSignedUpAt);
        assertThat(betaUser.getBetaAccess().getBetaInvitedAt()).isEqualTo(originalSignedUpAt);
        assertThat(betaUser.getBetaAccess().getBetaInviteLastSentAt()).isEqualTo(LocalDateTime.of(2026, 6, 3, 9, 30));
    }

    @Test
    void inviteReturnsSkippedUsersForRequestedIdsThatAreNotEligible() {
        stubBetaConfig();
        UUID userId = UUID.randomUUID();
        when(userRepository.findBetaWaitlistCandidatesByIdInForUpdate(List.of(userId))).thenReturn(List.of());

        var response = service.invite(
            new BetaWaitlistInviteRequest(
                List.of(userId),
                BetaWaitlistSelectionMode.IDS,
                null,
                null,
                null,
                false,
                24
            ),
            UUID.randomUUID()
        );

        assertThat(response.requestedCount()).isEqualTo(1);
        assertThat(response.invitedCount()).isZero();
        assertThat(response.skippedUsers())
            .singleElement()
            .satisfies(skipped -> {
                assertThat(skipped.id()).isEqualTo(userId);
                assertThat(skipped.reason()).isEqualTo("NOT_ELIGIBLE");
            });
    }

    @Test
    void previewAllEligibleUsesServerSideEligiblePredicate() {
        User first = waitlistedUser(UUID.randomUUID(), "first@example.com");
        User second = waitlistedUser(UUID.randomUUID(), "second@example.com");
        when(userRepository.findEligibleBetaWaitlistInviteUsersForPreview())
            .thenReturn(List.of(first, second));

        var response = service.preview(BetaWaitlistSelectionMode.ALL_ELIGIBLE, List.of());

        assertThat(response.selectionMode()).isEqualTo(BetaWaitlistSelectionMode.ALL_ELIGIBLE);
        assertThat(response.eligibleCount()).isEqualTo(2);
        assertThat(response.sampleRecipients())
            .extracting("email")
            .containsExactly("first@example.com", "second@example.com");
    }

    @Test
    void previewSelectedIdsUsesReadOnlyCandidateLookup() {
        UUID userId = UUID.randomUUID();
        User selected = waitlistedUser(userId, "lead@example.com");
        when(userRepository.findBetaWaitlistCandidatesByIdInForPreview(List.of(userId)))
            .thenReturn(List.of(selected));

        var response = service.preview(BetaWaitlistSelectionMode.IDS, List.of(userId));

        assertThat(response.selectionMode()).isEqualTo(BetaWaitlistSelectionMode.IDS);
        assertThat(response.eligibleCount()).isEqualTo(1);
        assertThat(response.sampleRecipients())
            .extracting("email")
            .containsExactly("lead@example.com");
        verify(userRepository).findBetaWaitlistCandidatesByIdInForPreview(List.of(userId));
        verify(userRepository, never()).findBetaWaitlistCandidatesByIdInForUpdate(any());
    }

    @Test
    void responseDtosDoNotExposeTemporaryPasswords() {
        stubBetaConfig();
        UUID userId = UUID.randomUUID();
        User waitlisted = waitlistedUser(userId, "lead@example.com");
        when(userRepository.findBetaWaitlistCandidatesByIdInForUpdate(List.of(userId)))
            .thenReturn(List.of(waitlisted));
        when(userRepository.save(waitlisted)).thenReturn(waitlisted);

        var response = service.invite(
            new BetaWaitlistInviteRequest(
                List.of(userId),
                BetaWaitlistSelectionMode.IDS,
                null,
                null,
                null,
                false,
                24
            ),
            UUID.randomUUID()
        );

        assertThat(response.toString()).doesNotContain("hashed-temp-password");
        assertThat(response.toString()).doesNotContain("temporaryPassword");
        verify(emailService).sendBetaWaitlistInviteEmail(
            eq(waitlisted),
            eq("BETAMM2026"),
            eq(3)
        );
    }

    private static User waitlistedUser(UUID userId, String email) {
        User user = User.builder()
            .id(userId)
            .email(email)
            .displayName("Lead Trader")
            .registrationStatus(UserRegistrationStatus.WAITLISTED)
            .enabled(false)
            .emailVerified(false)
            .waitlistJoinedAt(LocalDateTime.of(2026, 5, 30, 12, 0))
            .waitlistSource("landing")
            .createdAt(LocalDateTime.of(2026, 5, 30, 11, 59))
            .build();
        user.setEmail(email);
        return user;
    }

    private void stubBetaConfig() {
        when(stripeConfigService.getValue(anyString(), anyString())).thenReturn("BETAMM2026");
        when(stripeConfigService.getValueAsInt(anyString(), eq(3))).thenReturn(3);
    }
}
