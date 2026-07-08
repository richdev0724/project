package com.marketmono.dto.auth;

import com.marketmono.entity.auth.User;
import com.marketmono.entity.auth.UserRole;
import com.marketmono.entity.auth.UserRoleType;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * User data transfer object (excludes sensitive data).
 */
public record UserDto(
    String id,
    String email,
    String displayName,
    boolean emailVerified,
    boolean twoFactorEnabled,
    boolean requiresPasswordChange,
    Set<String> roles
) {
    
    /**
     * Create UserDto from User entity.
     */
    public static UserDto fromUser(User user) {
        Set<String> roles = user.getRoles().stream()
            .map(UserRole::getRole)
            .map(UserRoleType::name)
            .collect(Collectors.toSet());
        
        return new UserDto(
            user.getId().toString(),
            user.getEmail(),
            user.getDisplayName(),
            user.getEmailVerified(),
            user.getTwoFactorEnabled(),
            Boolean.TRUE.equals(user.getRequiresPasswordChange()),
            roles
        );
    }
}
