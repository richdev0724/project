package com.marketmono.controller.auth;

import com.marketmono.config.security.UserPrincipal;
import com.marketmono.dto.auth.TwoFactorCodeRequest;
import com.marketmono.dto.auth.TwoFactorDisableRequest;
import com.marketmono.dto.auth.TwoFactorEnableResponse;
import com.marketmono.dto.auth.TwoFactorStatusResponse;
import com.marketmono.entity.auth.User;
import com.marketmono.service.auth.TwoFactorService;
import com.marketmono.service.auth.TwoFactorService.TwoFactorSetupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for Two-Factor Authentication (2FA) management.
 */
@RestController
@RequestMapping("/api/v1/auth/2fa")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Two-Factor Authentication", description = "2FA setup, verification, and management")
@SecurityRequirement(name = "bearerAuth")
public class TwoFactorController {
    
    private final TwoFactorService twoFactorService;
    
    /**
     * Get 2FA status for the current user.
     */
    @GetMapping("/status")
    @Operation(summary = "Get 2FA status", description = "Check if 2FA is enabled for the current user")
    public ResponseEntity<TwoFactorStatusResponse> getStatus(@AuthenticationPrincipal UserPrincipal principal) {
        User user = principal.getUser();
        boolean enabled = user.getTwoFactorEnabled();
        int remainingRecoveryCodes = enabled ? twoFactorService.getRemainingRecoveryCodeCount(user) : 0;
        
        return ResponseEntity.ok(new TwoFactorStatusResponse(
            enabled,
            remainingRecoveryCodes
        ));
    }
    
    /**
     * Initialize 2FA setup.
     * Returns QR code and manual entry key for authenticator app.
     */
    @PostMapping("/setup")
    @Operation(summary = "Initialize 2FA setup", description = "Generate secret and QR code for authenticator app")
    public ResponseEntity<TwoFactorSetupResponse> initializeSetup(@AuthenticationPrincipal UserPrincipal principal) {
        User user = principal.getUser();
        TwoFactorSetupResponse response = twoFactorService.initializeSetup(user);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Verify TOTP code and enable 2FA.
     * Returns recovery codes after successful verification.
     */
    @PostMapping("/verify")
    @Operation(summary = "Verify and enable 2FA", description = "Verify TOTP code and enable 2FA, returns recovery codes")
    public ResponseEntity<TwoFactorEnableResponse> verifyAndEnable(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody TwoFactorCodeRequest request) {
        
        User user = principal.getUser();
        List<String> recoveryCodes = twoFactorService.verifyAndEnable(user, request.code());
        
        return ResponseEntity.ok(new TwoFactorEnableResponse(
            true,
            recoveryCodes,
            "2FA has been enabled. Save your recovery codes in a safe place."
        ));
    }
    
    /**
     * Disable 2FA for the current user.
     * Requires password verification.
     */
    @PostMapping("/disable")
    @Operation(summary = "Disable 2FA", description = "Disable 2FA (requires password verification)")
    public ResponseEntity<Map<String, Object>> disable(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody TwoFactorDisableRequest request) {
        
        User user = principal.getUser();
        twoFactorService.disable(user, request.password());
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "2FA has been disabled"
        ));
    }
    
    /**
     * Regenerate recovery codes.
     * Invalidates all existing recovery codes.
     */
    @PostMapping("/recovery-codes/regenerate")
    @Operation(summary = "Regenerate recovery codes", description = "Generate new recovery codes (invalidates existing ones)")
    public ResponseEntity<TwoFactorEnableResponse> regenerateRecoveryCodes(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody TwoFactorCodeRequest request) {
        
        User user = principal.getUser();
        List<String> recoveryCodes = twoFactorService.regenerateRecoveryCodes(user, request.code());
        
        return ResponseEntity.ok(new TwoFactorEnableResponse(
            true,
            recoveryCodes,
            "New recovery codes generated. Previous codes are now invalid."
        ));
    }
}
