package com.marketmono.util;

import com.marketmono.config.security.InternalAdminPrincipal;
import com.marketmono.config.security.UserPrincipal;
import com.marketmono.entity.auth.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class PrincipalUtilsTest {

    @Test
    void subscriptionUserIdAcceptsUserPrincipalAndDirectUserOnly() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);

        assertThat(PrincipalUtils.requireSubscriptionUserId(new UserPrincipal(user))).isEqualTo(userId);
        assertThat(PrincipalUtils.requireSubscriptionUserId(userDetailsBackedByUser(userId))).isEqualTo(userId);
        assertThatThrownBy(() -> PrincipalUtils.requireSubscriptionUserId(new InternalAdminPrincipal(UUID.randomUUID())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to extract user ID");
    }

    @Test
    void registrationModeActorIdAcceptsInternalAdminUserPrincipalAndDirectUser() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = user(userId);

        assertThat(PrincipalUtils.requireRegistrationModeActorId(new InternalAdminPrincipal(adminId))).isEqualTo(adminId);
        assertThat(PrincipalUtils.requireRegistrationModeActorId(new UserPrincipal(user))).isEqualTo(userId);
        assertThat(PrincipalUtils.requireRegistrationModeActorId(userDetailsBackedByUser(userId))).isEqualTo(userId);
        assertThatThrownBy(() -> PrincipalUtils.requireRegistrationModeActorId(new InternalAdminPrincipal(null)))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Internal admin requests must include the admin actor id header");
    }

    @Test
    void adminConfigActorIdAcceptsInternalAdminAndDirectUserButRejectsUserPrincipal() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        assertThat(PrincipalUtils.requireAdminConfigActorId(new InternalAdminPrincipal(adminId))).isEqualTo(adminId);
        assertThat(PrincipalUtils.requireAdminConfigActorId(userDetailsBackedByUser(userId))).isEqualTo(userId);
        assertThatThrownBy(() -> PrincipalUtils.requireAdminConfigActorId(new UserPrincipal(user(userId))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to extract user ID");
        assertThatThrownBy(() -> PrincipalUtils.requireAdminConfigActorId(new InternalAdminPrincipal(null)))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Internal admin requests must include the admin actor id header");
    }

    @Test
    void changelogActorIdPreservesInternalAdminNullExceptionAsIllegalState() {
        assertThatThrownBy(() -> PrincipalUtils.requireChangelogActorId(new InternalAdminPrincipal(null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Internal admin requests must include the admin actor id header");
    }

    @Test
    void internalAdminActorIdRemainsAdminOnly() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        assertThat(PrincipalUtils.requireInternalAdminActorId(new InternalAdminPrincipal(adminId))).isEqualTo(adminId);
        assertThatThrownBy(() -> PrincipalUtils.requireInternalAdminActorId(new UserPrincipal(user(userId))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to extract admin actor ID");
        assertThatThrownBy(() -> PrincipalUtils.requireInternalAdminActorId(userDetailsBackedByUser(userId)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to extract admin actor ID");
        assertThatThrownBy(() -> PrincipalUtils.requireInternalAdminActorId(new InternalAdminPrincipal(null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to extract admin actor ID");
    }

    private static User user(UUID userId) {
        return User.builder()
            .id(userId)
            .email("user@example.com")
            .enabled(true)
            .emailVerified(true)
            .build();
    }

    private static UserDetails userDetailsBackedByUser(UUID userId) {
        User user = mock(User.class, withSettings().extraInterfaces(UserDetails.class));
        when(user.getId()).thenReturn(userId);
        return (UserDetails) user;
    }
}
