package com.marketmono.controller;

import com.marketmono.config.security.UserPrincipal;
import com.marketmono.dto.*;
import com.marketmono.entity.StripeMode;
import com.marketmono.entity.auth.User;
import com.marketmono.service.StripeConfigService;
import com.marketmono.util.HttpUtils;
import com.marketmono.util.PrincipalUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Stripe configuration management (admin only).
 * 
 * Admin endpoints:
 * - GET /api/v1/admin/stripe/config - Get current config (masked keys)
 * - PUT /api/v1/admin/stripe/config - Update Stripe keys
 * - GET /api/v1/admin/stripe/audit-logs - Get audit log history
 * - GET /api/v1/admin/stripe/status - Check if Stripe is configured
 * 
 * Internal endpoints (for Stripe.js initialization):
 * - GET /api/v1/stripe/public-key - Get public key for current mode (authenticated)
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class StripeConfigController {
    
    private final StripeConfigService stripeConfigService;
    
    // ============================================
    // Public/Authenticated Endpoints
    // ============================================
    
    /**
     * Get Stripe public key for frontend initialization.
     * Returns the public key based on user's mode override or default mode.
     * GET /api/v1/stripe/public-key
     */
    @GetMapping("/api/v1/stripe/public-key")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StripePublicKeyResponse> getPublicKey(@AuthenticationPrincipal UserDetails userDetails) {
        log.debug("GET /api/v1/stripe/public-key");
        
        StripeMode mode = resolveEffectiveMode(userDetails);
        
        String publicKey = stripeConfigService.getPublicKey(mode);
        
        if (publicKey == null || publicKey.isBlank()) {
            return ResponseEntity.ok(new StripePublicKeyResponse(null, mode.name(), false));
        }
        
        return ResponseEntity.ok(new StripePublicKeyResponse(publicKey, mode.name(), true));
    }

    private StripeMode resolveEffectiveMode(UserDetails userDetails) {
        if (userDetails instanceof UserPrincipal userPrincipal) {
            return resolveEffectiveMode(userPrincipal.getUser());
        }
        if (userDetails instanceof User user) {
            return resolveEffectiveMode(user);
        }
        return stripeConfigService.getDefaultMode();
    }

    private StripeMode resolveEffectiveMode(User user) {
        StripeMode userMode = user.getStripeMode();
        if (userMode == null || userMode == StripeMode.DEFAULT) {
            return stripeConfigService.getDefaultMode();
        }
        return userMode;
    }
    
    /**
     * Check if Stripe is configured (for public UI).
     * GET /api/v1/stripe/status
     */
    @GetMapping("/api/v1/stripe/status")
    public ResponseEntity<StripeStatusResponse> getStripeStatus() {
        log.debug("GET /api/v1/stripe/status");
        boolean configured = stripeConfigService.isConfigured();
        var mode = stripeConfigService.getDefaultMode();
        return ResponseEntity.ok(new StripeStatusResponse(configured, mode.name()));
    }
    
    // ============================================
    // Admin Endpoints
    // ============================================
    
    /**
     * Get current Stripe configuration (admin view, masked keys).
     * GET /api/v1/admin/stripe/config
     */
    @GetMapping("/api/v1/admin/stripe/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StripeConfigDto> getConfig() {
        log.debug("GET /api/v1/admin/stripe/config");
        StripeConfigDto config = stripeConfigService.getConfig();
        return ResponseEntity.ok(config);
    }
    
    /**
     * Update Stripe configuration.
     * PUT /api/v1/admin/stripe/config
     */
    @PutMapping("/api/v1/admin/stripe/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StripeConfigDto> updateConfig(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateStripeConfigRequest request,
            HttpServletRequest httpRequest) {
        UUID adminId = PrincipalUtils.requireAdminConfigActorId(userDetails);
        String ipAddress = HttpUtils.getClientIpWithRealIpFallback(httpRequest);
        
        log.info("PUT /api/v1/admin/stripe/config - adminId: {}, ip: {}", adminId, ipAddress);
        
        StripeConfigDto updated = stripeConfigService.updateConfig(request, adminId, ipAddress);
        return ResponseEntity.ok(updated);
    }
    
    /**
     * Get recent audit logs for Stripe config changes.
     * GET /api/v1/admin/stripe/audit-logs
     */
    @GetMapping("/api/v1/admin/stripe/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StripeConfigAuditLogDto>> getAuditLogs(
            @RequestParam(required = false, defaultValue = "false") boolean all) {
        log.debug("GET /api/v1/admin/stripe/audit-logs - all: {}", all);
        
        List<StripeConfigAuditLogDto> logs = all 
            ? stripeConfigService.getAllAuditLogs() 
            : stripeConfigService.getRecentAuditLogs();
        
        return ResponseEntity.ok(logs);
    }
    
    /**
     * Get configuration with public keys visible (for admin testing).
     * GET /api/v1/admin/stripe/config/with-public-keys
     */
    @GetMapping("/api/v1/admin/stripe/config/with-public-keys")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StripeConfigDto> getConfigWithPublicKeys() {
        log.debug("GET /api/v1/admin/stripe/config/with-public-keys");
        StripeConfigDto config = stripeConfigService.getConfigWithPublicKeys();
        return ResponseEntity.ok(config);
    }
    
    // ============================================
    // Helper Methods
    // ============================================
    
    // ============================================
    // Response DTOs
    // ============================================
    
    /**
     * Response for public key endpoint.
     */
    public record StripePublicKeyResponse(
        String publicKey,
        String mode,
        boolean configured
    ) {}
    
    /**
     * Response for Stripe status check.
     */
    public record StripeStatusResponse(
        boolean configured,
        String defaultMode
    ) {}
}
