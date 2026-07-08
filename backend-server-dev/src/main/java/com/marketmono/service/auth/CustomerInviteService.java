package com.marketmono.service.auth;

import com.marketmono.dto.admin.CustomerInviteActionRequest;
import com.marketmono.dto.admin.CustomerInviteCreateRequest;
import com.marketmono.dto.admin.CustomerInviteDto;
import com.marketmono.entity.auth.CustomerInviteStatus;
import com.marketmono.entity.auth.User;
import com.marketmono.entity.auth.UserRegistrationStatus;
import com.marketmono.entity.auth.UserRoleType;
import com.marketmono.repository.auth.UserRepository;
import com.marketmono.service.AdminAuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class CustomerInviteService {

    public static final String WAITLISTED_INVITE_MESSAGE =
        "This user is already on the waiting list and cannot be invited manually.";
    public static final String EXISTING_USER_INVITE_MESSAGE =
        "A user with this email already exists.";

    private static final String RESOURCE_TYPE = "CUSTOMER_INVITE";
    private static final String ACTION_CREATED = "CUSTOMER_INVITE_CREATED";
    private static final String ACTION_REJECTED = "CUSTOMER_INVITE_REJECTED";
    private static final String ACTION_REVOKED = "CUSTOMER_INVITE_REVOKED";
    private static final String ACTION_TTL_EXTENDED = "CUSTOMER_INVITE_TTL_EXTENDED";
    private static final String ACTION_REGENERATED = "CUSTOMER_INVITE_REGENERATED";
    private static final String ACTION_ACCEPTED = "CUSTOMER_INVITE_ACCEPTED";
    private static final String REJECTION_WAITLISTED = "WAITLISTED";
    private static final String REJECTION_EXISTING_USER = "EXISTING_USER";

    private static final int DEFAULT_TTL_HOURS = 48;
    private static final int RANDOM_BYTES = 24;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AdminAuditLogService adminAuditLogService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public CustomerInviteService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            AdminAuditLogService adminAuditLogService) {
        this(userRepository, passwordEncoder, emailService, adminAuditLogService, Clock.systemUTC());
    }

    CustomerInviteService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            AdminAuditLogService adminAuditLogService,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.adminAuditLogService = adminAuditLogService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CustomerInviteDto> listInvites() {
        return userRepository.findCustomerInvitesOrderByInvitedAtDesc()
            .stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public CustomerInviteDto createInvite(CustomerInviteCreateRequest request, UUID actorId) {
        String email = normalizeEmail(request.email());
        User existing = userRepository.findByEmailIncludingDeleted(email).orElse(null);
        if (existing != null) {
            if (existing.getRegistrationStatus() == UserRegistrationStatus.WAITLISTED) {
                recordRejectedInvite(actorId, email, REJECTION_WAITLISTED, existing);
                throw new ResponseStatusException(HttpStatus.CONFLICT, WAITLISTED_INVITE_MESSAGE);
            }
            recordRejectedInvite(actorId, email, REJECTION_EXISTING_USER, existing);
            throw new ResponseStatusException(HttpStatus.CONFLICT, EXISTING_USER_INVITE_MESSAGE);
        }

        String temporaryPassword = generateTemporaryPassword();
        LocalDateTime now = now();
        LocalDateTime expiresAt = now.plusHours(ttlHours(request.ttlHours()));

        User user = User.builder()
            .email(email)
            .displayName(email)
            .passwordHash(passwordEncoder.encode(temporaryPassword))
            .registrationStatus(UserRegistrationStatus.REGISTERED)
            .enabled(true)
            .emailVerified(true)
            .requiresPasswordChange(true)
            .passwordChangedAt(null)
            .tempPasswordExpiresAt(expiresAt)
            .customerInviteStatus(CustomerInviteStatus.ACTIVE)
            .customerInvitedAt(now)
            .customerInviteLastSentAt(now)
            .customerInviteRevokedAt(null)
            .customerInvitedBy(actorId)
            .build();
        user.setEmail(email);
        user.addRole(UserRoleType.USER);

        user = userRepository.save(user);
        emailService.sendCustomerInviteEmail(user, temporaryPassword, expiresAt);
        recordInviteAudit(actorId, ACTION_CREATED, user, details(null, user));
        log.info("Customer invite created for {}", email);
        return toDto(user);
    }

    @Transactional
    public CustomerInviteDto revokeInvite(UUID userId, UUID actorId) {
        User user = requireInvitedUser(userId);
        if (user.getCustomerInviteStatus() == CustomerInviteStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer invite has already been accepted.");
        }
        Map<String, Object> before = snapshot(user);
        user.setCustomerInviteStatus(CustomerInviteStatus.REVOKED);
        user.setCustomerInviteRevokedAt(now());
        user.setPasswordHash(passwordEncoder.encode(generateTemporaryPassword()));
        user = userRepository.save(user);
        recordInviteAudit(actorId, ACTION_REVOKED, user, details(before, user));
        return toDto(user);
    }

    @Transactional
    public CustomerInviteDto extendInviteTtl(UUID userId, CustomerInviteActionRequest request, UUID actorId) {
        User user = requireActiveUnexpiredInvite(userId);
        Map<String, Object> before = snapshot(user);
        LocalDateTime baseExpiry = user.getTempPasswordExpiresAt();
        user.setTempPasswordExpiresAt(baseExpiry.plusHours(ttlHours(request.ttlHours())));
        user = userRepository.save(user);
        recordInviteAudit(actorId, ACTION_TTL_EXTENDED, user, details(before, user));
        return toDto(user);
    }

    @Transactional
    public CustomerInviteDto regenerateInvite(UUID userId, CustomerInviteActionRequest request, UUID actorId) {
        User user = requireInvitedUser(userId);
        if (user.getCustomerInviteStatus() == CustomerInviteStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer invite has already been accepted.");
        }

        Map<String, Object> before = snapshot(user);
        String temporaryPassword = generateTemporaryPassword();
        LocalDateTime now = now();
        LocalDateTime expiresAt = now.plusHours(ttlHours(request.ttlHours()));
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setRequiresPasswordChange(true);
        user.setCustomerInviteStatus(CustomerInviteStatus.ACTIVE);
        user.setCustomerInviteRevokedAt(null);
        user.setCustomerInviteLastSentAt(now);
        user.setTempPasswordExpiresAt(expiresAt);
        user = userRepository.save(user);
        emailService.sendCustomerInviteEmail(user, temporaryPassword, expiresAt);
        recordInviteAudit(actorId, ACTION_REGENERATED, user, details(before, user));
        return toDto(user);
    }

    public void markAcceptedIfNeeded(User user) {
        if (Boolean.TRUE.equals(user.getRequiresPasswordChange())) {
            Map<String, Object> before = snapshot(user);
            user.setRequiresPasswordChange(false);
            user.setPasswordChangedAt(now());
            if (user.getCustomerInviteStatus() == CustomerInviteStatus.ACTIVE
                || user.getCustomerInviteStatus() == CustomerInviteStatus.EXPIRED) {
                user.setCustomerInviteStatus(CustomerInviteStatus.ACCEPTED);
            }
            if (user.getCustomerInvitedBy() != null) {
                recordInviteAudit(user.getCustomerInvitedBy(), ACTION_ACCEPTED, user, details(before, user));
            }
        }
    }

    public void ensureTemporaryPasswordUsable(User user) {
        if (!Boolean.TRUE.equals(user.getRequiresPasswordChange())) {
            return;
        }
        if (user.getCustomerInviteStatus() == CustomerInviteStatus.REVOKED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Temporary password has been revoked.");
        }
        if (user.getTempPasswordExpiresAt() != null && user.getTempPasswordExpiresAt().isBefore(now())) {
            user.setCustomerInviteStatus(CustomerInviteStatus.EXPIRED);
            userRepository.save(user);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Temporary password has expired.");
        }
    }

    private User requireActiveUnexpiredInvite(UUID userId) {
        User user = requireInvitedUser(userId);
        if (user.getCustomerInviteStatus() != CustomerInviteStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer invite is not active.");
        }
        if (user.getTempPasswordExpiresAt() == null || user.getTempPasswordExpiresAt().isBefore(now())) {
            user.setCustomerInviteStatus(CustomerInviteStatus.EXPIRED);
            userRepository.save(user);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer invite has expired.");
        }
        return user;
    }

    private User requireInvitedUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer invite not found."));
        if (user.getCustomerInvitedAt() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer invite not found.");
        }
        return user;
    }

    private CustomerInviteDto toDto(User user) {
        return new CustomerInviteDto(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getRegistrationStatus().name(),
            user.getCustomerInviteStatus(),
            Boolean.TRUE.equals(user.getEnabled()),
            Boolean.TRUE.equals(user.getEmailVerified()),
            Boolean.TRUE.equals(user.getRequiresPasswordChange()),
            user.getCustomerInvitedAt(),
            user.getCustomerInviteLastSentAt(),
            user.getTempPasswordExpiresAt(),
            user.getCustomerInviteRevokedAt(),
            user.getPasswordChangedAt()
        );
    }

    private String generateTemporaryPassword() {
        byte[] bytes = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void recordRejectedInvite(UUID actorId, String email, String rejectionReason, User existingUser) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("email", email);
        details.put("rejectionReason", rejectionReason);
        if (existingUser != null) {
            details.put("existingUser", snapshot(existingUser));
        }
        adminAuditLogService.recordEvent(actorId, ACTION_REJECTED, RESOURCE_TYPE, email, details);
    }

    private void recordInviteAudit(UUID actorId, String action, User user, Map<String, Object> details) {
        adminAuditLogService.recordEvent(
            actorId,
            action,
            RESOURCE_TYPE,
            user.getId() != null ? user.getId().toString() : null,
            details
        );
    }

    private Map<String, Object> details(Map<String, Object> before, User after) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (before != null) {
            details.put("before", before);
        }
        if (after != null) {
            details.put("after", snapshot(after));
        }
        return details;
    }

    private Map<String, Object> snapshot(User user) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", user.getId() != null ? user.getId().toString() : null);
        snapshot.put("email", user.getEmail());
        snapshot.put("registrationStatus", user.getRegistrationStatus() != null
            ? user.getRegistrationStatus().name()
            : null);
        snapshot.put("customerInviteStatus", user.getCustomerInviteStatus() != null
            ? user.getCustomerInviteStatus().name()
            : null);
        snapshot.put("enabled", user.getEnabled());
        snapshot.put("emailVerified", user.getEmailVerified());
        snapshot.put("requiresPasswordChange", user.getRequiresPasswordChange());
        snapshot.put("customerInvitedAt", user.getCustomerInvitedAt() != null
            ? user.getCustomerInvitedAt().toString()
            : null);
        snapshot.put("customerInviteLastSentAt", user.getCustomerInviteLastSentAt() != null
            ? user.getCustomerInviteLastSentAt().toString()
            : null);
        snapshot.put("tempPasswordExpiresAt", user.getTempPasswordExpiresAt() != null
            ? user.getTempPasswordExpiresAt().toString()
            : null);
        snapshot.put("customerInviteRevokedAt", user.getCustomerInviteRevokedAt() != null
            ? user.getCustomerInviteRevokedAt().toString()
            : null);
        snapshot.put("passwordChangedAt", user.getPasswordChangedAt() != null
            ? user.getPasswordChangedAt().toString()
            : null);
        return snapshot;
    }

    private int ttlHours(Integer ttlHours) {
        return ttlHours == null ? DEFAULT_TTL_HOURS : ttlHours;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
