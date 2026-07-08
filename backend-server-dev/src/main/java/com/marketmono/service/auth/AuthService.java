package com.marketmono.service.auth;

import com.marketmono.config.security.JwtTokenProvider;
import com.marketmono.dto.auth.*;
import com.marketmono.entity.StripeConfig;
import com.marketmono.service.HonorCodeException;
import com.marketmono.service.HonorCodeService;
import com.marketmono.service.ReferralService;
import com.marketmono.service.StripeConfigService;
import com.marketmono.service.WhopMembershipReconciliationService;
import com.marketmono.entity.auth.*;
import com.marketmono.repository.auth.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Authentication service for user registration, login, and token management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String GOOGLE_PROVIDER = "GOOGLE";
    private static final String REGISTRATION_SUCCESS_MESSAGE =
        "Registration successful. Please check your email to verify your account.";
    private static final String EMAIL_VERIFIED_MESSAGE = "Email verified successfully. You can now log in.";
    private static final String EMAIL_VERIFIED_AUTO_LOGIN_MESSAGE = "Email verified successfully. You are now logged in.";
    private static final String EMAIL_VERIFIED_TWO_FACTOR_MESSAGE =
        "Email verified successfully. Complete two-factor authentication to finish signing in.";
    private static final String DEFAULT_BETA_PROMO_CODE = "BETAMM2026";
    
    private final UserRepository userRepository;
    private final OAuth2ProviderRepository oauth2ProviderRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PendingTwoFactorLoginRepository pendingTwoFactorLoginRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final EmailValidationService emailValidationService;
    private final TwoFactorService twoFactorService;
    private final ReferralService referralService;
    private final StripeConfigService stripeConfigService;
    private final RegistrationModeService registrationModeService;
    private final CustomerInviteService customerInviteService;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final GoogleOAuthProperties googleOAuthProperties;
    private final EmailVerificationContinuationTokenService emailVerificationContinuationTokenService;
    private final WhopMembershipReconciliationService whopMembershipReconciliationService;
    private final HonorCodeService honorCodeService;
    
    /**
     * Register a new user.
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        registrationModeService.requireOpenForRegistration();

        String email = normalizeEmail(request.email());
        String displayName = normalizeDisplayName(request.displayName());

        // Validate email for disposable domains
        emailValidationService.validateEmail(email);

        rejectIfEmailBanned(email);
        
        // Validate password (minimum 12 characters per NIST 800-63B)
        if (request.password().length() < 12) {
            throw new IllegalArgumentException("Password must be at least 12 characters");
        }

        User user = userRepository.findByEmailForUpdate(email).orElse(null);
        if (user != null && user.getRegistrationStatus() != UserRegistrationStatus.WAITLISTED) {
            throw new IllegalArgumentException("Email already registered");
        }

        if (user == null) {
            user = User.builder()
                .email(email)
                .displayName(displayName)
                .enabled(false) // Will be enabled after email verification
                .build();
            user.setEmail(email); // This also sets emailLowercase
        } else {
            user.setEmail(email);
            if (displayName != null) {
                user.setDisplayName(displayName);
            }
        }

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRegistrationStatus(UserRegistrationStatus.PENDING_EMAIL_VERIFICATION);
        user.setEnabled(false);
        user.setEmailVerified(false);
        if (!user.hasRole(UserRoleType.USER)) {
            user.addRole(UserRoleType.USER);
        }

        String honorPromoCode = HonorCodeService.isHonorPromoCode(request.promoCode())
            ? HonorCodeService.normalizeHonorCode(request.promoCode())
            : null;
        if (honorPromoCode == null) {
            applyRegistrationPromoCode(user, request.promoCode());
        }
        
        user = userRepository.save(user);
        if (honorPromoCode != null) {
            honorCodeService.redeemForUser(honorPromoCode, user);
            user = userRepository.save(user);
        }
        whopMembershipReconciliationService.reconcilePendingMemberships(user);
        referralService.ensureReferralCode(user);
        referralService.attachReferral(user, request.referralCode());
        
        // Create and send verification token
        EmailVerificationToken token = EmailVerificationToken.create(user);
        EmailVerificationToken savedToken = emailVerificationTokenRepository.save(token);
        if (savedToken != null) {
            token = savedToken;
        }
        EmailVerificationContinuationToken continuationToken =
            emailVerificationContinuationTokenService.create(user, token);
        emailService.sendVerificationEmail(user, token);
        
        log.info("User registered: {}", email);
        return RegisterResponse.success(REGISTRATION_SUCCESS_MESSAGE, continuationToken);
    }

    /**
     * Reject a banned email before controller-level controls such as IP rate limiting
     * can mask the support message required for banned accounts.
     */
    public void rejectIfEmailBanned(String email) {
        if (isEmailBanned(email)) {
            throw new IllegalArgumentException(AuthFailureMessages.BANNED_EMAIL_SUPPORT_MESSAGE);
        }
    }
    
    /**
     * Verify user email with token.
     */
    @Transactional
    public VerifyEmailResponse verifyEmail(String tokenString) {
        return verifyEmail(tokenString, null, null, null);
    }

    /**
     * Verify user email with token and optionally continue same-browser registration into login.
     */
    @Transactional
    public VerifyEmailResponse verifyEmail(
            String tokenString,
            String emailVerificationContinuationToken,
            String userAgent,
            String ipAddress) {
        EmailVerificationToken token = emailVerificationTokenRepository.findByToken(tokenString)
            .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));
        
        if (!token.isValid()) {
            throw new IllegalArgumentException("Verification token is expired or already used");
        }
        
        User user = token.getUser();
        user.setEmailVerified(true);
        user.setEnabled(true);
        user.setRegistrationStatus(UserRegistrationStatus.REGISTERED);

        boolean continuationMatches = emailVerificationContinuationTokenService.matches(
            emailVerificationContinuationToken,
            user,
            token
        );
        if (continuationMatches) {
            user.recordSuccessfulLogin();
        }

        userRepository.save(user);
        whopMembershipReconciliationService.reconcilePendingMemberships(user);
        
        token.markVerified();
        emailVerificationTokenRepository.save(token);
        
        log.info("Email verified for user: {}", user.getEmail());

        if (!continuationMatches) {
            return VerifyEmailResponse.verifiedOnly(EMAIL_VERIFIED_MESSAGE);
        }

        if (twoFactorService.isTwoFactorRequired(user)) {
            PendingTwoFactorLogin pending = PendingTwoFactorLogin.create(user, userAgent, ipAddress);
            pendingTwoFactorLoginRepository.save(pending);

            log.info("2FA required after email verification for user: {}", user.getEmail());
            return VerifyEmailResponse.twoFactorRequired(EMAIL_VERIFIED_TWO_FACTOR_MESSAGE, pending.getToken());
        }

        return VerifyEmailResponse.autoLoggedIn(
            EMAIL_VERIFIED_AUTO_LOGIN_MESSAGE,
            completeLogin(user, userAgent, ipAddress)
        );
    }
    
    /**
     * Login user and return tokens.
     * If 2FA is enabled, returns a pending 2FA token instead of final tokens.
     */
    @Transactional
    public LoginResponse login(LoginRequest request, String userAgent, String ipAddress) {
        String email = normalizeEmail(request.email());
        if (isEmailBanned(email)) {
            throw new BadCredentialsException(AuthFailureMessages.BANNED_EMAIL_SUPPORT_MESSAGE);
        }

        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        
        // Check if account is locked
        if (user.isAccountLocked()) {
            throw new IllegalStateException("Account is temporarily locked. Please try again later.");
        }

        if (user.getRegistrationStatus() == UserRegistrationStatus.WAITLISTED) {
            throw new IllegalStateException("Please complete registration before logging in");
        }

        customerInviteService.ensureTemporaryPasswordUsable(user);
        
        // Check if email is verified
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalStateException("Please verify your email before logging in");
        }
        
        try {
            // Authenticate with Spring Security
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password())
            );
            
            // Record successful login
            user.recordSuccessfulLogin();
            userRepository.save(user);
            
        } catch (BadCredentialsException _) {
            // Record failed login
            user.recordFailedLogin();
            userRepository.save(user);
            throw new BadCredentialsException("Invalid email or password");
        }
        
        // Check if 2FA is required
        if (twoFactorService.isTwoFactorRequired(user)) {
            // Create pending 2FA login
            PendingTwoFactorLogin pending = PendingTwoFactorLogin.create(user, userAgent, ipAddress);
            pendingTwoFactorLoginRepository.save(pending);
            
            log.info("2FA required for user: {}", user.getEmail());
            return LoginResponse.twoFactorRequired(pending.getToken());
        }
        
        // Generate tokens (no 2FA required)
        return completeLogin(user, userAgent, ipAddress);
    }

    /**
     * Login or register a user from a verified Google Identity Services ID token.
     * If 2FA is enabled on a linked account, returns a pending 2FA token.
     */
    @Transactional
    public LoginResponse loginWithGoogle(GoogleLoginRequest request, String userAgent, String ipAddress) {
        GoogleIdTokenClaims claims = googleIdTokenVerifier.verify(request.credential());
        if (!claims.emailVerified()) {
            throw new IllegalArgumentException("Google account email must be verified");
        }

        if (isEmailBanned(claims.email())) {
            throw new BadCredentialsException(AuthFailureMessages.BANNED_EMAIL_SUPPORT_MESSAGE);
        }

        User user = resolveGoogleUser(claims);

        if (user.isDeleted()) {
            throw new IllegalStateException("User account is disabled");
        }
        if (user.isAccountLocked()) {
            throw new IllegalStateException("Account is temporarily locked. Please try again later.");
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new IllegalStateException("User account is disabled");
        }

        customerInviteService.ensureTemporaryPasswordUsable(user);

        user.recordSuccessfulLogin();
        userRepository.save(user);
        whopMembershipReconciliationService.reconcilePendingMemberships(user);

        if (twoFactorService.isTwoFactorRequired(user)) {
            PendingTwoFactorLogin pending = PendingTwoFactorLogin.create(user, userAgent, ipAddress);
            pendingTwoFactorLoginRepository.save(pending);

            log.info("2FA required for Google user: {}", user.getEmail());
            return LoginResponse.twoFactorRequired(pending.getToken());
        }

        customerInviteService.markAcceptedIfNeeded(user);
        userRepository.save(user);

        return completeLogin(user, userAgent, ipAddress);
    }

    private User resolveGoogleUser(GoogleIdTokenClaims claims) {
        OAuth2Provider linkedProvider = oauth2ProviderRepository
            .findByProviderAndProviderUserIdForUpdate(GOOGLE_PROVIDER, claims.subject())
            .orElse(null);

        if (linkedProvider != null) {
            return linkedProvider.getUser();
        }

        User user = userRepository.findByEmailForUpdate(claims.email()).orElse(null);
        if (user == null) {
            registrationModeService.requireOpenForRegistration();
            user = createGoogleUser(claims);
        } else {
            if (requiresRegistrationOpen(user)) {
                registrationModeService.requireOpenForRegistration();
            }
            if (!googleOAuthProperties.isAutoLinkUsers()) {
                throw new IllegalStateException("Google account linking is disabled");
            }
            applyVerifiedGoogleIdentity(user, claims);
            user = userRepository.save(user);
            user = referralService.ensureReferralCode(user);
        }

        oauth2ProviderRepository.save(OAuth2Provider.link(user, GOOGLE_PROVIDER, claims.subject()));
        return user;
    }

    private boolean requiresRegistrationOpen(User user) {
        return user.getRegistrationStatus() != UserRegistrationStatus.REGISTERED;
    }

    private User createGoogleUser(GoogleIdTokenClaims claims) {
        if (!googleOAuthProperties.isAutoCreateUsers()) {
            throw new IllegalStateException("Google account registration is disabled");
        }

        User user = User.builder()
            .email(claims.email())
            .displayName(normalizeDisplayName(claims.name()))
            .registrationStatus(UserRegistrationStatus.REGISTERED)
            .enabled(true)
            .emailVerified(true)
            .build();
        user.setEmail(claims.email());
        user.addRole(UserRoleType.USER);

        user = userRepository.save(user);
        return referralService.ensureReferralCode(user);
    }

    private void applyVerifiedGoogleIdentity(User user, GoogleIdTokenClaims claims) {
        if (user.getRegistrationStatus() == UserRegistrationStatus.WAITLISTED
            || user.getRegistrationStatus() == UserRegistrationStatus.PENDING_EMAIL_VERIFICATION) {
            user.setRegistrationStatus(UserRegistrationStatus.REGISTERED);
            user.setEnabled(true);
        }

        user.setEmailVerified(true);
        if (normalizeDisplayName(user.getDisplayName()) == null) {
            user.setDisplayName(normalizeDisplayName(claims.name()));
        }
        if (!user.hasRole(UserRoleType.USER)) {
            user.addRole(UserRoleType.USER);
        }
    }
    
    /**
     * Complete login with 2FA verification.
     * Used when user provides TOTP or recovery code after initial login.
     */
    @Transactional
    public LoginResponse verifyTwoFactor(String twoFactorToken, String code, boolean isRecoveryCode) {
        PendingTwoFactorLogin pending = pendingTwoFactorLoginRepository.findByToken(twoFactorToken)
            .orElseThrow(() -> new IllegalArgumentException("Invalid or expired 2FA session"));
        
        if (!pending.isValid()) {
            throw new IllegalArgumentException("2FA session has expired. Please login again.");
        }
        
        User user = pending.getUser();
        
        // Verify the code
        boolean verified;
        if (isRecoveryCode) {
            verified = twoFactorService.verifyRecoveryCode(user, code);
        } else {
            verified = twoFactorService.verifyTotpCode(user, code);
        }
        
        if (!verified) {
            throw new BadCredentialsException("Invalid verification code");
        }
        
        // Mark pending login as completed
        pending.markCompleted();
        pendingTwoFactorLoginRepository.save(pending);
        
        log.info("2FA verified for user: {}", user.getEmail());
        
        // Complete the login
        return completeLogin(user, pending.getUserAgent(), pending.getIpAddress());
    }
    
    /**
     * Complete login by generating tokens.
     */
    private LoginResponse completeLogin(User user, String userAgent, String ipAddress) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        RefreshToken refreshToken = RefreshToken.create(user, userAgent, ipAddress);
        refreshTokenRepository.save(refreshToken);
        
        log.info("User logged in: {}", user.getEmail());
        
        return LoginResponse.success(
            accessToken,
            refreshToken.getToken(),
            UserDto.fromUser(user)
        );
    }

    /**
     * Refresh access token.
     */
    @Transactional
    public String refreshAccessToken(String refreshTokenString) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenString)
            .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        
        if (!refreshToken.isValid()) {
            throw new IllegalArgumentException("Refresh token is expired or revoked");
        }
        
        User user = refreshToken.getUser();
        if (user.isDeleted() || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new IllegalStateException("User account is disabled");
        }
        
        return jwtTokenProvider.generateAccessToken(user);
    }
    
    /**
     * Logout user (revoke refresh token).
     */
    @Transactional
    public void logout(String refreshTokenString) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenString)
            .orElse(null);
        
        if (refreshToken != null) {
            refreshToken.revoke();
            refreshTokenRepository.save(refreshToken);
            log.info("User logged out: {}", refreshToken.getUser().getEmail());
        }
    }
    
    /**
     * Logout from all devices.
     */
    @Transactional
    public void logoutAll(User user) {
        int revokedCount = refreshTokenRepository.revokeAllByUser(user);
        log.info("Revoked {} refresh tokens for user: {}", revokedCount, user.getEmail());
    }
    
    /**
     * Request password reset.
     */
    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        
        // Always return success to prevent email enumeration
        if (user == null) {
            log.warn("Password reset requested for non-existent email: {}", email);
            return;
        }

        if (user.getRegistrationStatus() == UserRegistrationStatus.WAITLISTED) {
            log.info("Password reset ignored for waitlisted email: {}", email);
            return;
        }
        
        // Check rate limiting (1 request per 5 minutes)
        if (passwordResetTokenRepository.hasRecentToken(user, LocalDateTime.now().minusMinutes(5))) {
            log.warn("Password reset rate limited for: {}", email);
            return;
        }
        
        PasswordResetToken token = PasswordResetToken.create(user);
        passwordResetTokenRepository.save(token);
        emailService.sendPasswordResetEmail(user, token);
        
        log.info("Password reset email sent to: {}", email);
    }
    
    /**
     * Reset password with token.
     */
    @Transactional
    public void resetPassword(String tokenString, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository.findByToken(tokenString)
            .orElseThrow(() -> new IllegalArgumentException("Invalid reset token"));
        
        if (!token.isValid()) {
            throw new IllegalArgumentException("Reset token is expired or already used");
        }
        
        // Validate new password
        if (newPassword.length() < 12) {
            throw new IllegalArgumentException("Password must be at least 12 characters");
        }
        
        User user = token.getUser();
        if (user.getRegistrationStatus() == UserRegistrationStatus.WAITLISTED) {
            throw new IllegalStateException("Please complete registration before resetting your password");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);
        
        // Invalidate all password reset tokens for this user
        passwordResetTokenRepository.invalidateAllByUser(user, LocalDateTime.now());
        
        // Revoke all refresh tokens (force re-login on all devices)
        refreshTokenRepository.revokeAllByUser(user);
        
        log.info("Password reset for user: {}", user.getEmail());
    }
    
    /**
     * Resend verification email.
     */
    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getRegistrationStatus() == UserRegistrationStatus.WAITLISTED) {
            throw new IllegalStateException("Please complete registration before requesting verification email");
        }
        
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalStateException("Email is already verified");
        }
        
        // Check rate limiting (1 request per 5 minutes)
        if (emailVerificationTokenRepository.hasRecentToken(user, LocalDateTime.now().minusMinutes(5))) {
            throw new IllegalStateException("Please wait before requesting another verification email");
        }
        
        EmailVerificationToken token = EmailVerificationToken.create(user);
        emailVerificationTokenRepository.save(token);
        emailService.sendVerificationEmail(user, token);
        
        log.info("Verification email resent to: {}", email);
    }
    
    /**
     * Change password for authenticated user.
     */
    @Transactional
    public void changePassword(User user, String currentPassword, String newPassword) {
        // Verify current password
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        
        // Validate new password
        if (newPassword.length() < 12) {
            throw new IllegalArgumentException("Password must be at least 12 characters");
        }
        
        // Check that new password is different from current
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from current password");
        }
        
        // Update password
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        customerInviteService.markAcceptedIfNeeded(user);
        userRepository.save(user);
        
        // Revoke all refresh tokens except current session (force re-login on other devices)
        refreshTokenRepository.revokeAllByUser(user);
        
        log.info("Password changed for user: {}", user.getEmail());
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim();
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        String normalized = displayName.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    private boolean isEmailBanned(String email) {
        String normalized = normalizeEmail(email);
        return normalized != null
            && userRepository.existsBannedByEmailLowercase(normalized.toLowerCase(Locale.ROOT));
    }

    private void applyRegistrationPromoCode(User user, String promoCode) {
        String normalizedPromoCode = normalizePromoCode(promoCode);
        if (normalizedPromoCode == null) {
            return;
        }

        String betaPromoCode = stripeConfigService.getValue(
            StripeConfig.BETA_PROMO_CODE,
            DEFAULT_BETA_PROMO_CODE
        );
        String normalizedBetaPromoCode = normalizePromoCode(betaPromoCode);
        if (!normalizedPromoCode.equals(normalizedBetaPromoCode)) {
            return;
        }

        user.setBetaUser(true);
        UserBetaAccess betaAccess = user.ensureBetaAccess();
        if (betaAccess.getBetaSignedUpAt() == null) {
            betaAccess.setBetaSignedUpAt(LocalDateTime.now());
        }
        betaAccess.setBetaPromoCode(betaPromoCode);
    }

    private String normalizePromoCode(String promoCode) {
        if (promoCode == null) {
            return null;
        }
        String normalized = promoCode.trim().replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        return normalized.isBlank() ? null : normalized;
    }
}
