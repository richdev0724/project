package com.marketmono.config.security;

import com.marketmono.entity.auth.User;
import com.marketmono.repository.auth.UserRepository;
import com.marketmono.service.SubscriptionService;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SubscriptionAccessFilterTest {

    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldBypassSubscriptionChecksForInternalAdminPrincipal() throws ServletException, IOException {
        SubscriptionAccessFilter filter = new SubscriptionAccessFilter(subscriptionService, userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/stripe/subscriptions/user/8b1c0b2d-5c2e-4a73-8d7b-02f30f8d2e11/cancel");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain filterChain = new RecordingFilterChain();

        InternalAdminPrincipal principal = new InternalAdminPrincipal(UUID.randomUUID());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_OK);
        verifyNoInteractions(subscriptionService, userRepository);
    }

    @Test
    void shouldAllowAccessWhenSubscriptionServiceReportsActiveSubscription() throws ServletException, IOException {
        SubscriptionAccessFilter filter = new SubscriptionAccessFilter(subscriptionService, userRepository);
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/market-tide");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain filterChain = new RecordingFilterChain();

        User user = User.builder()
            .id(userId)
            .email("subscriber@example.com")
            .enabled(true)
            .emailVerified(true)
            .build();
        UserPrincipal principal = new UserPrincipal(user);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(subscriptionService.hasWebsiteAccess(userId)).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_OK);
        verify(subscriptionService).hasWebsiteAccess(userId);
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectProtectedRequestWhenSubscriptionServiceReportsNoActiveSubscription() throws ServletException, IOException {
        SubscriptionAccessFilter filter = new SubscriptionAccessFilter(subscriptionService, userRepository);
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/market-tide");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain filterChain = new RecordingFilterChain();

        User user = User.builder()
            .id(userId)
            .email("subscriber@example.com")
            .enabled(true)
            .emailVerified(true)
            .build();
        UserPrincipal principal = new UserPrincipal(user);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(subscriptionService.hasWebsiteAccess(userId)).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getContentAsString()).contains("SUBSCRIPTION_REQUIRED");
        verify(subscriptionService).hasWebsiteAccess(userId);
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldAllowPublicChangelogForAuthenticatedUserWithoutSubscription() throws ServletException, IOException {
        SubscriptionAccessFilter filter = new SubscriptionAccessFilter(subscriptionService, userRepository);
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/changelog");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain filterChain = new RecordingFilterChain();

        User user = User.builder()
            .id(userId)
            .email("subscriber@example.com")
            .enabled(true)
            .emailVerified(true)
            .build();
        UserPrincipal principal = new UserPrincipal(user);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_OK);
        verifyNoInteractions(subscriptionService, userRepository);
    }

    @Test
    void shouldAllowAuthenticatedSupportApisWithoutSubscription() throws ServletException, IOException {
        SubscriptionAccessFilter filter = new SubscriptionAccessFilter(subscriptionService, userRepository);
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/support/tickets/unread-summary");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain filterChain = new RecordingFilterChain();

        User user = User.builder()
            .id(userId)
            .email("subscriber@example.com")
            .enabled(true)
            .emailVerified(true)
            .build();
        UserPrincipal principal = new UserPrincipal(user);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_OK);
        verifyNoInteractions(subscriptionService, userRepository);
    }

    @Test
    void shouldAllowWhopWebhookWithoutSubscriptionCheck() throws ServletException, IOException {
        SubscriptionAccessFilter filter = new SubscriptionAccessFilter(subscriptionService, userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/whop/webhooks");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain filterChain = new RecordingFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_OK);
        verifyNoInteractions(subscriptionService, userRepository);
    }

    @Test
    void shouldAllowWhopCheckoutApisWithoutSubscriptionCheck() throws ServletException, IOException {
        SubscriptionAccessFilter filter = new SubscriptionAccessFilter(subscriptionService, userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/whop/checkout/create-session");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain filterChain = new RecordingFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_OK);
        verifyNoInteractions(subscriptionService, userRepository);
    }

    @Test
    void shouldAllowProviderNeutralBillingPortalWithoutSubscriptionCheck() throws ServletException, IOException {
        SubscriptionAccessFilter filter = new SubscriptionAccessFilter(subscriptionService, userRepository);
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/billing/portal/create-session");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain filterChain = new RecordingFilterChain();

        User user = User.builder()
            .id(userId)
            .email("nosub@example.com")
            .enabled(true)
            .emailVerified(true)
            .build();
        UserPrincipal principal = new UserPrincipal(user);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_OK);
        verifyNoInteractions(subscriptionService, userRepository);
    }

    private static final class RecordingFilterChain implements jakarta.servlet.FilterChain {
        private boolean invoked;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            invoked = true;
        }
    }
}
