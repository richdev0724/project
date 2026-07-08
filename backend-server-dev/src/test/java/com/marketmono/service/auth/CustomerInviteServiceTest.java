package com.marketmono.service.auth;

import com.marketmono.dto.admin.CustomerInviteActionRequest;
import com.marketmono.dto.admin.CustomerInviteCreateRequest;
import com.marketmono.dto.admin.CustomerInviteDto;
import com.marketmono.entity.auth.CustomerInviteStatus;
import com.marketmono.entity.auth.User;
import com.marketmono.entity.auth.UserRegistrationStatus;
import com.marketmono.repository.auth.UserRepository;
import com.marketmono.service.AdminAuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerInviteServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-19T10:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    private CustomerInviteService service;

    @BeforeEach
    void setUp() {
        service = new CustomerInviteService(
            userRepository,
            passwordEncoder,
            emailService,
            adminAuditLogService,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createInviteCreatesCustomerOnlyWhenEmailIsAbsentFromSystem() {
        UUID actorId = UUID.randomUUID();
        when(userRepository.findByEmailIncludingDeleted("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed-temp-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        CustomerInviteDto created = service.createInvite(
            new CustomerInviteCreateRequest(" new@example.com ", 48),
            actorId
        );

        assertThat(created.email()).isEqualTo("new@example.com");
        assertThat(created.inviteStatus()).isEqualTo(CustomerInviteStatus.ACTIVE);
        assertThat(created.requiresPasswordChange()).isTrue();
        assertThat(created.tempPasswordExpiresAt()).isEqualTo(LocalDateTime.of(2026, 5, 21, 10, 0));

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getRegistrationStatus()).isEqualTo(UserRegistrationStatus.REGISTERED);
        assertThat(savedUser.getValue().getEnabled()).isTrue();
        assertThat(savedUser.getValue().getEmailVerified()).isTrue();
        assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("hashed-temp-password");
        verify(emailService).sendCustomerInviteEmail(any(User.class), any(), any(LocalDateTime.class));
        verify(adminAuditLogService).recordEvent(
            eq(actorId),
            eq("CUSTOMER_INVITE_CREATED"),
            eq("CUSTOMER_INVITE"),
            eq(savedUser.getValue().getId().toString()),
            argThat(details -> snapshotFieldEquals(details, "after", "email", "new@example.com"))
        );
    }

    @Test
    void createInviteDefaultsTemporaryPasswordTtlTo48HoursWhenMissing() {
        UUID actorId = UUID.randomUUID();
        when(userRepository.findByEmailIncludingDeleted("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed-temp-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        CustomerInviteDto created = service.createInvite(
            new CustomerInviteCreateRequest("new@example.com", null),
            actorId
        );

        assertThat(created.tempPasswordExpiresAt()).isEqualTo(LocalDateTime.of(2026, 5, 21, 10, 0));
        verify(emailService).sendCustomerInviteEmail(
            any(User.class),
            any(),
            eq(LocalDateTime.of(2026, 5, 21, 10, 0))
        );
    }

    @Test
    void createInviteRejectsWaitlistedEmailWithWaitlistMessage() {
        User waitlisted = User.builder()
            .email("lead@example.com")
            .registrationStatus(UserRegistrationStatus.WAITLISTED)
            .build();
        waitlisted.setEmail("lead@example.com");
        when(userRepository.findByEmailIncludingDeleted("lead@example.com")).thenReturn(Optional.of(waitlisted));
        UUID actorId = UUID.randomUUID();

        assertThatThrownBy(() -> service.createInvite(
            new CustomerInviteCreateRequest("lead@example.com", 24),
            actorId
        ))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(ex.getReason()).isEqualTo("This user is already on the waiting list and cannot be invited manually.");
            });
        verify(adminAuditLogService).recordEvent(
            eq(actorId),
            eq("CUSTOMER_INVITE_REJECTED"),
            eq("CUSTOMER_INVITE"),
            eq("lead@example.com"),
            argThat(details -> "WAITLISTED".equals(details.get("rejectionReason")))
        );
    }

    @Test
    void createInviteRejectsAnyOtherExistingUserStatusWithExistingUserMessage() {
        User registered = User.builder()
            .email("registered@example.com")
            .registrationStatus(UserRegistrationStatus.REGISTERED)
            .build();
        registered.setEmail("registered@example.com");
        when(userRepository.findByEmailIncludingDeleted("registered@example.com")).thenReturn(Optional.of(registered));
        UUID actorId = UUID.randomUUID();

        assertThatThrownBy(() -> service.createInvite(
            new CustomerInviteCreateRequest("registered@example.com", 24),
            actorId
        ))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(ex.getReason()).isEqualTo("A user with this email already exists.");
            });
        verify(adminAuditLogService).recordEvent(
            eq(actorId),
            eq("CUSTOMER_INVITE_REJECTED"),
            eq("CUSTOMER_INVITE"),
            eq("registered@example.com"),
            argThat(details -> "EXISTING_USER".equals(details.get("rejectionReason")))
        );
    }

    @Test
    void extendInviteTtlOnlyWorksWhileInviteIsActiveAndUnexpired() {
        UUID actorId = UUID.randomUUID();
        User invited = invitedUser(CustomerInviteStatus.ACTIVE, LocalDateTime.of(2026, 5, 19, 12, 0));
        when(userRepository.findById(invited.getId())).thenReturn(Optional.of(invited));
        when(userRepository.save(invited)).thenReturn(invited);

        CustomerInviteDto extended = service.extendInviteTtl(
            invited.getId(),
            new CustomerInviteActionRequest(6),
            actorId
        );

        assertThat(extended.tempPasswordExpiresAt()).isEqualTo(LocalDateTime.of(2026, 5, 19, 18, 0));
        verify(adminAuditLogService).recordEvent(
            eq(actorId),
            eq("CUSTOMER_INVITE_TTL_EXTENDED"),
            eq("CUSTOMER_INVITE"),
            eq(invited.getId().toString()),
            argThat(details ->
                snapshotFieldEquals(details, "before", "tempPasswordExpiresAt", "2026-05-19T12:00")
                    && snapshotFieldEquals(details, "after", "tempPasswordExpiresAt", "2026-05-19T18:00"))
        );
    }

    @Test
    void extendInviteTtlDefaultsTo48HoursWhenMissing() {
        UUID actorId = UUID.randomUUID();
        User invited = invitedUser(CustomerInviteStatus.ACTIVE, LocalDateTime.of(2026, 5, 19, 12, 0));
        when(userRepository.findById(invited.getId())).thenReturn(Optional.of(invited));
        when(userRepository.save(invited)).thenReturn(invited);

        CustomerInviteDto extended = service.extendInviteTtl(
            invited.getId(),
            new CustomerInviteActionRequest(null),
            actorId
        );

        assertThat(extended.tempPasswordExpiresAt()).isEqualTo(LocalDateTime.of(2026, 5, 21, 12, 0));
    }

    @Test
    void revokeInviteInvalidatesOutstandingTemporaryPassword() {
        UUID actorId = UUID.randomUUID();
        User invited = invitedUser(CustomerInviteStatus.ACTIVE, LocalDateTime.of(2026, 5, 19, 12, 0));
        when(userRepository.findById(invited.getId())).thenReturn(Optional.of(invited));
        when(passwordEncoder.encode(any())).thenReturn("revoked-hash");
        when(userRepository.save(invited)).thenReturn(invited);

        CustomerInviteDto revoked = service.revokeInvite(invited.getId(), actorId);

        assertThat(revoked.inviteStatus()).isEqualTo(CustomerInviteStatus.REVOKED);
        assertThat(invited.getPasswordHash()).isEqualTo("revoked-hash");
        verify(adminAuditLogService).recordEvent(
            eq(actorId),
            eq("CUSTOMER_INVITE_REVOKED"),
            eq("CUSTOMER_INVITE"),
            eq(invited.getId().toString()),
            argThat(details ->
                snapshotFieldEquals(details, "before", "customerInviteStatus", "ACTIVE")
                    && snapshotFieldEquals(details, "after", "customerInviteStatus", "REVOKED"))
        );
    }

    @Test
    void regenerateInviteRefreshesTemporaryPasswordAndRecordsAuditLog() {
        UUID actorId = UUID.randomUUID();
        User invited = invitedUser(CustomerInviteStatus.REVOKED, LocalDateTime.of(2026, 5, 19, 12, 0));
        when(userRepository.findById(invited.getId())).thenReturn(Optional.of(invited));
        when(passwordEncoder.encode(any())).thenReturn("regenerated-hash");
        when(userRepository.save(invited)).thenReturn(invited);

        CustomerInviteDto regenerated = service.regenerateInvite(
            invited.getId(),
            new CustomerInviteActionRequest(12),
            actorId
        );

        assertThat(regenerated.inviteStatus()).isEqualTo(CustomerInviteStatus.ACTIVE);
        assertThat(regenerated.tempPasswordExpiresAt()).isEqualTo(LocalDateTime.of(2026, 5, 19, 22, 0));
        verify(emailService).sendCustomerInviteEmail(any(User.class), any(), any(LocalDateTime.class));
        verify(adminAuditLogService).recordEvent(
            eq(actorId),
            eq("CUSTOMER_INVITE_REGENERATED"),
            eq("CUSTOMER_INVITE"),
            eq(invited.getId().toString()),
            argThat(details ->
                snapshotFieldEquals(details, "before", "customerInviteStatus", "REVOKED")
                    && snapshotFieldEquals(details, "after", "customerInviteStatus", "ACTIVE"))
        );
    }

    @Test
    void regenerateInviteDefaultsTemporaryPasswordTtlTo48HoursWhenMissing() {
        UUID actorId = UUID.randomUUID();
        User invited = invitedUser(CustomerInviteStatus.REVOKED, LocalDateTime.of(2026, 5, 19, 12, 0));
        when(userRepository.findById(invited.getId())).thenReturn(Optional.of(invited));
        when(passwordEncoder.encode(any())).thenReturn("regenerated-hash");
        when(userRepository.save(invited)).thenReturn(invited);

        CustomerInviteDto regenerated = service.regenerateInvite(
            invited.getId(),
            new CustomerInviteActionRequest(null),
            actorId
        );

        assertThat(regenerated.tempPasswordExpiresAt()).isEqualTo(LocalDateTime.of(2026, 5, 21, 10, 0));
        verify(emailService).sendCustomerInviteEmail(
            any(User.class),
            any(),
            eq(LocalDateTime.of(2026, 5, 21, 10, 0))
        );
    }

    @Test
    void revokeInviteRejectsAlreadyAcceptedInviteWithoutChangingPassword() {
        UUID actorId = UUID.randomUUID();
        User invited = invitedUser(CustomerInviteStatus.ACCEPTED, LocalDateTime.of(2026, 5, 19, 12, 0));
        invited.setRequiresPasswordChange(false);
        when(userRepository.findById(invited.getId())).thenReturn(Optional.of(invited));

        assertThatThrownBy(() -> service.revokeInvite(invited.getId(), actorId))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(ex.getReason()).isEqualTo("Customer invite has already been accepted.");
            });

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void markAcceptedIfNeededClearsPasswordChangeFlagAndRecordsAcceptedAuditLog() {
        UUID actorId = UUID.randomUUID();
        User invited = invitedUser(CustomerInviteStatus.ACTIVE, LocalDateTime.of(2026, 5, 19, 12, 0));
        invited.setCustomerInvitedBy(actorId);

        service.markAcceptedIfNeeded(invited);

        assertThat(invited.getRequiresPasswordChange()).isFalse();
        assertThat(invited.getPasswordChangedAt()).isEqualTo(LocalDateTime.of(2026, 5, 19, 10, 0));
        assertThat(invited.getCustomerInviteStatus()).isEqualTo(CustomerInviteStatus.ACCEPTED);
        verify(adminAuditLogService).recordEvent(
            eq(actorId),
            eq("CUSTOMER_INVITE_ACCEPTED"),
            eq("CUSTOMER_INVITE"),
            eq(invited.getId().toString()),
            argThat(details ->
                snapshotFieldEquals(details, "before", "requiresPasswordChange", "true")
                    && snapshotFieldEquals(details, "after", "requiresPasswordChange", "false")
                    && snapshotFieldEquals(details, "after", "customerInviteStatus", "ACCEPTED"))
        );
    }

    private User invitedUser(CustomerInviteStatus status, LocalDateTime expiresAt) {
        User user = User.builder()
            .id(UUID.randomUUID())
            .email("invited@example.com")
            .registrationStatus(UserRegistrationStatus.REGISTERED)
            .enabled(true)
            .emailVerified(true)
            .passwordHash("hashed")
            .requiresPasswordChange(true)
            .customerInviteStatus(status)
            .customerInvitedAt(LocalDateTime.of(2026, 5, 19, 9, 0))
            .customerInviteLastSentAt(LocalDateTime.of(2026, 5, 19, 9, 0))
            .tempPasswordExpiresAt(expiresAt)
            .build();
        user.setEmail("invited@example.com");
        return user;
    }

    private boolean snapshotFieldEquals(Map<String, Object> details, String key, String field, String expectedValue) {
        if (details == null || !(details.get(key) instanceof Map<?, ?> snapshot)) {
            return false;
        }
        return expectedValue.equals(String.valueOf(snapshot.get(field)));
    }
}
