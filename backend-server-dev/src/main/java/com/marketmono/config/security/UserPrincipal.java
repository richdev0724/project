package com.marketmono.config.security;

import com.marketmono.entity.auth.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Custom UserDetails implementation that wraps the User entity.
 * Allows access to the full User entity via @AuthenticationPrincipal.
 */
@Getter
public class UserPrincipal implements UserDetails {
    
    private final User user;
    private final List<SimpleGrantedAuthority> authorities;
    
    public UserPrincipal(User user) {
        this.user = user;
        this.authorities = user.getRoles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getRole().name()))
            .toList();
    }
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
    
    @Override
    public String getPassword() {
        return user.getPasswordHash() != null ? user.getPasswordHash() : "";
    }
    
    @Override
    public String getUsername() {
        return user.getEmail();
    }
    
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }
    
    @Override
    public boolean isAccountNonLocked() {
        return !user.isAccountLocked();
    }
    
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
    
    @Override
    public boolean isEnabled() {
        return user.getEnabled() && !user.isDeleted();
    }
}
