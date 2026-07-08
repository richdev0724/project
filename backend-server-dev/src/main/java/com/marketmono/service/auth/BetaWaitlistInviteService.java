package com.marketmono.service.auth;

import com.marketmono.dto.waitlist.BetaWaitlistInvitePreviewResponse;
import com.marketmono.dto.waitlist.BetaWaitlistInviteRequest;
import com.marketmono.dto.waitlist.BetaWaitlistInviteResponse;
import com.marketmono.dto.waitlist.BetaWaitlistInviteSkippedUserDto;
import com.marketmono.dto.waitlist.BetaWaitlistInviteStatus;
import com.marketmono.dto.waitlist.BetaWaitlistSelectionMode;
import com.marketmono.dto.waitlist.WaitlistedUserDto;
import com.marketmono.entity.StripeConfig;
import com.marketmono.entity.auth.CustomerInviteStatus;
import com.marketmono.entity.auth.User;
import com.marketmono.entity.auth.UserBetaAccess;
import com.marketmono.repository.auth.UserRepository;
import com.marketmono.service.AdminAuditLogService;
import com.marketmono.service.StripeConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BetaWaitlistInviteService {

    private static final String RESOURCE_TYPE = "BETA_WAITLIST_INVITE";
    private static final String ACTION_SENT = "BETA_WAITLIST_INVITE_SENT";
    private static final String PUBLIC_BETA_PROMO_CODE = "BETAMM2026";
    private static final int DEFAULT_TRIAL_DAYS = 3;
    private static final int PREVIEW_SAMPLE_LIMIT = 25;
    private static final Pattern EVENT_HANDLER_ATTRIBUTE =
        Pattern.compile("\\son[a-z]+\\s*=", Pattern.CASE_INSENSITIVE);

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AdminAuditLogService adminAuditLogService;
    private final StripeConfigService stripeConfigService;
    private final Clock clock;

    @Autowired
    public BetaWaitlistInviteService(
            UserRepository userRepository,
            EmailService emailService,
            AdminAuditLogService adminAuditLogService,
            StripeConfigService stripeConfigService) {
        this(
            userRepository,
            emailService,
            adminAuditLogService,
            stripeConfigService,
            Clock.systemUTC()
        );
    }

    BetaWaitlistInviteService(
            UserRepository userRepository,
            EmailService emailService,
            AdminAuditLogService adminAuditLogService,
            StripeConfigService stripeConfigService,
            Clock clock) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.adminAuditLogService = adminAuditLogService;
        this.stripeConfigService = stripeConfigService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BetaWaitlistInvitePreviewResponse preview(BetaWaitlistSelectionMode selectionMode, List<UUID> ids) {
        BetaWaitlistSelectionMode mode = selectionModeOrDefault(selectionMode);
        List<UUID> uniqueIds = uniqueIds(ids);
        List<User> eligibleUsers = mode == BetaWaitlistSelectionMode.ALL_ELIGIBLE
            ? userRepository.findEligibleBetaWaitlistInviteUsersForPreview()
            : uniqueIds.isEmpty()
                ? List.of()
                : userRepository.findBetaWaitlistCandidatesByIdInForPreview(uniqueIds);

        Map<UUID, User> eligibleById = eligibleUsers.stream()
            .filter(user -> user.getId() != null)
            .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
        List<BetaWaitlistInviteSkippedUserDto> skippedUsers = mode == BetaWaitlistSelectionMode.ALL_ELIGIBLE
            ? List.of()
            : uniqueIds.stream()
                .filter(id -> !eligibleById.containsKey(id))
                .map(id -> new BetaWaitlistInviteSkippedUserDto(id, null, "NOT_ELIGIBLE"))
                .toList();

        int alreadyBetaCount = (int) eligibleUsers.stream()
            .filter(user -> Boolean.TRUE.equals(user.getBetaUser()))
            .count();

        return new BetaWaitlistInvitePreviewResponse(
            mode,
            eligibleUsers.size(),
            eligibleUsers.stream().limit(PREVIEW_SAMPLE_LIMIT).map(this::toDto).toList(),
            alreadyBetaCount,
            skippedUsers
        );
    }

    @Transactional
    public BetaWaitlistInviteResponse invite(BetaWaitlistInviteRequest request, UUID actorId) {
        validateTemplatePayload(request);
        BetaWaitlistSelectionMode mode = selectionModeOrDefault(request.selectionMode());
        List<UUID> uniqueIds = uniqueIds(request.ids());
        if (mode != BetaWaitlistSelectionMode.ALL_ELIGIBLE && uniqueIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one waitlist user must be selected.");
        }

        List<User> candidates = mode == BetaWaitlistSelectionMode.ALL_ELIGIBLE
            ? userRepository.findAllEligibleBetaWaitlistInviteUsersForUpdate()
            : userRepository.findBetaWaitlistCandidatesByIdInForUpdate(uniqueIds);

        Map<UUID, User> candidateById = candidates.stream()
            .filter(user -> user.getId() != null)
            .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));

        List<BetaWaitlistInviteSkippedUserDto> skippedUsers = mode == BetaWaitlistSelectionMode.ALL_ELIGIBLE
            ? List.of()
            : uniqueIds.stream()
                .filter(id -> !candidateById.containsKey(id))
                .map(id -> new BetaWaitlistInviteSkippedUserDto(id, null, "NOT_ELIGIBLE"))
                .toList();

        String promoCode = betaPromoCode();
        int trialDays = betaTrialDays();
        List<WaitlistedUserDto> invitedUsers = new ArrayList<>();

        for (User user : orderedCandidates(candidates, uniqueIds, mode)) {
            invitedUsers.add(inviteUser(user, actorId, promoCode, trialDays));
        }

        int requestedCount = mode == BetaWaitlistSelectionMode.ALL_ELIGIBLE
            ? candidates.size()
            : uniqueIds.size();

        return new BetaWaitlistInviteResponse(
            requestedCount,
            invitedUsers.size(),
            skippedUsers.size(),
            invitedUsers,
            skippedUsers
        );
    }

    private WaitlistedUserDto inviteUser(
            User user,
            UUID actorId,
            String promoCode,
            int trialDays) {
        Map<String, Object> before = snapshot(user);
        LocalDateTime now = now();

        user.setBetaUser(true);
        UserBetaAccess betaAccess = user.ensureBetaAccess();
        if (betaAccess.getBetaInvitedAt() == null) {
            betaAccess.setBetaInvitedAt(now);
        }
        betaAccess.setBetaInviteLastSentAt(now);
        betaAccess.setBetaInvitedBy(actorId);
        betaAccess.setBetaPromoCode(promoCode);
        if (betaAccess.getUsedBetaPromo() == null) {
            betaAccess.setUsedBetaPromo(false);
        }

        User saved = userRepository.save(user);
        emailService.sendBetaWaitlistInviteEmail(saved, promoCode, trialDays);
        recordAudit(actorId, saved, before, snapshot(saved), trialDays);
        log.info("Beta waitlist invite sent to {}", saved.getEmail());
        return toDto(saved);
    }

    private List<User> orderedCandidates(
            List<User> candidates,
            List<UUID> uniqueIds,
            BetaWaitlistSelectionMode mode) {
        if (mode == BetaWaitlistSelectionMode.ALL_ELIGIBLE) {
            return candidates;
        }
        Map<UUID, User> candidateById = candidates.stream()
            .filter(user -> user.getId() != null)
            .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
        return uniqueIds.stream()
            .map(candidateById::get)
            .filter(user -> user != null)
            .toList();
    }

    private void validateTemplatePayload(BetaWaitlistInviteRequest request) {
        rejectUnsafeTemplateText(request.templateSubject());
        rejectUnsafeTemplateText(request.templateHtml());
        rejectUnsafeTemplateText(request.templateText());
    }

    private void rejectUnsafeTemplateText(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String lowered = value.toLowerCase();
        if (lowered.contains("<script") || EVENT_HANDLER_ATTRIBUTE.matcher(value).find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Template contains unsupported script content.");
        }
    }

    private void recordAudit(
            UUID actorId,
            User user,
            Map<String, Object> before,
            Map<String, Object> after,
            int trialDays) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("before", before);
        details.put("after", after);
        details.put("promoCode", betaPromoCode());
        details.put("trialDays", trialDays);
        adminAuditLogService.recordEvent(
            actorId,
            ACTION_SENT,
            RESOURCE_TYPE,
            user.getId() != null ? user.getId().toString() : null,
            details
        );
    }

    private WaitlistedUserDto toDto(User user) {
        UserBetaAccess betaAccess = user.getBetaAccess();
        return new WaitlistedUserDto(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getRegistrationStatus(),
            user.getWaitlistJoinedAt(),
            user.getWaitlistSource(),
            user.getWaitlistConfirmationEmailSentAt(),
            user.getCreatedAt(),
            Boolean.TRUE.equals(user.getBetaUser()),
            betaAccess != null ? betaAccess.getBetaInvitedAt() : null,
            betaAccess != null ? betaAccess.getBetaSignedUpAt() : null,
            betaAccess != null ? betaAccess.getBetaInviteLastSentAt() : null,
            betaAccess != null ? betaAccess.getBetaInvitedBy() : null,
            betaInviteStatus(user),
            betaAccess != null && Boolean.TRUE.equals(betaAccess.getUsedBetaPromo()),
            betaAccess != null ? betaAccess.getBetaPromoAppliedAt() : null
        );
    }

    private BetaWaitlistInviteStatus betaInviteStatus(User user) {
        UserBetaAccess betaAccess = user.getBetaAccess();
        LocalDateTime betaInvitedAt = betaAccess != null ? betaAccess.getBetaInvitedAt() : null;
        LocalDateTime betaSignedUpAt = betaAccess != null ? betaAccess.getBetaSignedUpAt() : null;
        LocalDateTime betaInviteLastSentAt = betaAccess != null ? betaAccess.getBetaInviteLastSentAt() : null;
        if (user.getCustomerInviteStatus() == CustomerInviteStatus.REVOKED && betaInvitedAt != null) {
            return BetaWaitlistInviteStatus.REVOKED;
        }
        if (user.getCustomerInviteStatus() == CustomerInviteStatus.EXPIRED && betaInvitedAt != null) {
            return BetaWaitlistInviteStatus.EXPIRED;
        }
        if (user.getCustomerInviteStatus() == CustomerInviteStatus.ACCEPTED || user.getPasswordChangedAt() != null) {
            return BetaWaitlistInviteStatus.ACCEPTED;
        }
        if (betaSignedUpAt != null) {
            return BetaWaitlistInviteStatus.SIGNED_UP_FOR_BETA;
        }
        if (betaInvitedAt != null || betaInviteLastSentAt != null) {
            return BetaWaitlistInviteStatus.BETA_INVITE_SENT;
        }
        return BetaWaitlistInviteStatus.NOT_INVITED;
    }

    private Map<String, Object> snapshot(User user) {
        UserBetaAccess betaAccess = user.getBetaAccess();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", user.getId() != null ? user.getId().toString() : null);
        snapshot.put("email", user.getEmail());
        snapshot.put("registrationStatus", user.getRegistrationStatus() != null
            ? user.getRegistrationStatus().name()
            : null);
        snapshot.put("customerInviteStatus", user.getCustomerInviteStatus() != null
            ? user.getCustomerInviteStatus().name()
            : null);
        snapshot.put("betaUser", Boolean.TRUE.equals(user.getBetaUser()));
        snapshot.put("betaInvitedAt", timestamp(betaAccess != null ? betaAccess.getBetaInvitedAt() : null));
        snapshot.put("betaSignedUpAt", timestamp(betaAccess != null ? betaAccess.getBetaSignedUpAt() : null));
        snapshot.put("betaInviteLastSentAt", timestamp(betaAccess != null ? betaAccess.getBetaInviteLastSentAt() : null));
        snapshot.put("usedBetaPromo", betaAccess != null && Boolean.TRUE.equals(betaAccess.getUsedBetaPromo()));
        snapshot.put("betaPromoAppliedAt", timestamp(betaAccess != null ? betaAccess.getBetaPromoAppliedAt() : null));
        return snapshot;
    }

    private String timestamp(LocalDateTime timestamp) {
        return timestamp != null ? timestamp.toString() : null;
    }

    private String betaPromoCode() {
        return stripeConfigService.getValue(StripeConfig.BETA_PROMO_CODE, PUBLIC_BETA_PROMO_CODE);
    }

    private int betaTrialDays() {
        return stripeConfigService.getValueAsInt(StripeConfig.BETA_TRIAL_DAYS, DEFAULT_TRIAL_DAYS);
    }

    private List<UUID> uniqueIds(List<UUID> ids) {
        return ids == null ? List.of() : new LinkedHashSet<>(ids).stream().toList();
    }

    private BetaWaitlistSelectionMode selectionModeOrDefault(BetaWaitlistSelectionMode selectionMode) {
        return selectionMode == null ? BetaWaitlistSelectionMode.IDS : selectionMode;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
