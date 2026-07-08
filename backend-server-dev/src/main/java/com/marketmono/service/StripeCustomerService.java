package com.marketmono.service;

import com.marketmono.entity.StripeMode;
import com.marketmono.entity.auth.User;
import com.marketmono.exception.StripeBillingException;
import com.marketmono.repository.auth.UserRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for managing Stripe customer creation and retrieval.
 * Ensures users have Stripe Customer IDs in the appropriate mode (TEST/PROD).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeCustomerService {
    
    private final StripeConfigService stripeConfigService;
    private final UserRepository userRepository;
    
    /**
     * Ensure user has a Stripe customer ID in the effective mode.
     * Creates a new customer if one doesn't exist.
     * 
     * @param userId The user ID to check/create customer for
     * @return The Stripe customer ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String ensureCustomerExists(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        
        return ensureCustomerExists(user);
    }
    
    /**
     * Ensure user has a Stripe customer ID in the effective mode.
     * Creates a new customer if one doesn't exist.
     * 
     * @param user The user to check/create customer for
     * @return The Stripe customer ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String ensureCustomerExists(User user) {
        // Resolve effective mode (DEFAULT → global default)
        StripeMode effectiveMode = resolveEffectiveMode(user);
        
        // Check if customer ID already exists for this mode
        String customerId = getCustomerIdForMode(user, effectiveMode);
        if (customerId != null && !customerId.isBlank()) {
            log.debug("User {} already has Stripe customer ID in {} mode: {}", 
                user.getEmail(), effectiveMode, customerId);
            return customerId;
        }
        
        // Create new Stripe customer
        try {
            customerId = createStripeCustomer(user, effectiveMode);
            
            // Save customer ID to user entity
            setCustomerIdForMode(user, effectiveMode, customerId);
            userRepository.save(user);
            
            log.info("Created Stripe customer for user {} in {} mode: {}", 
                user.getEmail(), effectiveMode, customerId);
            
            return customerId;
            
        } catch (StripeException e) {
            log.error("Failed to create Stripe customer for user {}: {}", user.getEmail(), e.getMessage());
            throw new StripeBillingException("Failed to create Stripe customer: " + e.getMessage(), e);
        }
    }
    
    /**
     * Create a new Stripe customer via API.
     */
    private String createStripeCustomer(User user, StripeMode mode) throws StripeException {
        // Build request options with API key scoped to this request (thread-safe)
        String secretKey = stripeConfigService.getSecretKey(mode);
        RequestOptions requestOptions = RequestOptions.builder().setApiKey(secretKey).build();
        
        // Build customer creation params
        CustomerCreateParams params = CustomerCreateParams.builder()
            .setEmail(user.getEmail())
            .setName(user.getDisplayName() != null ? user.getDisplayName() : user.getEmail())
            .putMetadata("user_id", user.getId().toString())
            .putMetadata("mode", mode.name())
            .build();
        
        // Create customer
        Customer customer = Customer.create(params, requestOptions);
        
        return customer.getId();
    }
    
    /**
     * Resolve user's effective Stripe mode.
     * DEFAULT → resolves to global default mode from stripe_config.
     */
    private StripeMode resolveEffectiveMode(User user) {
        StripeMode userMode = user.getStripeMode();
        if (userMode == null || userMode == StripeMode.DEFAULT) {
            return stripeConfigService.getDefaultMode();
        }
        return userMode;
    }
    
    /**
     * Get customer ID for the specified mode.
     */
    public String getCustomerIdForMode(User user, StripeMode mode) {
        return mode == StripeMode.PROD 
            ? user.getStripeCustomerIdProd() 
            : user.getStripeCustomerIdTest();
    }
    
    /**
     * Get customer ID for the user's effective mode.
     */
    public String getCustomerIdForEffectiveMode(User user) {
        StripeMode effectiveMode = resolveEffectiveMode(user);
        return getCustomerIdForMode(user, effectiveMode);
    }
    
    /**
     * Set customer ID for the specified mode.
     */
    private void setCustomerIdForMode(User user, StripeMode mode, String customerId) {
        if (mode == StripeMode.PROD) {
            user.setStripeCustomerIdProd(customerId);
        } else {
            user.setStripeCustomerIdTest(customerId);
        }
    }
}
