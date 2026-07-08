package com.marketmono.config.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Synthetic principal for authenticated internal backend-admin calls.
 */
public record InternalAdminPrincipal(UUID adminId) implements UserDetails {

    private static final List<GrantedAuthority> AUTHORITIES = List.of(
        new SimpleGrantedAuthority("ROLE_INTERNAL_ADMIN"),
        new SimpleGrantedAuthority("ROLE_ADMIN")
    );

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AUTHORITIES;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return "backend-admin-internal";
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
