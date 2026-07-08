package com.marketmono.service.auth;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.config.security.JwtTokenProvider;
import com.marketmono.dto.auth.GoogleLoginRequest;
import com.marketmono.dto.auth.EmailVerificationContinuationToken;
import com.marketmono.dto.auth.LoginRequest;
import com.marketmono.dto.auth.LoginResponse;
import com.marketmono.dto.auth.RegisterRequest;
import com.marketmono.dto.auth.RegisterResponse;
import com.marketmono.dto.auth.VerifyEmailResponse;
import com.marketmono.entity.StripeConfig;
import com.marketmono.entity.auth.CustomerInviteStatus;
import com.marketmono.entity.auth.EmailVerificationToken;
import com.marketmono.entity.auth.OAuth2Provider;
import com.marketmono.entity.auth.PendingTwoFactorLogin;
import com.marketmono.entity.auth.PasswordResetToken;
import com.marketmono.entity.auth.RefreshToken;
import com.marketmono.entity.auth.User;
import com.marketmono.entity.auth.UserRegistrationStatus;
import com.marketmono.entity.auth.UserRoleType;
import com.marketmono.repository.auth.EmailVerificationTokenRepository;
import com.marketmono.repository.auth.OAuth2ProviderRepository;
import com.marketmono.repository.auth.PasswordResetTokenRepository;
import com.marketmono.repository.auth.PendingTwoFactorLoginRepository;
import com.marketmono.repository.auth.RefreshTokenRepository;
import com.marketmono.repository.auth.UserRepository;
import com.marketmono.service.HonorCodeService;
import com.marketmono.service.ReferralService;
import com.marketmono.service.StripeCustomerService;
import com.marketmono.service.StripeConfigService;
import com.marketmono.service.WhopMembershipReconciliationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String BANNED_EMAIL_SUPPORT_MESSAGE =
        "Error logging in, please contact support@marketmono.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private OAuth2ProviderRepository oauth2ProviderRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PendingTwoFactorLoginRepository pendingTwoFactorLoginRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private EmailService emailService;

    @Mock
    private EmailValidationService emailValidationService;

    @Mock
    private TwoFactorService twoFactorService;

    @Mock
    private ReferralService referralService;

    @Mock
    private StripeCustomerService stripeCustomerService;

    @Mock
    private StripeConfigService stripeConfigService;

    @Mock
    private RegistrationModeService registrationModeService;

    @Mock
    private CustomerInviteService customerInviteService;

    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @Mock
    private GoogleOAuthProperties googleOAuthProperties;

    @Mock
    private EmailVerificationContinuationTokenService emailVerificationContinuationTokenService;

    @Mock
    private WhopMembershipReconciliationService whopMembershipReconciliationService;

    @Mock
    private HonorCodeService honorCodeService;

    @InjectMocks
    private AuthService authService;

    @Test
    void shouldMarkRegisteredUserAsBetaWhenRegistrationPromoCodeMatches() throws Exception {
        when(userRepository.findByEmailForUpdate("beta@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("long-enough-password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(referralService.ensureReferralCode(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(stripeConfigService.getValue(StripeConfig.BETA_PROMO_CODE, "BETAMM2026"))
            .thenReturn("BETAMM2026");

        RegisterRequest request = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readValue("""
                {
                  "email": "beta@example.com",
                  "displayName": "Beta User",
                  "promoCode": "betamm2026",
                  "password": "long-enough-password"
                }
                """, RegisterRequest.class);

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User registered = userCaptor.getValue();
        assertThat(registered.getBetaUser()).isTrue();
        assertThat(registered.getBetaAccess()).isNotNull();
        assertThat(registered.getBetaAccess().getBetaPromoCode()).isEqualTo("BETAMM2026");
        assertThat(registered.getBetaAccess().getBetaSignedUpAt()).isNotNull();
        assertThat(registered.getBetaAccess().getUsedBetaPromo()).isFalse();
        verify(referralService).attachReferral(registered, null);
        verify(honorCodeService, never()).redeemForUser(any(), any());
    }

    @Test
    void shouldRedeemHonorCodeDuringRegistration() {
        when(userRepository.findByEmailForUpdate("hero@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("long-enough-password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(UUID.randomUUID());
            }
            return user;
        });
        when(referralService.ensureReferralCode(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterRequest request = new RegisterRequest(
            "hero@example.com",
            "Hero User",
            null,
            "HONOR-TEST01",
            "long-enough-password"
        );

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.atLeastOnce()).save(userCaptor.capture());
        verify(honorCodeService).redeemForUser("HONOR-TEST01", userCaptor.getValue());
        verify(stripeConfigService, never()).getValue(StripeConfig.BETA_PROMO_CODE, "BETAMM2026");
    }

    @Test
    void shouldUpgradeWaitlistedUserDuringRegistration() {
        User waitlisted = User.builder()
            .id(UUID.randomUUID())
            .email("lead@example.com")
            .displayName("Waitlist Name")
            .registrationStatus(UserRegistrationStatus.WAITLISTED)
            .enabled(false)
            .emailVerified(false)
            .waitlistJoinedAt(LocalDateTime.now().minusDays(1))
            .waitlistSource("landing")
            .build();
        waitlisted.setEmail("lead@example.com");

        when(userRepository.findByEmailForUpdate("Lead@Example.com")).thenReturn(Optional.of(waitlisted));
        when(passwordEncoder.encode("long-enough-password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(referralService.ensureReferralCode(waitlisted)).thenReturn(waitlisted);

        RegisterResponse response = authService.register(new RegisterRequest(
            " Lead@Example.com ",
            "  Registered   Name ",
            "ABC12345",
            "long-enough-password"
        ));

        assertThat(response.message()).isEqualTo("Registration successful. Please check your email to verify your account.");
        assertThat(waitlisted.getEmail()).isEqualTo("Lead@Example.com");
        assertThat(waitlisted.getEmailLowercase()).isEqualTo("lead@example.com");
        assertThat(waitlisted.getDisplayName()).isEqualTo("Registered Name");
        assertThat(waitlisted.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(waitlisted.getRegistrationStatus()).isEqualTo(UserRegistrationStatus.PENDING_EMAIL_VERIFICATION);
        assertThat(waitlisted.getEnabled()).isFalse();
        assertThat(waitlisted.getEmailVerified()).isFalse();
        assertThat(waitlisted.hasRole(UserRoleType.USER)).isTrue();
        assertThat(waitlisted.getWaitlistJoinedAt()).isNotNull();
        assertThat(waitlisted.getWaitlistSource()).isEqualTo("landing");
        verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
        verify(emailService).sendVerificationEmail(any(User.class), any(EmailVerificationToken.class));
        verify(whopMembershipReconciliationService).reconcilePendingMemberships(waitlisted);
        verify(referralService).attachReferral(waitlisted, "ABC12345");
        verify(registrationModeService).requireOpenForRegistration();
    }

    @Test
    void shouldCreateNewUserWhenSoftDeletedUserHasSameEmail() {
        User deleted = User.builder()
            .id(UUID.randomUUID())
            .email("restart@example.com")
            .displayName("Old Name")
            .passwordHash("old-hash")
            .registrationStatus(UserRegistrationStatus.REGISTERED)
            .enabled(true)
            .emailVerified(true)
            .twoFactorEnabled(true)
            .twoFactorSecret("stale-secret")
            .failedLoginAttempts(4)
            .lockedUntil(LocalDateTime.now().plusMinutes(15))
            .deletedAt(LocalDateTime.now().minusDays(1))
            .requiresPasswordChange(true)
            .tempPasswordExpiresAt(LocalDateTime.now().plusHours(2))
            .customerInviteStatus(CustomerInviteStatus.ACTIVE)
            .customerInvitedAt(LocalDateTime.now().minusDays(2))
            .customerInviteLastSentAt(LocalDateTime.now().minusDays(2))
            .build();
        deleted.setEmail("restart@example.com");

        when(userRepository.findByEmailForUpdate("Restart@Example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("long-enough-password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(referralService.ensureReferralCode(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterResponse response = authService.register(new RegisterRequest(
            " Restart@Example.com ",
            "Restarted User",
            null,
            "long-enough-password"
        ));

        assertThat(response.message()).isEqualTo("Registration successful. Please check your email to verify your account.");
        assertThat(deleted.getDeletedAt()).isNotNull();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User registered = userCaptor.getValue();
        assertThat(registered).isNotSameAs(deleted);
        assertThat(registered.getEmail()).isEqualTo("Restart@Example.com");
        assertThat(registered.getDisplayName()).isEqualTo("Restarted User");
        assertThat(registered.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(registered.getRegistrationStatus()).isEqualTo(UserRegistrationStatus.PENDING_EMAIL_VERIFICATION);
        assertThat(registered.getEnabled()).isFalse();
        assertThat(registered.getEmailVerified()).isFalse();
        assertThat(registered.hasRole(UserRoleType.USER)).isTrue();
        verify(refreshTokenRepository, never()).revokeAllByUser(deleted);
        verify(pendingTwoFactorLoginRepository, never()).deleteByUser(deleted);
        verify(passwordResetTokenRepository, never()).invalidateAllByUser(any(User.class), any(LocalDateTime.class));
        verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
        verify(emailService).sendVerificationEmail(any(User.class), any(EmailVerificationToken.class));
        verify(referralService).attachReferral(registered, null);
    }

    @Test
    void shouldReturnContinuationTokenDuringRegistration() {
        LocalDateTime continuationExpiresAt = LocalDateTime.now().plusHours(2);

        when(userRepository.findByEmailForUpdate("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("long-enough-password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(referralService.ensureReferralCode(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(emailVerificationTokenRepository.save(any(EmailVerificationToken.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(emailVerificationContinuationTokenService.create(any(User.class), any(EmailVerificationToken.class)))
            .thenReturn(new EmailVerificationContinuationToken("continuation-token", continuationExpiresAt));

        RegisterResponse response = authService.register(new RegisterRequest(
            "new@example.com",
            "New Person",
            null,
            "long-enough-password"
        ));

        assertThat(response.message()).isEqualTo("Registration successful. Please check your email to verify your account.");
        assertThat(response.emailVerificationContinuationToken()).isEqualTo("continuation-token");
        assertThat(response.emailVerificationContinuationExpiresAt()).isEqualTo(continuationExpiresAt);
        verify(emailVerificationContinuationTokenService).create(any(User.class), any(EmailVerificationToken.class));
    }

    @Test
    void shouldRejectRegistrationWhenRegistrationModeIsNotOpen() {
        doThrow(new RegistrationModeService.RegistrationModeUnavailableException("Registration is currently waitlist-only"))
            .when(registrationModeService)
            .requireOpenForRegistration();

        assertThatThrownBy(() -> authService.register(new RegisterRequest(
            "lead@example.com",
            "Lead",
            null,
            "long-enough-password"
        )))
            .isInstanceOf(RegistrationModeService.RegistrationModeUnavailableException.class)
            .hasMessage("Registration is currently waitlist-only");

        verify(emailValidationService, never()).validateEmail(any());
        verify(userRepository, never()).findByEmailForUpdate(any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldRejectRegistrationForExistingPendingUser() {
        User pending = User.builder()
            .email("pending@example.com")
            .registrationStatus(UserRegistrationStatus.PENDING_EMAIL_VERIFICATION)
            .enabled(false)
            .emailVerified(false)
            .build();
        pending.setEmail("pending@example.com");

        when(userRepository.findByEmailForUpdate("pending@example.com")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> authService.register(new RegisterRequest(
            "pending@example.com",
            "Pending",
            null,
            "long-enough-password"
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Email already registered");

        verify(userRepository, never()).save(any(User.class));
        verify(emailVerificationTokenRepository, never()).save(any(EmailVerificationToken.class));
    }

    @Test
    void shouldRejectRegistrationForBannedEmailWithGenericSupportMessage() {
        when(userRepository.existsBannedByEmailLowercase("blocked@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(
            " Blocked@Example.com ",
            "Blocked",
            null,
            "long-enough-password"
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(BANNED_EMAIL_SUPPORT_MESSAGE);

        verify(userRepository, never()).findByEmailForUpdate(any());
        verify(userRepository, never()).save(any(User.class));
        verify(emailVerificationTokenRepository, never()).save(any(EmailVerificationToken.class));
    }

    @Test
    void shouldMarkUserRegisteredAfterEmailVerification() {
        User user = User.builder()
            .email("pending@example.com")
            .registrationStatus(UserRegistrationStatus.PENDING_EMAIL_VERIFICATION)
            .enabled(false)
            .emailVerified(false)
            .build();
        user.setEmail("pending@example.com");
        EmailVerificationToken token = EmailVerificationToken.builder()
            .user(user)
            .token("verify-token")
            .expiresAt(LocalDateTime.now().plusHours(1))
            .build();

        when(emailVerificationTokenRepository.findByToken("verify-token")).thenReturn(Optional.of(token));

        VerifyEmailResponse response = authService.verifyEmail("verify-token", null, "JUnit", "127.0.0.1");

        assertThat(response.autoLoggedIn()).isFalse();
        assertThat(response.requiresTwoFactor()).isFalse();
        assertThat(response.accessToken()).isNull();
        assertThat(response.refreshToken()).isNull();
        assertThat(user.getEmailVerified()).isTrue();
        assertThat(user.getEnabled()).isTrue();
        assertThat(user.getRegistrationStatus()).isEqualTo(UserRegistrationStatus.REGISTERED);
        verify(userRepository).save(user);
        verify(whopMembershipReconciliationService).reconcilePendingMemberships(user);
        verify(emailVerificationTokenRepository).save(token);
        verify(jwtTokenProvider, never()).generateAccessToken(any());
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void shouldAutoLoginAfterEmailVerificationWhenContinuationTokenMatches() {
        User user = User.builder()
            .id(UUID.randomUUID())
            .email("pending@example.com")
            .registrationStatus(UserRegistrationStatus.PENDING_EMAIL_VERIFICATION)
            .enabled(false)
            .emailVerified(false)
            .build();
        user.setEmail("pending@example.com");
        user.addRole(UserRoleType.USER);
        EmailVerificationToken token = EmailVerificationToken.builder()
            .user(user)
            .token("verify-token")
            .expiresAt(LocalDateTime.now().plusHours(1))
            .build();

        when(emailVerificationTokenRepository.findByToken("verify-token")).thenReturn(Optional.of(token));
        when(emailVerificationContinuationTokenService.matches("continuation-token", user, token)).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("access-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VerifyEmailResponse response = authService.verifyEmail(
            "verify-token",
            "continuation-token",
            "JUnit",
            "203.0.113.10"
        );

        assertThat(response.autoLoggedIn()).isTrue();
        assertThat(response.requiresTwoFactor()).isFalse();
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("pending@example.com");

        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        assertThat(refreshTokenCaptor.getValue().getUserAgent()).isEqualTo("JUnit");
        assertThat(refreshTokenCaptor.getValue().getIpAddress()).isEqualTo("203.0.113.10");
        verifyNoInteractions(stripeCustomerService);
    }

    @Test
    void shouldVerifyOnlyWhenContinuationTokenDoesNotMatch() {
        User user = User.builder()
            .id(UUID.randomUUID())
            .email("pending@example.com")
            .registrationStatus(UserRegistrationStatus.PENDING_EMAIL_VERIFICATION)
            .enabled(false)
            .emailVerified(false)
            .build();
        user.setEmail("pending@example.com");
        user.addRole(UserRoleType.USER);
        EmailVerificationToken token = EmailVerificationToken.builder()
            .user(user)
            .token("verify-token")
            .expiresAt(LocalDateTime.now().plusHours(1))
            .build();

        when(emailVerificationTokenRepository.findByToken("verify-token")).thenReturn(Optional.of(token));
        when(emailVerificationContinuationTokenService.matches("bad-continuation-token", user, token)).thenReturn(false);

        VerifyEmailResponse response = authService.verifyEmail(
            "verify-token",
            "bad-continuation-token",
            "JUnit",
            "203.0.113.10"
        );

        assertThat(response.autoLoggedIn()).isFalse();
        assertThat(response.accessToken()).isNull();
        assertThat(response.refreshToken()).isNull();
        verify(jwtTokenProvider, never()).generateAccessToken(any());
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verifyNoInteractions(stripeCustomerService);
    }

    @Test
    void shouldRequireTwoFactorAfterEmailVerificationWhenUserHasTwoFactorEnabled() {
        User user = User.builder()
            .id(UUID.randomUUID())
            .email("pending@example.com")
            .registrationStatus(UserRegistrationStatus.PENDING_EMAIL_VERIFICATION)
            .enabled(false)
            .emailVerified(false)
            .twoFactorEnabled(true)
            .build();
        user.setEmail("pending@example.com");
        user.addRole(UserRoleType.USER);
        EmailVerificationToken token = EmailVerificationToken.builder()
            .user(user)
            .token("verify-token")
            .expiresAt(LocalDateTime.now().plusHours(1))
            .build();

        when(emailVerificationTokenRepository.findByToken("verify-token")).thenReturn(Optional.of(token));
        when(emailVerificationContinuationTokenService.matches("continuation-token", user, token)).thenReturn(true);
        when(twoFactorService.isTwoFactorRequired(user)).thenReturn(true);
        when(pendingTwoFactorLoginRepository.save(any(PendingTwoFactorLogin.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        VerifyEmailResponse response = authService.verifyEmail(
            "verify-token",
            "continuation-token",
            "JUnit",
            "203.0.113.10"
        );

        assertThat(response.autoLoggedIn()).isFalse();
        assertThat(response.requiresTwoFactor()).isTrue();
        assertThat(response.twoFactorToken()).isNotBlank();
        assertThat(response.accessToken()).isNull();
        assertThat(response.refreshToken()).isNull();
        verify(pendingTwoFactorLoginRepository).save(any(PendingTwoFactorLogin.class));
        verify(jwtTokenProvider, never()).generateAccessToken(any());
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void shouldRecordPasswordChangedAtWhenPasswordIsReset() {
        User user = User.builder()
            .email("person@example.com")
            .registrationStatus(UserRegistrationStatus.REGISTERED)
            .enabled(true)
            .emailVerified(true)
            .build();
        user.setEmail("person@example.com");
        PasswordResetToken token = PasswordResetToken.builder()
            .user(user)
            .token("reset-token")
            .expiresAt(LocalDateTime.now().plusHours(1))
            .build();

        when(passwordResetTokenRepository.findByToken("reset-token")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("new-long-enough-password")).thenReturn("encoded-new-password");

        authService.resetPassword("reset-token", "new-long-enough-password");

        assertThat(user.getPasswordHash()).isEqualTo("encoded-new-password");
        assertThat(user.getPasswordChangedAt()).isNotNull();
        assertThat(user.getPasswordChangedAt()).isBeforeOrEqualTo(LocalDateTime.now());
        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).invalidateAllByUser(any(User.class), any(LocalDateTime.class));
        verify(refreshTokenRepository).revokeAllByUser(user);
    }

    @Test
    void shouldNotIssuePasswordResetForWaitlistedUser() {
        User waitlisted = User.builder()
            .email("lead@example.com")
            .registrationStatus(UserRegistrationStatus.WAITLISTED)
            .enabled(false)
            .emailVerified(false)
            .build();
        waitlisted.setEmail("lead@example.com");

        when(userRepository.findByEmail("lead@example.com")).thenReturn(Optional.of(waitlisted));

        authService.forgotPassword("lead@example.com");

        verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
        verify(emailService, never()).sendPasswordResetEmail(any(User.class), any(PasswordResetToken.class));
    }

    @Test
    void shouldRejectLoginForWaitlistedUserBeforePasswordAuthentication() {
        User waitlisted = User.builder()
            .email("lead@example.com")
            .registrationStatus(UserRegistrationStatus.WAITLISTED)
            .enabled(false)
            .emailVerified(false)
            .build();
        waitlisted.setEmail("lead@example.com");

        when(userRepository.findByEmail("lead@example.com")).thenReturn(Optional.of(waitlisted));

        assertThatThrownBy(() -> authService.login(
            new LoginRequest("lead@example.com", "long-enough-password"),
            "JUnit",
            "127.0.0.1"
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Please complete registration before logging in");

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void shouldRejectLoginForBannedEmailWithGenericSupportMessage() {
        when(userRepository.existsBannedByEmailLowercase("blocked@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(
            new LoginRequest(" Blocked@Example.com ", "long-enough-password"),
            "JUnit",
            "127.0.0.1"
        ))
            .isInstanceOf(BadCredentialsException.class)
            .hasMessage(BANNED_EMAIL_SUPPORT_MESSAGE);

        verify(userRepository, never()).findByEmail(any());
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void shouldRejectResendVerificationForWaitlistedUser() {
        User waitlisted = User.builder()
            .email("lead@example.com")
            .registrationStatus(UserRegistrationStatus.WAITLISTED)
            .enabled(false)
            .emailVerified(false)
            .build();
        waitlisted.setEmail("lead@example.com");

        when(userRepository.findByEmail("lead@example.com")).thenReturn(Optional.of(waitlisted));

        assertThatThrownBy(() -> authService.resendVerificationEmail("lead@example.com"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Please complete registration before requesting verification email");

        verify(emailVerificationTokenRepository, never()).save(any(EmailVerificationToken.class));
        verify(emailService, never()).sendVerificationEmail(any(User.class), any(EmailVerificationToken.class));
    }

    @Test
    void shouldCreateGoogleUserWhenVerifiedGoogleEmailHasNoAccount() {
        when(googleIdTokenVerifier.verify("google-id-token"))
            .thenReturn(new GoogleIdTokenClaims(
                "google-subject-1",
                "Person@Example.com",
                true,
                "Google Person",
                null
            ));
        when(oauth2ProviderRepository.findByProviderAndProviderUserIdForUpdate("GOOGLE", "google-subject-1"))
            .thenReturn(Optional.empty());
        when(googleOAuthProperties.isAutoCreateUsers()).thenReturn(true);
        when(userRepository.findByEmailForUpdate("Person@Example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(UUID.randomUUID());
            }
            return user;
        });
        when(referralService.ensureReferralCode(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtTokenProvider.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoginResponse response = authService.loginWithGoogle(
            new GoogleLoginRequest("google-id-token"),
            "JUnit",
            "127.0.0.1"
        );

        assertThat(response.requiresTwoFactor()).isFalse();
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("Person@Example.com");
        assertThat(response.user().displayName()).isEqualTo("Google Person");
        assertThat(response.user().emailVerified()).isTrue();
        assertThat(response.user().roles()).contains("USER");

        verify(registrationModeService).requireOpenForRegistration();
        verify(emailValidationService, never()).validateEmail(any());
        verify(oauth2ProviderRepository).save(any(OAuth2Provider.class));
        verifyNoInteractions(stripeCustomerService);
    }

    @Test
    void shouldRejectNewGoogleUserWhenRegistrationModeIsNotOpen() {
        when(googleIdTokenVerifier.verify("google-id-token"))
            .thenReturn(new GoogleIdTokenClaims(
                "google-subject-waitlist",
                "person@example.com",
                true,
                "Google Person",
                null
            ));
        when(oauth2ProviderRepository.findByProviderAndProviderUserIdForUpdate("GOOGLE", "google-subject-waitlist"))
            .thenReturn(Optional.empty());
        when(userRepository.findByEmailForUpdate("person@example.com")).thenReturn(Optional.empty());
        doThrow(new RegistrationModeService.RegistrationModeUnavailableException("Registration is currently waitlist-only"))
            .when(registrationModeService)
            .requireOpenForRegistration();

        assertThatThrownBy(() -> authService.loginWithGoogle(
            new GoogleLoginRequest("google-id-token"),
            "JUnit",
            "127.0.0.1"
        ))
            .isInstanceOf(RegistrationModeService.RegistrationModeUnavailableException.class)
            .hasMessage("Registration is currently waitlist-only");

        verify(userRepository, never()).save(any(User.class));
        verify(oauth2ProviderRepository, never()).save(any(OAuth2Provider.class));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void shouldLinkExistingEmailPasswordUserOnGoogleLogin() {
        User existing = User.builder()
            .id(UUID.randomUUID())
            .email("person@example.com")
            .displayName("Existing Person")
            .passwordHash("encoded-password")
            .registrationStatus(UserRegistrationStatus.REGISTERED)
            .enabled(true)
            .emailVerified(true)
            .build();
        existing.setEmail("person@example.com");
        existing.addRole(UserRoleType.USER);

        when(googleIdTokenVerifier.verify("google-id-token"))
            .thenReturn(new GoogleIdTokenClaims(
                "google-subject-2",
                "PERSON@example.com",
                true,
                "Google Person",
                null
            ));
        when(oauth2ProviderRepository.findByProviderAndProviderUserIdForUpdate("GOOGLE", "google-subject-2"))
            .thenReturn(Optional.empty());
        when(googleOAuthProperties.isAutoLinkUsers()).thenReturn(true);
        when(userRepository.findByEmailForUpdate("PERSON@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);
        when(referralService.ensureReferralCode(existing)).thenReturn(existing);
        when(jwtTokenProvider.generateAccessToken(existing)).thenReturn("access-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoginResponse response = authService.loginWithGoogle(
            new GoogleLoginRequest("google-id-token"),
            "JUnit",
            "127.0.0.1"
        );

        assertThat(response.requiresTwoFactor()).isFalse();
        assertThat(response.user().id()).isEqualTo(existing.getId().toString());
        assertThat(existing.getEmailVerified()).isTrue();
        assertThat(existing.getEnabled()).isTrue();
        assertThat(existing.getRegistrationStatus()).isEqualTo(UserRegistrationStatus.REGISTERED);

        verify(registrationModeService, never()).requireOpenForRegistration();
        verify(emailValidationService, never()).validateEmail(any());
        verify(oauth2ProviderRepository).save(any(OAuth2Provider.class));
    }

    @Test
    void shouldReturnTwoFactorChallengeForGoogleLoginWhenLinkedUserRequiresTwoFactor() {
        User existing = User.builder()
            .id(UUID.randomUUID())
            .email("person@example.com")
            .displayName("Existing Person")
            .passwordHash("encoded-password")
            .registrationStatus(UserRegistrationStatus.REGISTERED)
            .enabled(true)
            .emailVerified(true)
            .twoFactorEnabled(true)
            .build();
        existing.setEmail("person@example.com");
        existing.addRole(UserRoleType.USER);

        when(googleIdTokenVerifier.verify("google-id-token"))
            .thenReturn(new GoogleIdTokenClaims(
                "google-subject-2fa",
                "person@example.com",
                true,
                "Google Person",
                null
            ));
        when(oauth2ProviderRepository.findByProviderAndProviderUserIdForUpdate("GOOGLE", "google-subject-2fa"))
            .thenReturn(Optional.of(OAuth2Provider.link(existing, "GOOGLE", "google-subject-2fa")));
        when(userRepository.save(existing)).thenReturn(existing);
        when(twoFactorService.isTwoFactorRequired(existing)).thenReturn(true);
        when(pendingTwoFactorLoginRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LoginResponse response = authService.loginWithGoogle(
            new GoogleLoginRequest("google-id-token"),
            "JUnit",
            "127.0.0.1"
        );

        assertThat(response.requiresTwoFactor()).isTrue();
        assertThat(response.twoFactorToken()).isNotBlank();
        assertThat(response.accessToken()).isNull();
        assertThat(response.refreshToken()).isNull();
        assertThat(response.user()).isNull();

        verify(pendingTwoFactorLoginRepository).save(any());
        verify(jwtTokenProvider, never()).generateAccessToken(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void shouldRejectWaitlistedUserOnGoogleLoginWhenRegistrationModeIsNotOpen() {
        User waitlisted = User.builder()
            .id(UUID.randomUUID())
            .email("lead@example.com")
            .displayName("Waitlist Lead")
            .registrationStatus(UserRegistrationStatus.WAITLISTED)
            .enabled(false)
            .emailVerified(false)
            .build();
        waitlisted.setEmail("lead@example.com");

        when(googleIdTokenVerifier.verify("google-id-token"))
            .thenReturn(new GoogleIdTokenClaims(
                "google-subject-4",
                "lead@example.com",
                true,
                "Google Lead",
                null
            ));
        when(oauth2ProviderRepository.findByProviderAndProviderUserIdForUpdate("GOOGLE", "google-subject-4"))
            .thenReturn(Optional.empty());
        when(userRepository.findByEmailForUpdate("lead@example.com")).thenReturn(Optional.of(waitlisted));
        doThrow(new RegistrationModeService.RegistrationModeUnavailableException("Registration is currently waitlist-only"))
            .when(registrationModeService)
            .requireOpenForRegistration();

        assertThatThrownBy(() -> authService.loginWithGoogle(
            new GoogleLoginRequest("google-id-token"),
            "JUnit",
            "127.0.0.1"
        ))
            .isInstanceOf(RegistrationModeService.RegistrationModeUnavailableException.class)
            .hasMessage("Registration is currently waitlist-only");

        assertThat(waitlisted.getRegistrationStatus()).isEqualTo(UserRegistrationStatus.WAITLISTED);
        assertThat(waitlisted.getEnabled()).isFalse();
        assertThat(waitlisted.getEmailVerified()).isFalse();

        verify(googleOAuthProperties, never()).isAutoLinkUsers();
        verify(userRepository, never()).save(any(User.class));
        verify(oauth2ProviderRepository, never()).save(any(OAuth2Provider.class));
    }

    @Test
    void shouldAllowInvitedUserOnGoogleLoginWhenRegistrationModeIsNotOpen() {
        User invited = User.builder()
            .id(UUID.randomUUID())
            .email("invited@example.com")
            .displayName("Invited User")
            .registrationStatus(UserRegistrationStatus.REGISTERED)
            .enabled(true)
            .emailVerified(true)
            .requiresPasswordChange(true)
            .customerInviteStatus(CustomerInviteStatus.ACTIVE)
            .customerInvitedAt(LocalDateTime.now().minusHours(1))
            .tempPasswordExpiresAt(LocalDateTime.now().plusHours(24))
            .build();
        invited.setEmail("invited@example.com");
        invited.addRole(UserRoleType.USER);

        when(googleIdTokenVerifier.verify("google-id-token"))
            .thenReturn(new GoogleIdTokenClaims(
                "google-subject-invite",
                "invited@example.com",
                true,
                "Google Invited",
                null
            ));
        when(oauth2ProviderRepository.findByProviderAndProviderUserIdForUpdate("GOOGLE", "google-subject-invite"))
            .thenReturn(Optional.empty());
        when(googleOAuthProperties.isAutoLinkUsers()).thenReturn(true);
        when(userRepository.findByEmailForUpdate("invited@example.com")).thenReturn(Optional.of(invited));
        when(userRepository.save(invited)).thenReturn(invited);
        when(referralService.ensureReferralCode(invited)).thenReturn(invited);
        when(jwtTokenProvider.generateAccessToken(invited)).thenReturn("access-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setRequiresPasswordChange(false);
            user.setPasswordChangedAt(LocalDateTime.now());
            user.setCustomerInviteStatus(CustomerInviteStatus.ACCEPTED);
            return null;
        }).when(customerInviteService).markAcceptedIfNeeded(invited);

        LoginResponse response = authService.loginWithGoogle(
            new GoogleLoginRequest("google-id-token"),
            "JUnit",
            "127.0.0.1"
        );

        assertThat(response.requiresTwoFactor()).isFalse();
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.user().id()).isEqualTo(invited.getId().toString());
        assertThat(response.user().requiresPasswordChange()).isFalse();
        assertThat(invited.getCustomerInviteStatus()).isEqualTo(CustomerInviteStatus.ACCEPTED);

        verify(registrationModeService, never()).requireOpenForRegistration();
        verify(customerInviteService).ensureTemporaryPasswordUsable(invited);
        verify(customerInviteService).markAcceptedIfNeeded(invited);
        verify(oauth2ProviderRepository).save(any(OAuth2Provider.class));
    }

    @Test
    void shouldNotCreateStripeCustomerAfterGoogleLoginTransactionCommits() {
        User invited = User.builder()
            .id(UUID.randomUUID())
            .email("invited@example.com")
            .displayName("Invited User")
            .registrationStatus(UserRegistrationStatus.REGISTERED)
            .enabled(true)
            .emailVerified(true)
            .requiresPasswordChange(true)
            .customerInviteStatus(CustomerInviteStatus.ACTIVE)
            .customerInvitedAt(LocalDateTime.now().minusHours(1))
            .tempPasswordExpiresAt(LocalDateTime.now().plusHours(24))
            .build();
        invited.setEmail("invited@example.com");
        invited.addRole(UserRoleType.USER);

        when(googleIdTokenVerifier.verify("google-id-token"))
            .thenReturn(new GoogleIdTokenClaims(
                "google-subject-invite",
                "invited@example.com",
                true,
                "Google Invited",
                null
            ));
        when(oauth2ProviderRepository.findByProviderAndProviderUserIdForUpdate("GOOGLE", "google-subject-invite"))
            .thenReturn(Optional.empty());
        when(googleOAuthProperties.isAutoLinkUsers()).thenReturn(true);
        when(userRepository.findByEmailForUpdate("invited@example.com")).thenReturn(Optional.of(invited));
        when(userRepository.save(invited)).thenReturn(invited);
        when(referralService.ensureReferralCode(invited)).thenReturn(invited);
        when(jwtTokenProvider.generateAccessToken(invited)).thenReturn("access-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setRequiresPasswordChange(false);
            user.setPasswordChangedAt(LocalDateTime.now());
            user.setCustomerInviteStatus(CustomerInviteStatus.ACCEPTED);
            return null;
        }).when(customerInviteService).markAcceptedIfNeeded(invited);

        authService.loginWithGoogle(
            new GoogleLoginRequest("google-id-token"),
            "JUnit",
            "127.0.0.1"
        );

        verifyNoInteractions(stripeCustomerService);
    }

    @Test
    void shouldRejectGoogleLoginWhenGoogleEmailIsNotVerified() {
        when(googleIdTokenVerifier.verify("google-id-token"))
            .thenReturn(new GoogleIdTokenClaims(
                "google-subject-3",
                "person@example.com",
                false,
                "Google Person",
                null
            ));

        assertThatThrownBy(() -> authService.loginWithGoogle(
            new GoogleLoginRequest("google-id-token"),
            "JUnit",
            "127.0.0.1"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Google account email must be verified");

        verify(oauth2ProviderRepository, never()).findByProviderAndProviderUserIdForUpdate(any(), any());
        verify(userRepository, never()).save(any(User.class));
    }
}
