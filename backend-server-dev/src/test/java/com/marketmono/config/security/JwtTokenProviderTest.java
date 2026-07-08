package com.marketmono.config.security;

import com.marketmono.entity.auth.User;
import com.marketmono.entity.auth.UserRoleType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    @Test
    void shouldRejectTokenGenerationWhenJwtSecretIsNotConfigured() {
        JwtProperties properties = new JwtProperties();
        JwtTokenProvider provider = new JwtTokenProvider(properties);
        User user = User.builder()
            .id(UUID.randomUUID())
            .email("person@example.com")
            .emailVerified(true)
            .build();
        user.setEmail("person@example.com");
        user.addRole(UserRoleType.USER);

        assertThatThrownBy(() -> provider.generateAccessToken(user))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("JWT_SECRET is not configured");
    }
}
