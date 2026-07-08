package com.marketmono.service;

import com.marketmono.dto.AdminGrantSubscriptionRequest;
import com.marketmono.dto.honor.HonorCodeValidateResponse;
import com.marketmono.entity.BillingPeriod;
import com.marketmono.entity.HonorCode;
import com.marketmono.entity.HonorCodeStatus;
import com.marketmono.entity.StripeConfig;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.HonorCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class HonorCodeService {

    private static final String INVALID_CODE_MESSAGE =
        "That HONOR code is invalid or has expired.";
    private static final String ALREADY_USED_MESSAGE =
        "This HONOR code has already been used.";
    private static final String EXPIRED_MESSAGE =
        "This HONOR code has expired. Contact support@marketmono.com for assistance.";
    private static final String REVOKED_MESSAGE =
        "This HONOR code is no longer valid.";
    private static final String EMAIL_MISMATCH_MESSAGE =
        "This HONOR code cannot be used with that email address.";
    private static final Pattern HONOR_CODE_PATTERN =
        Pattern.compile("^HONOR-[A-Z0-9]+$");

    private static final int DEFAULT_GRANT_DURATION_DAYS = 3660;

    private final HonorCodeRepository honorCodeRepository;
    private final StripeConfigService stripeConfigService;
    private final SubscriptionService subscriptionService;

    public static String normalizeHonorCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().replaceAll("[^A-Za-z0-9-]", "").toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    public static boolean isHonorPromoCode(String promoCode) {
        String normalized = normalizeHonorCode(promoCode);
        return normalized != null && HONOR_CODE_PATTERN.matcher(normalized).matches();
    }

    @Transactional
    public HonorCodeValidateResponse validate(String rawCode) {
        String normalizedCode = normalizeHonorCode(rawCode);
        if (normalizedCode == null) {
            return HonorCodeValidateResponse.rejected(INVALID_CODE_MESSAGE);
        }

        HonorCode honorCode = honorCodeRepository.findByCode(normalizedCode).orElse(null);
        if (honorCode == null) {
            return HonorCodeValidateResponse.rejected(INVALID_CODE_MESSAGE);
        }

        String rejectionMessage = rejectionMessageFor(honorCode);
        if (rejectionMessage != null) {
            return HonorCodeValidateResponse.rejected(rejectionMessage);
        }

        honorCode.setValidationAttempts(honorCode.getValidationAttempts() + 1);
        honorCodeRepository.save(honorCode);

        return HonorCodeValidateResponse.accepted(normalizedCode);
    }

    @Transactional
    public void redeemForUser(String rawCode, User user) {
        String normalizedCode = normalizeHonorCode(rawCode);
        if (normalizedCode == null) {
            throw new HonorCodeException(INVALID_CODE_MESSAGE);
        }

        HonorCode honorCode = honorCodeRepository.findByCodeForUpdate(normalizedCode)
            .orElseThrow(() -> new HonorCodeException(INVALID_CODE_MESSAGE));

        String rejectionMessage = rejectionMessageFor(honorCode);
        if (rejectionMessage != null) {
            throw new HonorCodeException(rejectionMessage);
        }

        if (honorCode.getEmail() != null
            && !honorCode.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw new HonorCodeException(EMAIL_MISMATCH_MESSAGE);
        }

        LocalDateTime now = LocalDateTime.now();
        honorCode.setStatus(HonorCodeStatus.REDEEMED);
        honorCode.setRedeemedAt(now);
        honorCode.setRedeemedByUser(user);
        honorCodeRepository.save(honorCode);

        user.setHonorUser(true);
        grantComplimentarySubscription(user.getId());

        log.info("Redeemed HONOR code {} for user {}", normalizedCode, user.getEmail());
    }

    private void grantComplimentarySubscription(UUID userId) {
        UUID productId = resolveHonorProductId();
        int durationDays = resolveHonorGrantDurationDays();

        subscriptionService.grantSubscription(
            userId,
            new AdminGrantSubscriptionRequest(
                productId,
                BillingPeriod.MONTHLY,
                durationDays,
                "HONOR program complimentary access"
            )
        );
    }

    private UUID resolveHonorProductId() {
        String configured = stripeConfigService.getValue(StripeConfig.HONOR_COMPLIMENTARY_PRODUCT_ID, null);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("HONOR complimentary product is not configured");
        }
        return UUID.fromString(configured.trim());
    }

    private int resolveHonorGrantDurationDays() {
        String configured = stripeConfigService.getValue(
            StripeConfig.HONOR_GRANT_DURATION_DAYS,
            String.valueOf(DEFAULT_GRANT_DURATION_DAYS)
        );
        try {
            return Integer.parseInt(configured.trim());
        } catch (NumberFormatException ex) {
            return DEFAULT_GRANT_DURATION_DAYS;
        }
    }

    private String rejectionMessageFor(HonorCode honorCode) {
        if (honorCode.getStatus() == HonorCodeStatus.REDEEMED) {
            return ALREADY_USED_MESSAGE;
        }
        if (honorCode.getStatus() == HonorCodeStatus.REVOKED) {
            return REVOKED_MESSAGE;
        }
        if (honorCode.getStatus() == HonorCodeStatus.EXPIRED || honorCode.isExpired()) {
            return EXPIRED_MESSAGE;
        }
        if (honorCode.getStatus() != HonorCodeStatus.ACTIVE) {
            return INVALID_CODE_MESSAGE;
        }
        return null;
    }
}
