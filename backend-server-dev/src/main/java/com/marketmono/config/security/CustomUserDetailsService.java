package com.marketmono.config.security;

import com.marketmono.entity.auth.User;
import com.marketmono.repository.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Custom UserDetailsService implementation for Spring Security.
 * Loads user details from database for authentication.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    
    private final UserRepository userRepository;
    
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
        
        return new UserPrincipal(user);
    }
    
    /**
     * Load user by ID for JWT validation.
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserById(String userId) throws UsernameNotFoundException {
        User user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));
        
        if (user.isDeleted()) {
            throw new UsernameNotFoundException("User account has been deleted");
        }
        
        return new UserPrincipal(user);
    }
}
