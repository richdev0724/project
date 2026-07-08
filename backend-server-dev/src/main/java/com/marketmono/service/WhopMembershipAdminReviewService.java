package com.marketmono.service;

import com.marketmono.dto.whop.WhopMembershipAttachRequest;
import com.marketmono.dto.whop.WhopMembershipAttachResponse;
import com.marketmono.dto.whop.WhopMembershipCandidateConfidence;
import com.marketmono.dto.whop.WhopMembershipCandidateEvidenceDto;
import com.marketmono.dto.whop.WhopMembershipCandidateUserDto;
import com.marketmono.dto.whop.WhopMembershipCheckoutAttemptDto;
import com.marketmono.dto.whop.WhopMembershipReviewDetailDto;
import com.marketmono.dto.whop.WhopMembershipReviewItemDto;
import com.marketmono.dto.whop.WhopMembershipWebhookLedgerDto;
import com.marketmono.entity.BillingPeriod;
import com.marketmono.entity.BillingProvider;
import com.marketmono.entity.BillingWebhookEvent;
import com.marketmono.entity.Product;
import com.marketmono.entity.ProductPrice;
import com.marketmono.entity.StripeMode;
import com.marketmono.entity.Subscription;
import com.marketmono.entity.SubscriptionStatus;
import com.marketmono.entity.WhopCheckoutAttempt;
import com.marketmono.entity.WhopCheckoutAttemptStatus;
import com.marketmono.entity.WhopMembershipCorrelation;
import com.marketmono.entity.WhopMembershipCorrelationStatus;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.BillingWebhookEventRepository;
import com.marketmono.repository.SubscriptionRepository;
import com.marketmono.repository.WhopCheckoutAttemptRepository;
import com.marketmono.repository.WhopMembershipCorrelationRepository;
import com.marketmono.repository.auth.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WhopMembershipAdminReviewService {

    private static final String DEFAULT_CURRENCY = "USD";
    private static final String AUDIT_ACTION_ATTACHED = "WHOP_MEMBERSHIP_ATTACHED";
    private static final String AUDIT_ACTION_RESOLVED_WITHOUT_ACTION =
        "WHOP_MEMBERSHIP_RESOLVED_WITHOUT_ACTION";
    private static final String AUDIT_RESOURCE_TYPE = "WHOP_MEMBERSHIP_CORRELATION";

    private final WhopMembershipCorrelationRepository whopMembershipCorrelationRepository;
    private final WhopCheckoutAttemptRepository whopCheckoutAttemptRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingWebhookEventRepository billingWebhookEventRepository;
    private final UserRepository userRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final Clock clock;

    @Autowired
    public WhopMembershipAdminReviewService(
            WhopMembershipCorrelationRepository whopMembershipCorrelationRepository,
            WhopCheckoutAttemptRepository whopCheckoutAttemptRepository,
            SubscriptionRepository subscriptionRepository,
            BillingWebhookEventRepository billingWebhookEventRepository,
            UserRepository userRepository,
            AdminAuditLogService adminAuditLogService) {
        this(
            whopMembershipCorrelationRepository,
            whopCheckoutAttemptRepository,
            subscriptionRepository,
            billingWebhookEventRepository,
            userRepository,
            adminAuditLogService,
            Clock.systemUTC()
        );
    }

    WhopMembershipAdminReviewService(
            WhopMembershipCorrelationRepository whopMembershipCorrelationRepository,
            WhopCheckoutAttemptRepository whopCheckoutAttemptRepository,
            SubscriptionRepository subscriptionRepository,
            BillingWebhookEventRepository billingWebhookEventRepository,
            UserRepository userRepository,
            AdminAuditLogService adminAuditLogService,
            Clock clock) {
        this.whopMembershipCorrelationRepository = whopMembershipCorrelationRepository;
        this.whopCheckoutAttemptRepository = whopCheckoutAttemptRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.billingWebhookEventRepository = billingWebhookEventRepository;
        this.userRepository = userRepository;
        this.adminAuditLogService = adminAuditLogService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<WhopMembershipReviewItemDto> listReviews(
            WhopMembershipCorrelationStatus status,
            String search,
            boolean includeResolved,
            Pageable pageable) {
        List<WhopMembershipCorrelationStatus> statuses = status == null
            ? reviewStatuses(includeResolved)
            : List.of(status);
        String normalizedSearch = StringUtils.hasText(search) ? search.trim() : "";
        return whopMembershipCorrelationRepository.findReviews(statuses, normalizedSearch, pageable)
            .map(this::toItem);
    }

    @Transactional(readOnly = true)
    public WhopMembershipReviewDetailDto getReviewDetail(UUID correlationId) {
        WhopMembershipCorrelation correlation = whopMembershipCorrelationRepository.findById(correlationId)
            .orElseThrow(() -> new WhopMembershipReviewNotFoundException("Whop membership review was not found"));

        return toDetail(correlation, candidatesFor(correlation));
    }

    @Transactional
    public WhopMembershipReviewDetailDto updateReviewNote(UUID correlationId, String reviewNote) {
        WhopMembershipCorrelation correlation = whopMembershipCorrelationRepository.findByIdForUpdate(correlationId)
            .orElseThrow(() -> new WhopMembershipReviewNotFoundException("Whop membership review was not found"));
        correlation.setReviewReason(StringUtils.hasText(reviewNote) ? reviewNote.trim() : null);
        whopMembershipCorrelationRepository.save(correlation);
        return toDetail(correlation, candidatesFor(correlation));
    }

    @Transactional
    public WhopMembershipAttachResponse attachMembership(
            UUID correlationId,
            WhopMembershipAttachRequest request,
            UUID adminId,
            String ipAddress,
            String userAgent) {
        WhopMembershipCorrelation correlation = whopMembershipCorrelationRepository.findByIdForUpdate(correlationId)
            .orElseThrow(() -> new WhopMembershipReviewNotFoundException("Whop membership review was not found"));
        if (correlation.getStatus() == WhopMembershipCorrelationStatus.MATCHED
            && correlation.getUser() != null
            && correlation.getSubscription() != null
            && correlation.getUser().getId().equals(request.selectedUserId())) {
            return new WhopMembershipAttachResponse(
                correlation.getId(),
                correlation.getUser().getId(),
                correlation.getSubscription().getId(),
                correlation.getStatus(),
                0
            );
        }
        ensureReviewable(correlation);

        User user = userRepository.findActiveByIdForUpdate(request.selectedUserId())
            .orElseThrow(() -> new WhopMembershipReviewConflictException("Selected user was not found"));
        if (user.isBanned()) {
            throw new WhopMembershipReviewConflictException("Selected user is banned");
        }
        if (subscriptionRepository.hasWebsiteAccessSubscription(user.getId())) {
            throw new WhopMembershipReviewConflictException(
                "Selected user already has an access-granting subscription"
            );
        }
        if (emailMismatch(correlation.getBuyerEmail(), user.getEmail()) && !request.overrideConflicts()) {
            throw new WhopMembershipReviewConflictException(
                "Selected user email differs from Whop buyer email"
            );
        }
        subscriptionRepository.findByBillingProviderAndProviderSubscriptionId(
            BillingProvider.WHOP,
            correlation.getWhopMembershipId()
        ).ifPresent(existing -> {
            throw new WhopMembershipReviewConflictException("Whop membership is already attached");
        });

        Product product = productFor(correlation);
        ProductPrice price = priceForPlan(product, correlation.getWhopPlanId());
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime periodEnd = periodEnd(now, billingPeriod(price));
        String reviewNote = normalizedReviewNote(request.reason());

        Subscription subscription = new Subscription();
        subscription.setBillingProvider(BillingProvider.WHOP);
        subscription.setProviderSubscriptionId(correlation.getWhopMembershipId());
        subscription.setProviderCustomerId(correlation.getWhopUserId());
        subscription.setProviderUserId(correlation.getWhopUserId());
        subscription.setProviderProductId(correlation.getWhopProductId());
        subscription.setProviderPlanId(correlation.getWhopPlanId());
        subscription.setProviderStatus("active");
        subscription.setProviderMetadata(providerMetadata(correlation, adminId, reviewNote, now));
        subscription.setUser(user);
        subscription.setProduct(product);
        subscription.setStripeMode(StripeMode.DEFAULT);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setBillingPeriod(billingPeriod(price));
        subscription.setCurrency(DEFAULT_CURRENCY);
        subscription.setPriceAtSubscription(priceAmount(price));
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(periodEnd);
        subscription.setNextBillingDate(periodEnd);
        subscription.setCancelAtPeriodEnd(false);

        Subscription saved = subscriptionRepository.save(subscription);
        correlation.setUser(user);
        correlation.setProduct(product);
        correlation.setSubscription(saved);
        correlation.setStatus(WhopMembershipCorrelationStatus.MATCHED);
        correlation.setReviewReason(null);
        whopMembershipCorrelationRepository.save(correlation);

        markWebhookProcessed(correlation);
        int expiredAttempts = expireStaleCheckoutAttempts(correlation, now);
        adminAuditLogService.recordEvent(
            adminId,
            AUDIT_ACTION_ATTACHED,
            AUDIT_RESOURCE_TYPE,
            correlation.getId().toString(),
            auditDetails(correlation, user, saved, request, reviewNote, expiredAttempts, ipAddress, userAgent)
        );

        return new WhopMembershipAttachResponse(
            correlation.getId(),
            user.getId(),
            saved.getId(),
            correlation.getStatus(),
            expiredAttempts
        );
    }

    @Transactional
    public WhopMembershipReviewDetailDto resolveWithoutAction(
            UUID correlationId,
            String reviewNote,
            UUID adminId,
            String ipAddress,
            String userAgent) {
        WhopMembershipCorrelation correlation = whopMembershipCorrelationRepository.findByIdForUpdate(correlationId)
            .orElseThrow(() -> new WhopMembershipReviewNotFoundException("Whop membership review was not found"));
        ensureReviewable(correlation);

        String normalizedReviewNote = requiredReviewNote(reviewNote);
        correlation.setStatus(WhopMembershipCorrelationStatus.RESOLVED_NO_ACTION);
        correlation.setReviewReason(normalizedReviewNote);
        whopMembershipCorrelationRepository.save(correlation);

        markWebhookProcessed(correlation);
        adminAuditLogService.recordEvent(
            adminId,
            AUDIT_ACTION_RESOLVED_WITHOUT_ACTION,
            AUDIT_RESOURCE_TYPE,
            correlation.getId().toString(),
            resolvedWithoutActionAuditDetails(correlation, normalizedReviewNote, ipAddress, userAgent)
        );

        return toDetail(correlation, candidatesFor(correlation));
    }

    private WhopMembershipReviewDetailDto toDetail(
            WhopMembershipCorrelation correlation,
            List<WhopMembershipCandidateUserDto> candidates) {
        Product product = correlation.getProduct();
        return new WhopMembershipReviewDetailDto(
            correlation.getId(),
            correlation.getWhopMembershipId(),
            correlation.getBuyerEmail(),
            correlation.getStatus(),
            correlation.getReviewReason(),
            product == null ? null : product.getId(),
            product == null ? null : product.getName(),
            correlation.getWhopProductId(),
            correlation.getWhopPlanId(),
            correlation.getCreatedAt(),
            correlation.getUpdatedAt(),
            webhookLedger(correlation),
            checkoutAttempts(correlation),
            candidates
        );
    }

    private WhopMembershipReviewItemDto toItem(WhopMembershipCorrelation correlation) {
        Product product = correlation.getProduct();
        return new WhopMembershipReviewItemDto(
            correlation.getId(),
            correlation.getBuyerEmail(),
            correlation.getStatus(),
            correlation.getReviewReason(),
            product == null ? null : product.getId(),
            product == null ? null : product.getName(),
            correlation.getWhopMembershipId(),
            correlation.getWhopProductId(),
            correlation.getWhopPlanId(),
            correlation.getCreatedAt(),
            correlation.getUpdatedAt()
        );
    }

    private List<WhopMembershipCandidateUserDto> candidatesFor(WhopMembershipCorrelation correlation) {
        if (!StringUtils.hasText(correlation.getBuyerEmail())) {
            return List.of();
        }
        Map<UUID, CandidateAccumulator> candidates = new LinkedHashMap<>();
        userRepository.findByEmail(correlation.getBuyerEmail())
            .ifPresent(user -> candidate(candidates, user).add(new WhopMembershipCandidateEvidenceDto(
                "EMAIL_MATCH",
                "users",
                null,
                "CRM user email matches Whop buyer email"
            )));

        List<WhopCheckoutAttempt> attempts = whopCheckoutAttemptRepository
            .findTop10ByLockedBuyerEmailIgnoreCaseOrderByCreatedAtDesc(correlation.getBuyerEmail());
        for (WhopCheckoutAttempt attempt : attempts) {
            User attemptUser = attempt.getUser();
            if (attemptUser == null || attemptUser.getId() == null) {
                continue;
            }
            String type = correlation.getWhopPlanId() != null
                && correlation.getWhopPlanId().equals(attempt.getWhopPlanId())
                    ? "CHECKOUT_ATTEMPT_MATCH"
                    : "CHECKOUT_ATTEMPT_EMAIL";
            candidate(candidates, attemptUser).add(new WhopMembershipCandidateEvidenceDto(
                type,
                "whop_checkout_attempts",
                attempt.getCreatedAt(),
                "Checkout attempt used the same locked buyer email"
            ));
        }

        return candidates.values().stream()
            .sorted(Comparator.comparing(CandidateAccumulator::score).reversed())
            .limit(3)
            .map(CandidateAccumulator::toDto)
            .toList();
    }

    private WhopMembershipWebhookLedgerDto webhookLedger(WhopMembershipCorrelation correlation) {
        if (!StringUtils.hasText(correlation.getLastWebhookEventId())) {
            return null;
        }
        return billingWebhookEventRepository.findByBillingProviderAndExternalEventId(
                BillingProvider.WHOP,
                correlation.getLastWebhookEventId()
            )
            .map(event -> new WhopMembershipWebhookLedgerDto(
                event.getId(),
                event.getExternalEventId(),
                event.getEventType(),
                event.getProcessingStatus(),
                event.getErrorMessage(),
                event.getReceivedAt(),
                event.getProcessedAt()
            ))
            .orElse(null);
    }

    private List<WhopMembershipCheckoutAttemptDto> checkoutAttempts(WhopMembershipCorrelation correlation) {
        if (!StringUtils.hasText(correlation.getBuyerEmail())) {
            return List.of();
        }
        return whopCheckoutAttemptRepository
            .findTop10ByLockedBuyerEmailIgnoreCaseOrderByCreatedAtDesc(correlation.getBuyerEmail())
            .stream()
            .map(attempt -> new WhopMembershipCheckoutAttemptDto(
                attempt.getId(),
                attempt.getUser() == null ? null : attempt.getUser().getId(),
                attempt.getUser() == null ? null : attempt.getUser().getEmail(),
                attempt.getWhopPlanId(),
                attempt.getStatus() == null ? null : attempt.getStatus().name(),
                attempt.getCreatedAt(),
                attempt.getExpiresAt(),
                attempt.getCompletedAt()
            ))
            .toList();
    }

    private CandidateAccumulator candidate(Map<UUID, CandidateAccumulator> candidates, User user) {
        return candidates.computeIfAbsent(user.getId(), ignored -> {
            List<String> blocks = new ArrayList<>();
            if (user.isBanned()) {
                blocks.add("User is banned");
            }
            if (user.isDeleted()) {
                blocks.add("User is deleted");
            }
            List<Subscription> subscriptions = subscriptionRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
            if (subscriptions.stream().anyMatch(Subscription::hasWebsiteAccess)) {
                blocks.add("User already has website access");
            }
            return new CandidateAccumulator(user, new ArrayList<>(), blocks);
        });
    }

    private void ensureReviewable(WhopMembershipCorrelation correlation) {
        if (correlation.getStatus() == WhopMembershipCorrelationStatus.MATCHED
            || correlation.getStatus() == WhopMembershipCorrelationStatus.REVOKED
            || correlation.getStatus() == WhopMembershipCorrelationStatus.RESOLVED_NO_ACTION) {
            throw new WhopMembershipReviewConflictException("Whop membership review is not reviewable");
        }
        if (!StringUtils.hasText(correlation.getWhopMembershipId())) {
            throw new WhopMembershipReviewConflictException("Whop membership id is missing");
        }
    }

    private boolean emailMismatch(String buyerEmail, String userEmail) {
        return StringUtils.hasText(buyerEmail)
            && StringUtils.hasText(userEmail)
            && !buyerEmail.trim().equalsIgnoreCase(userEmail.trim());
    }

    private List<WhopMembershipCorrelationStatus> defaultReviewStatuses() {
        return List.of(
            WhopMembershipCorrelationStatus.PUBLIC_NO_ATTEMPT,
            WhopMembershipCorrelationStatus.PENDING_NO_USER,
            WhopMembershipCorrelationStatus.PENDING_EMAIL_UNAVAILABLE,
            WhopMembershipCorrelationStatus.BUYER_EMAIL_MISMATCH,
            WhopMembershipCorrelationStatus.UNAUTHORIZED_EMAIL,
            WhopMembershipCorrelationStatus.UNMAPPED_PRODUCT,
            WhopMembershipCorrelationStatus.UNMAPPED_PLAN,
            WhopMembershipCorrelationStatus.REJECTED_PRODUCT
        );
    }

    private List<WhopMembershipCorrelationStatus> reviewStatuses(boolean includeResolved) {
        if (includeResolved) {
            return List.of(WhopMembershipCorrelationStatus.values());
        }
        return defaultReviewStatuses();
    }

    private Product productFor(WhopMembershipCorrelation correlation) {
        Product product = correlation.getProduct();
        if (product == null) {
            throw new WhopMembershipReviewConflictException("Whop product mapping is missing");
        }
        if (!Boolean.TRUE.equals(product.getIsActive()) || Boolean.TRUE.equals(product.getIsArchived())) {
            throw new WhopMembershipReviewConflictException("Whop product is inactive or archived");
        }
        return product;
    }

    private ProductPrice priceForPlan(Product product, String planId) {
        if (product.getPrices() == null || product.getPrices().isEmpty()) {
            throw new WhopMembershipReviewConflictException("Whop product has no active mapped price");
        }
        return product.getPrices().stream()
            .filter(price -> Boolean.TRUE.equals(price.getIsActive()))
            .filter(price -> !StringUtils.hasText(planId) || planId.equals(price.getWhopPlanId()))
            .findFirst()
            .orElseThrow(() -> new WhopMembershipReviewConflictException("Whop plan mapping is missing"));
    }

    private BillingPeriod billingPeriod(ProductPrice price) {
        if (price.getBillingPeriod() == null) {
            return BillingPeriod.MONTHLY;
        }
        try {
            return BillingPeriod.valueOf(price.getBillingPeriod().name());
        } catch (IllegalArgumentException ex) {
            return BillingPeriod.MONTHLY;
        }
    }

    private BigDecimal priceAmount(ProductPrice price) {
        if (price.getPriceCents() == null) {
            return null;
        }
        return BigDecimal.valueOf(price.getPriceCents()).movePointLeft(2);
    }

    private LocalDateTime periodEnd(LocalDateTime start, BillingPeriod billingPeriod) {
        return switch (billingPeriod) {
            case ANNUAL -> start.plusYears(1);
            case MONTHLY -> start.plusMonths(1);
        };
    }

    private void markWebhookProcessed(WhopMembershipCorrelation correlation) {
        if (!StringUtils.hasText(correlation.getLastWebhookEventId())) {
            return;
        }
        billingWebhookEventRepository.findByBillingProviderAndExternalEventId(
            BillingProvider.WHOP,
            correlation.getLastWebhookEventId()
        ).ifPresent(BillingWebhookEvent::markProcessed);
    }

    private int expireStaleCheckoutAttempts(WhopMembershipCorrelation correlation, LocalDateTime now) {
        if (!StringUtils.hasText(correlation.getBuyerEmail()) || !StringUtils.hasText(correlation.getWhopPlanId())) {
            return 0;
        }
        List<WhopCheckoutAttempt> attempts = whopCheckoutAttemptRepository.findExpiredCreatedAttemptsForReview(
            correlation.getBuyerEmail(),
            correlation.getWhopPlanId(),
            now
        );
        for (WhopCheckoutAttempt attempt : attempts) {
            attempt.setStatus(WhopCheckoutAttemptStatus.EXPIRED);
        }
        return attempts.size();
    }

    private String providerMetadata(
            WhopMembershipCorrelation correlation,
            UUID adminId,
            String reason,
            LocalDateTime now) {
        return """
            {"manual_attachment":true,"source_correlation_id":"%s","admin_id":"%s","reason":"%s","attached_at":"%s"}\
            """.formatted(
                correlation.getId(),
                adminId,
                jsonSafe(reason),
                now
            );
    }

    private Map<String, Object> auditDetails(
            WhopMembershipCorrelation correlation,
            User user,
            Subscription subscription,
            WhopMembershipAttachRequest request,
            String reviewNote,
            int expiredAttempts,
            String ipAddress,
            String userAgent) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("selectedUserId", user.getId().toString());
        details.put("selectedUserEmail", user.getEmail());
        details.put("membershipId", correlation.getWhopMembershipId());
        details.put("productId", correlation.getProduct() == null ? null : correlation.getProduct().getId().toString());
        details.put("subscriptionId", subscription.getId() == null ? null : subscription.getId().toString());
        details.put("reason", reviewNote);
        details.put("overrideConflicts", request.overrideConflicts());
        details.put("expiredCheckoutAttempts", expiredAttempts);
        details.put("ipAddress", ipAddress);
        details.put("userAgent", userAgent);
        return details;
    }

    private Map<String, Object> resolvedWithoutActionAuditDetails(
            WhopMembershipCorrelation correlation,
            String reviewNote,
            String ipAddress,
            String userAgent) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("membershipId", correlation.getWhopMembershipId());
        details.put("buyerEmail", correlation.getBuyerEmail());
        details.put("whopUserId", correlation.getWhopUserId());
        details.put("whopProductId", correlation.getWhopProductId());
        details.put("whopPlanId", correlation.getWhopPlanId());
        details.put("productId", correlation.getProduct() == null ? null : correlation.getProduct().getId().toString());
        details.put(
            "subscriptionId",
            correlation.getSubscription() == null ? null : correlation.getSubscription().getId().toString()
        );
        details.put("reason", reviewNote);
        details.put("ipAddress", ipAddress);
        details.put("userAgent", userAgent);
        return details;
    }

    private String requiredReviewNote(String value) {
        if (!StringUtils.hasText(value)) {
            throw new WhopMembershipReviewConflictException("Review note is required");
        }
        return value.trim();
    }

    private String normalizedReviewNote(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String jsonSafe(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record CandidateAccumulator(
        User user,
        List<WhopMembershipCandidateEvidenceDto> evidence,
        List<String> blockingReasons
    ) {
        void add(WhopMembershipCandidateEvidenceDto item) {
            evidence.add(item);
        }

        int score() {
            int score = 0;
            for (WhopMembershipCandidateEvidenceDto item : evidence) {
                if ("EMAIL_MATCH".equals(item.type())) {
                    score += 100;
                } else if ("CHECKOUT_ATTEMPT_MATCH".equals(item.type())) {
                    score += 70;
                } else {
                    score += 20;
                }
            }
            return score;
        }

        WhopMembershipCandidateUserDto toDto() {
            return new WhopMembershipCandidateUserDto(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                confidence(),
                List.copyOf(evidence),
                !blockingReasons.isEmpty(),
                List.copyOf(blockingReasons)
            );
        }

        WhopMembershipCandidateConfidence confidence() {
            int score = score();
            if (score >= 100) {
                return WhopMembershipCandidateConfidence.HIGH;
            }
            if (score >= 70) {
                return WhopMembershipCandidateConfidence.MEDIUM;
            }
            return WhopMembershipCandidateConfidence.LOW;
        }
    }

    public static class WhopMembershipReviewNotFoundException extends RuntimeException {
        public WhopMembershipReviewNotFoundException(String message) {
            super(message);
        }
    }

    public static class WhopMembershipReviewConflictException extends RuntimeException {
        public WhopMembershipReviewConflictException(String message) {
            super(message);
        }
    }
}
