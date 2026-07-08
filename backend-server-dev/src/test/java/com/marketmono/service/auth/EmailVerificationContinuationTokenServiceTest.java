package com.marketmono.service.auth;

import com.marketmono.config.security.JwtProperties;
import com.marketmono.config.security.JwtTokenProvider;
import com.marketmono.dto.auth.EmailVerificationContinuationToken;
import com.marketmono.entity.auth.EmailVerificationToken;
import com.marketmono.entity.auth.User;
import com.marketmono.entity.auth.UserRoleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmailVerificationContinuationTokenServiceTest {

    private JwtProperties jwtProperties;
    private EmailVerificationContinuationTokenService service;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-signing-secret-that-is-at-least-32-bytes-long");
        jwtProperties.setIssuer("marketmono-test");
        service = new EmailVerificationContinuationTokenService(jwtProperties);
    }

    @Test
    void shouldCreateTokenThatOnlyMatchesTheSameUserAndVerificationToken() {
        User user = verifiedUser("person@example.com");
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
            .user(user)
            .token("email-verification-token")
            .expiresAt(LocalDateTime.now().plusHours(24))
            .build();

        EmailVerificationContinuationToken continuation = service.create(user, verificationToken);

        assertThat(continuation.token()).isNotBlank();
        assertThat(continuation.expiresAt()).isAfter(LocalDateTime.now().plusMinutes(119));
        assertThat(service.matches(continuation.token(), user, verificationToken)).isTrue();
        assertThat(service.matches(
            continuation.token(),
            user,
            EmailVerificationToken.builder()
                .user(user)
                .token("different-email-verification-token")
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build()
        )).isFalse();
        assertThat(service.matches(continuation.token(), verifiedUser("other@example.com"), verificationToken)).isFalse();
    }

    @Test
    void shouldCapContinuationExpiryAtEmailVerificationTokenExpiry() {
        User user = verifiedUser("person@example.com");
        LocalDateTime emailTokenExpiry = LocalDateTime.now().plusMinutes(30);
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
            .user(user)
            .token("email-verification-token")
            .expiresAt(emailTokenExpiry)
            .build();

        EmailVerificationContinuationToken continuation = service.create(user, verificationToken);

        assertThat(continuation.expiresAt()).isEqualTo(emailTokenExpiry);
    }

    @Test
    void shouldRejectAccessJwtAsContinuationToken() {
        User user = verifiedUser("person@example.com");
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
            .user(user)
            .token("email-verification-token")
            .expiresAt(LocalDateTime.now().plusHours(24))
            .build();
        String accessToken = new JwtTokenProvider(jwtProperties).generateAccessToken(user);

        assertThat(service.matches(accessToken, user, verificationToken)).isFalse();
    }

    private static User verifiedUser(String email) {
        User user = User.builder()
            .id(UUID.randomUUID())
            .email(email)
            .enabled(true)
            .emailVerified(true)
            .build();
        user.setEmail(email);
        user.addRole(UserRoleType.USER);
        return user;
    }
}
