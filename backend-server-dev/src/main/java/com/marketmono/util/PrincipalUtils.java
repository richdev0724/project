package com.marketmono.util;

import com.marketmono.config.security.InternalAdminPrincipal;
import com.marketmono.config.security.UserPrincipal;
import com.marketmono.entity.auth.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Objects;
import java.util.UUID;

/**
 * Principal extraction helpers with call-site-specific accept sets.
 */
public final class PrincipalUtils {

    private static final String INTERNAL_ADMIN_ACTOR_ID_REQUIRED =
        "Internal admin requests must include the admin actor id header";

    private PrincipalUtils() {
    }

    public static UUID requireSubscriptionUserId(UserDetails userDetails) {
        if (userDetails instanceof UserPrincipal principal) {
            return principal.getUser().getId();
        }
        if (userDetails instanceof User user) {
            return user.getId();
        }
        throw new IllegalStateException("Unable to extract user ID from authentication");
    }

    public static User requireAuthenticatedUser(UserDetails userDetails) {
        if (userDetails instanceof UserPrincipal principal) {
            return principal.getUser();
        }
        if (userDetails instanceof User user) {
            return user;
        }
        throw new IllegalStateException("Unable to extract authenticated user from authentication");
    }

    public static UUID requireRegistrationModeActorId(UserDetails userDetails) {
        if (userDetails instanceof InternalAdminPrincipal internalAdminPrincipal) {
            return Objects.requireNonNull(internalAdminPrincipal.adminId(), INTERNAL_ADMIN_ACTOR_ID_REQUIRED);
        }
        if (userDetails instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getUser().getId();
        }
        if (userDetails instanceof User user) {
            return user.getId();
        }
        throw new IllegalStateException("Unable to extract user ID from authentication");
    }

    public static UUID requireAdminConfigActorId(UserDetails userDetails) {
        if (userDetails instanceof InternalAdminPrincipal internalAdminPrincipal) {
            return Objects.requireNonNull(internalAdminPrincipal.adminId(), INTERNAL_ADMIN_ACTOR_ID_REQUIRED);
        }
        if (userDetails instanceof User user) {
            return user.getId();
        }
        throw new IllegalStateException("Unable to extract user ID from authentication");
    }

    public static UUID requireChangelogActorId(UserDetails userDetails) {
        if (userDetails instanceof InternalAdminPrincipal internalAdminPrincipal) {
            UUID adminId = internalAdminPrincipal.adminId();
            if (adminId == null) {
                throw new IllegalStateException(INTERNAL_ADMIN_ACTOR_ID_REQUIRED);
            }
            return adminId;
        }
        if (userDetails instanceof User user) {
            return user.getId();
        }
        throw new IllegalStateException("Unable to extract user ID from authentication");
    }

    public static UUID requireInternalAdminActorId(UserDetails userDetails) {
        if (userDetails instanceof InternalAdminPrincipal internalAdminPrincipal
                && internalAdminPrincipal.adminId() != null) {
            return internalAdminPrincipal.adminId();
        }
        throw new IllegalStateException("Unable to extract admin actor ID from internal authentication");
    }
}
