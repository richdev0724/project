package com.marketmono.controller.auth;

import com.marketmono.config.security.UserPrincipal;
import com.marketmono.dto.auth.*;
import com.marketmono.service.auth.AuthService;
import com.marketmono.service.auth.HCaptchaService;
import com.marketmono.service.auth.RegistrationRateLimitService;
import com.marketmono.util.HttpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication REST controller.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    
    private final AuthService authService;
    private final HCaptchaService hCaptchaService;
    private final RegistrationRateLimitService registrationRateLimitService;
    
    /**
     * Register a new user.
     * 
     * @param request Registration request containing email, password, displayName
     * @param captchaToken Optional hCaptcha token (required if rate limit threshold reached)
     * @param httpRequest HTTP request for IP extraction
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request,
            @RequestHeader(value = "X-Captcha-Token", required = false) String captchaToken,
            HttpServletRequest httpRequest) {

        authService.rejectIfEmailBanned(request.email());
        
        // Check IP rate limit
        registrationRateLimitService.checkRateLimit(httpRequest);
        
        // Verify CAPTCHA if enabled and required
        boolean captchaRequired = hCaptchaService.isEnabled() && 
            (registrationRateLimitService.shouldRequireCaptcha(httpRequest) || captchaToken != null);
        
        if (captchaRequired) {
            hCaptchaService.verify(captchaToken, HttpUtils.getClientIpWithoutRealIpFallback(httpRequest));
        }
        
        try {
            RegisterResponse response = authService.register(request);
            return ResponseEntity.ok(response);
        } finally {
            // Always record attempt (success or failure)
            registrationRateLimitService.recordAttempt(httpRequest);
        }
    }
    
    /**
     * Check if CAPTCHA is required for registration from this IP.
     * Frontend can call this to determine if CAPTCHA widget should be shown.
     */
    @GetMapping("/register/captcha-required")
    public ResponseEntity<Map<String, Object>> isCaptchaRequired(HttpServletRequest httpRequest) {
        boolean required = hCaptchaService.isEnabled() && 
            registrationRateLimitService.shouldRequireCaptcha(httpRequest);
        
        return ResponseEntity.ok(Map.of(
            "captchaRequired", required,
            "siteKey", hCaptchaService.isEnabled() ? hCaptchaService.getSiteKey() : ""
        ));
    }
    
    /**
     * Verify email with token.
     */
    @PostMapping("/verify-email")
    public ResponseEntity<VerifyEmailResponse> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request,
            HttpServletRequest httpRequest) {
        String userAgent = httpRequest.getHeader("User-Agent");
        String ipAddress = HttpUtils.getClientIpWithoutRealIpFallback(httpRequest);

        VerifyEmailResponse response = authService.verifyEmail(
            request.token(),
            request.emailVerificationContinuationToken(),
            userAgent,
            ipAddress
        );
        return ResponseEntity.ok(response);
    }
    
    /**
     * Login and get tokens.
     * If 2FA is enabled, returns requiresTwoFactor=true with a twoFactorToken.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String userAgent = httpRequest.getHeader("User-Agent");
        String ipAddress = HttpUtils.getClientIpWithoutRealIpFallback(httpRequest);
        
        LoginResponse response = authService.login(request, userAgent, ipAddress);
        return ResponseEntity.ok(response);
    }

    /**
     * Login or register with a Google Identity Services ID token.
     */
    @PostMapping("/google")
    public ResponseEntity<LoginResponse> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request,
            HttpServletRequest httpRequest) {
        String userAgent = httpRequest.getHeader("User-Agent");
        String ipAddress = HttpUtils.getClientIpWithoutRealIpFallback(httpRequest);

        LoginResponse response = authService.loginWithGoogle(request, userAgent, ipAddress);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Verify 2FA code and complete login.
     * Call this after receiving requiresTwoFactor=true from /login.
     */
    @PostMapping("/login/2fa")
    public ResponseEntity<LoginResponse> verifyTwoFactor(
            @Valid @RequestBody TwoFactorVerifyRequest request) {
        LoginResponse response = authService.verifyTwoFactor(
            request.twoFactorToken(), 
            request.code(), 
            request.isRecoveryCode()
        );
        return ResponseEntity.ok(response);
    }
    
    /**
     * Refresh access token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        String accessToken = authService.refreshAccessToken(request.refreshToken());
        return ResponseEntity.ok(Map.of("accessToken", accessToken));
    }
    
    /**
     * Logout (revoke refresh token).
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
    
    /**
     * Logout from all devices.
     */
    @PostMapping("/logout-all")
    public ResponseEntity<Map<String, String>> logoutAll(@AuthenticationPrincipal UserPrincipal principal) {
        authService.logoutAll(principal.getUser());
        return ResponseEntity.ok(Map.of("message", "Logged out from all devices"));
    }
    
    /**
     * Request password reset.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        // Always return success to prevent email enumeration
        return ResponseEntity.ok(Map.of(
            "message", "If an account exists with this email, you will receive a password reset link."
        ));
    }
    
    /**
     * Reset password with token.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(Map.of("message", "Password reset successfully. Please log in with your new password."));
    }
    
    /**
     * Resend verification email.
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerificationEmail(request.email());
        return ResponseEntity.ok(Map.of("message", "Verification email sent."));
    }
    
    /**
     * Get current user info.
     */
    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || principal.getUser() == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(UserDto.fromUser(principal.getUser()));
    }
    
    /**
     * Change password for authenticated user.
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        if (principal == null || principal.getUser() == null) {
            return ResponseEntity.status(401).build();
        }
        authService.changePassword(principal.getUser(), request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }
    
}
