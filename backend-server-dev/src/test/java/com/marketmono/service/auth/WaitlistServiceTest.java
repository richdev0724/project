package com.marketmono.service.auth;

import com.marketmono.dto.waitlist.WaitlistRequest;
import com.marketmono.dto.waitlist.WaitlistResponse;
import com.marketmono.entity.auth.User;
import com.marketmono.entity.auth.UserRegistrationStatus;
import com.marketmono.repository.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailValidationService emailValidationService;

    @Mock
    private EmailService emailService;

    @Mock
    private RegistrationModeService registrationModeService;

    @InjectMocks
    private WaitlistService waitlistService;

    @Test
    void shouldCreateWaitlistedUserAndTrackConfirmationEmailTimestamp() {
        when(userRepository.findByEmailForUpdate("Lead@Example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        WaitlistResponse response = waitlistService.joinWaitlist(
            new WaitlistRequest("  Alpha   Trader  ", " Lead@Example.com ")
        );

        assertThat(response).isEqualTo(WaitlistResponse.accepted());
        verify(emailValidationService).validateEmail("Lead@Example.com");
        verify(emailService).sendWaitlistConfirmationEmail(any(User.class));
        verify(userRepository).findByEmailForUpdate("Lead@Example.com");
        verify(userRepository, times(2)).save(userCaptor.capture());

        List<User> savedUsers = userCaptor.getAllValues();
        User saved = savedUsers.getLast();
        assertThat(saved.getEmail()).isEqualTo("Lead@Example.com");
        assertThat(saved.getEmailLowercase()).isEqualTo("lead@example.com");
        assertThat(saved.getDisplayName()).isEqualTo("Alpha Trader");
        assertThat(saved.getRegistrationStatus()).isEqualTo(UserRegistrationStatus.WAITLISTED);
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.getEnabled()).isFalse();
        assertThat(saved.getEmailVerified()).isFalse();
        assertThat(saved.getWaitlistJoinedAt()).isNotNull();
        assertThat(saved.getWaitlistSource()).isEqualTo("landing");
        assertThat(saved.getWaitlistConfirmationEmailSentAt()).isNotNull();
        verify(registrationModeService).requireWaitlistMode();
    }

    @Test
    void shouldKeepWaitlistSignupAcceptedWhenConfirmationEmailFails() {
        when(userRepository.findByEmailForUpdate("lead@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("email provider unavailable"))
            .when(emailService)
            .sendWaitlistConfirmationEmail(any(User.class));
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        WaitlistResponse response = waitlistService.joinWaitlist(
            new WaitlistRequest("Lead", "lead@example.com")
        );

        assertThat(response).isEqualTo(WaitlistResponse.accepted());
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getWaitlistConfirmationEmailSentAt()).isNull();
    }

    @Test
    void shouldRejectWaitlistSubmissionWhenWaitlistModeIsDisabled() {
        doThrow(new RegistrationModeService.RegistrationModeUnavailableException("Waitlist is currently closed"))
            .when(registrationModeService)
            .requireWaitlistMode();

        assertThatThrownBy(() -> waitlistService.joinWaitlist(
            new WaitlistRequest("Lead", "lead@example.com")
        ))
            .isInstanceOf(RegistrationModeService.RegistrationModeUnavailableException.class)
            .hasMessage("Waitlist is currently closed");

        verify(emailValidationService, never()).validateEmail(any());
        verify(userRepository, never()).findByEmailForUpdate(any());
        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendWaitlistConfirmationEmail(any(User.class));
    }

    @Test
    void shouldRejectWaitlistSubmissionForBannedEmailWithGenericSupportMessage() {
        when(userRepository.existsBannedByEmailLowercase("blocked@example.com")).thenReturn(true);

        assertThatThrownBy(() -> waitlistService.joinWaitlist(
            new WaitlistRequest("Blocked", " Blocked@Example.com ")
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(AuthFailureMessages.BANNED_EMAIL_SUPPORT_MESSAGE);

        verify(userRepository, never()).findByEmailForUpdate(any());
        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendWaitlistConfirmationEmail(any(User.class));
    }

    @Test
    void shouldHandleDuplicateWaitlistedSubmissionIdempotentlyWithoutResendingEmailWhenConfirmationWasSent() {
        User existing = User.builder()
            .email("lead@example.com")
            .displayName("Old Name")
            .registrationStatus(UserRegistrationStatus.WAITLISTED)
            .enabled(false)
            .emailVerified(false)
            .waitlistConfirmationEmailSentAt(LocalDateTime.now().minusDays(1))
            .build();
        existing.setEmail("lead@example.com");

        when(userRepository.findByEmailForUpdate("lead@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WaitlistResponse response = waitlistService.joinWaitlist(
            new WaitlistRequest("New Name", "lead@example.com")
        );

        assertThat(response).isEqualTo(WaitlistResponse.accepted());
        assertThat(existing.getDisplayName()).isEqualTo("New Name");
        assertThat(existing.getWaitlistSource()).isEqualTo("landing");
        verify(emailService, never()).sendWaitlistConfirmationEmail(any(User.class));
        verify(userRepository).save(existing);
    }

    @Test
    void shouldRetryConfirmationEmailForDuplicateWaitlistedSubmissionWhenConfirmationWasNotSent() {
        User existing = User.builder()
            .email("lead@example.com")
            .displayName("Old Name")
            .registrationStatus(UserRegistrationStatus.WAITLISTED)
            .enabled(false)
            .emailVerified(false)
            .waitlistConfirmationEmailSentAt(null)
            .build();
        existing.setEmail("lead@example.com");

        when(userRepository.findByEmailForUpdate("lead@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WaitlistResponse response = waitlistService.joinWaitlist(
            new WaitlistRequest("New Name", "lead@example.com")
        );

        assertThat(response).isEqualTo(WaitlistResponse.accepted());
        assertThat(existing.getDisplayName()).isEqualTo("New Name");
        assertThat(existing.getWaitlistSource()).isEqualTo("landing");
        assertThat(existing.getWaitlistConfirmationEmailSentAt()).isNotNull();
        verify(emailService).sendWaitlistConfirmationEmail(existing);
        verify(userRepository).save(existing);
    }

    @Test
    void shouldReturnGenericSuccessForRegisteredEmailWithoutChangingAccount() {
        User existing = User.builder()
            .email("member@example.com")
            .registrationStatus(UserRegistrationStatus.REGISTERED)
            .enabled(true)
            .emailVerified(true)
            .build();
        existing.setEmail("member@example.com");

        when(userRepository.findByEmailForUpdate("member@example.com")).thenReturn(Optional.of(existing));

        WaitlistResponse response = waitlistService.joinWaitlist(
            new WaitlistRequest("Member", "member@example.com")
        );

        assertThat(response).isEqualTo(WaitlistResponse.alreadyInSystem());
        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendWaitlistConfirmationEmail(any(User.class));
    }

    @Test
    void shouldBulkDeleteOnlyActiveWaitlistedUsersAndReturnDeletedDtos() {
        UUID waitlistedId = UUID.randomUUID();
        UUID registeredId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        LocalDateTime joinedAt = LocalDateTime.now().minusDays(2);
        LocalDateTime confirmationSentAt = LocalDateTime.now().minusDays(1);
        LocalDateTime createdAt = LocalDateTime.now().minusDays(3);
        User waitlisted = User.builder()
            .id(waitlistedId)
            .email("lead@example.com")
            .displayName("Lead Trader")
            .registrationStatus(UserRegistrationStatus.WAITLISTED)
            .waitlistJoinedAt(joinedAt)
            .waitlistSource("landing")
            .waitlistConfirmationEmailSentAt(confirmationSentAt)
            .createdAt(createdAt)
            .build();
        waitlisted.setEmail("lead@example.com");

        when(userRepository.findActiveWaitlistedByIdInForUpdate(List.of(waitlistedId, registeredId, missingId)))
            .thenReturn(List.of(waitlisted));

        var response = waitlistService.deleteWaitlistedUsers(List.of(waitlistedId, registeredId, missingId));

        assertThat(response.deletedCount()).isEqualTo(1);
        assertThat(response.deletedUsers()).hasSize(1);
        assertThat(response.deletedUsers().getFirst().id()).isEqualTo(waitlistedId);
        assertThat(response.deletedUsers().getFirst().email()).isEqualTo("lead@example.com");
        assertThat(response.deletedUsers().getFirst().displayName()).isEqualTo("Lead Trader");
        assertThat(response.deletedUsers().getFirst().registrationStatus()).isEqualTo(UserRegistrationStatus.WAITLISTED);
        assertThat(response.deletedUsers().getFirst().waitlistJoinedAt()).isEqualTo(joinedAt);
        assertThat(response.deletedUsers().getFirst().waitlistSource()).isEqualTo("landing");
        assertThat(response.deletedUsers().getFirst().waitlistConfirmationEmailSentAt()).isEqualTo(confirmationSentAt);
        assertThat(response.deletedUsers().getFirst().createdAt()).isEqualTo(createdAt);
        verify(userRepository).deleteAll(List.of(waitlisted));
    }

    @Test
    void shouldReturnZeroDeletedWhenBulkDeleteHasNoActiveWaitlistedUsers() {
        UUID registeredId = UUID.randomUUID();
        when(userRepository.findActiveWaitlistedByIdInForUpdate(List.of(registeredId))).thenReturn(List.of());

        var response = waitlistService.deleteWaitlistedUsers(List.of(registeredId));

        assertThat(response.deletedCount()).isZero();
        assertThat(response.deletedUsers()).isEmpty();
        verify(userRepository, never()).deleteAll(any());
    }

    @Test
    void shouldResendConfirmationEmailForActiveWaitlistedUser() {
        UUID userId = UUID.randomUUID();
        User waitlisted = User.builder()
            .id(userId)
            .email("lead@example.com")
            .displayName("Lead Trader")
            .registrationStatus(UserRegistrationStatus.WAITLISTED)
            .waitlistJoinedAt(LocalDateTime.now().minusDays(2))
            .waitlistSource("landing")
            .createdAt(LocalDateTime.now().minusDays(3))
            .build();
        waitlisted.setEmail("lead@example.com");
        when(userRepository.findActiveWaitlistedByIdForUpdate(userId)).thenReturn(Optional.of(waitlisted));
        when(userRepository.save(waitlisted)).thenReturn(waitlisted);

        var response = waitlistService.resendWaitlistConfirmation(userId);

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("lead@example.com");
        assertThat(response.waitlistConfirmationEmailSentAt()).isNotNull();
        verify(emailService).sendWaitlistConfirmationEmail(waitlisted);
        verify(userRepository).save(waitlisted);
    }

    @Test
    void shouldSurfaceResendConfirmationEmailFailure() {
        UUID userId = UUID.randomUUID();
        User waitlisted = User.builder()
            .id(userId)
            .email("lead@example.com")
            .registrationStatus(UserRegistrationStatus.WAITLISTED)
            .build();
        waitlisted.setEmail("lead@example.com");
        when(userRepository.findActiveWaitlistedByIdForUpdate(userId)).thenReturn(Optional.of(waitlisted));
        doThrow(new RuntimeException("SMTP unavailable"))
            .when(emailService)
            .sendWaitlistConfirmationEmail(waitlisted);

        assertThatThrownBy(() -> waitlistService.resendWaitlistConfirmation(userId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_GATEWAY);

        assertThat(waitlisted.getWaitlistConfirmationEmailSentAt()).isNull();
        verify(userRepository, never()).save(waitlisted);
    }
}
