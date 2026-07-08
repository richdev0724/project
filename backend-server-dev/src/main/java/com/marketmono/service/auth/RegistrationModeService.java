package com.marketmono.service.auth;

import com.marketmono.dto.registration.RegistrationModeResponse;
import com.marketmono.entity.StripeConfig;
import com.marketmono.entity.auth.RegistrationMode;
import com.marketmono.repository.StripeConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * Reads and updates the global public registration mode.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationModeService {

    private static final RegistrationMode FALLBACK_MODE = RegistrationMode.WAITLIST;

    private final StripeConfigRepository stripeConfigRepository;

    @Transactional(readOnly = true)
    public RegistrationModeResponse getRegistrationMode() {
        return stripeConfigRepository.findByName(StripeConfig.REGISTRATION_MODE)
            .map(config -> RegistrationModeResponse.from(parseMode(config.getValue()), config.getUpdatedAt()))
            .orElseGet(() -> RegistrationModeResponse.from(FALLBACK_MODE, null));
    }

    @Transactional
    public RegistrationModeResponse updateRegistrationMode(RegistrationMode mode, UUID adminId) {
        if (mode == null) {
            throw new IllegalArgumentException("Registration mode is required");
        }

        RegistrationMode oldMode = getRegistrationMode().mode();
        stripeConfigRepository.upsertConfig(StripeConfig.REGISTRATION_MODE, mode.name());
        log.info("Registration mode changed from {} to {} by admin {}", oldMode, mode, adminId);
        return getRegistrationMode();
    }

    @Transactional(readOnly = true)
    public void requireOpenForRegistration() {
        RegistrationMode mode = getRegistrationMode().mode();
        if (mode != RegistrationMode.OPEN) {
            throw new RegistrationModeUnavailableException(messageForRegistration(mode));
        }
    }

    @Transactional(readOnly = true)
    public void requireWaitlistMode() {
        RegistrationMode mode = getRegistrationMode().mode();
        if (mode != RegistrationMode.WAITLIST) {
            throw new RegistrationModeUnavailableException(messageForWaitlist(mode));
        }
    }

    private String messageForRegistration(RegistrationMode mode) {
        return switch (mode) {
            case WAITLIST -> "Registration is currently waitlist-only";
            case CLOSED -> "Registration is currently closed";
            case OPEN -> "";
        };
    }

    private String messageForWaitlist(RegistrationMode mode) {
        return switch (mode) {
            case OPEN -> "Waitlist is currently closed because registration is open";
            case CLOSED -> "Waitlist is currently closed";
            case WAITLIST -> "";
        };
    }

    private RegistrationMode parseMode(String value) {
        if (value == null || value.isBlank()) {
            return FALLBACK_MODE;
        }

        try {
            return RegistrationMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid registration mode config value '{}'; defaulting to {}", value, FALLBACK_MODE);
            return FALLBACK_MODE;
        }
    }

    public static class RegistrationModeUnavailableException extends IllegalStateException {
        public RegistrationModeUnavailableException(String message) {
            super(message);
        }
    }
}
