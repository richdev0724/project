package com.marketmono.config.security;

import com.marketmono.entity.auth.User;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordChangeRequiredFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blocksProtectedRequestsForUsersWhoMustChangePassword() throws Exception {
        PasswordChangeRequiredFilter filter = new PasswordChangeRequiredFilter();
        authenticateUser(true);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        MockHttpServletResponse response = filter(filter, "GET", "/api/v1/flow/alerts", chainCalled);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("PASSWORD_CHANGE_REQUIRED");
        assertThat(chainCalled).isFalse();
    }

    @Test
    void allowsPasswordChangeEndpointForUsersWhoMustChangePassword() throws Exception {
        PasswordChangeRequiredFilter filter = new PasswordChangeRequiredFilter();
        authenticateUser(true);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        MockHttpServletResponse response = filter(filter, "POST", "/api/v1/auth/change-password", chainCalled);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chainCalled).isTrue();
    }

    @Test
    void allowsProtectedRequestsForUsersWithoutRequiredPasswordChange() throws Exception {
        PasswordChangeRequiredFilter filter = new PasswordChangeRequiredFilter();
        authenticateUser(false);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        MockHttpServletResponse response = filter(filter, "GET", "/api/v1/flow/alerts", chainCalled);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chainCalled).isTrue();
    }

    private MockHttpServletResponse filter(
            PasswordChangeRequiredFilter filter,
            String method,
            String uri,
            AtomicBoolean chainCalled) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setServletPath(uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            chainCalled.set(true);
            response.setStatus(200);
        };

        filter.doFilterInternal(request, response, chain);
        return response;
    }

    private void authenticateUser(boolean requiresPasswordChange) {
        User user = User.builder()
            .email("invited@example.com")
            .enabled(true)
            .requiresPasswordChange(requiresPasswordChange)
            .build();
        user.setEmail("invited@example.com");
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
