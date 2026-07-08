package com.marketmono.config.security;

import com.marketmono.entity.auth.User;
import com.marketmono.repository.auth.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider, userRepository);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateActiveUser() throws Exception {
        User user = activeUser();
        runFilterWithUser(user);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
            .isEqualTo(user.getEmail());
    }

    @Test
    void shouldNotAuthenticateDisabledUser() throws Exception {
        User user = activeUser();
        user.setEnabled(false);

        runFilterWithUser(user);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldNotAuthenticateDeletedUser() throws Exception {
        User user = activeUser();
        user.setDeletedAt(LocalDateTime.now());

        runFilterWithUser(user);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldNotAuthenticateBannedUser() throws Exception {
        User user = activeUser();
        user.setBannedAt(LocalDateTime.now());

        runFilterWithUser(user);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldNotAuthenticateLockedUser() throws Exception {
        User user = activeUser();
        user.setLockedUntil(LocalDateTime.now().plusMinutes(5));

        runFilterWithUser(user);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private void runFilterWithUser(User user) throws Exception {
        String token = "valid-token-" + UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/market-tide");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain filterChain = new RecordingFilterChain();

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(token)).thenReturn(user.getId());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.invoked).isTrue();
    }

    private static User activeUser() {
        User user = User.builder()
            .id(UUID.randomUUID())
            .email("subscriber@example.com")
            .emailLowercase("subscriber@example.com")
            .enabled(true)
            .emailVerified(true)
            .build();
        user.addRole(com.marketmono.entity.auth.UserRoleType.USER);
        return user;
    }

    private static final class RecordingFilterChain implements FilterChain {
        private boolean invoked;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            invoked = true;
        }
    }
}
