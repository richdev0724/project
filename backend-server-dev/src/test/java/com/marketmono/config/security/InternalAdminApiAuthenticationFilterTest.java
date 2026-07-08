package com.marketmono.config.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class InternalAdminApiAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldFilterChangelogRootAndNestedPaths() {
        InternalAdminApiAuthenticationFilter filter =
            new InternalAdminApiAuthenticationFilter(new InternalAdminApiProperties("test-secret"));

        assertThat(filter.shouldNotFilter(request("GET", "/api/v1/admin/changelog")))
            .isFalse();
        assertThat(filter.shouldNotFilter(request("POST", "/api/v1/admin/changelog")))
            .isFalse();
        assertThat(filter.shouldNotFilter(request("DELETE", "/api/v1/admin/changelog/123")))
            .isFalse();
    }

    @Test
    void shouldFilterWaitlistRootAndNestedPaths() {
        InternalAdminApiAuthenticationFilter filter =
            new InternalAdminApiAuthenticationFilter(new InternalAdminApiProperties("test-secret"));

        assertThat(filter.shouldNotFilter(request("DELETE", "/api/v1/admin/waitlist")))
            .isFalse();
        assertThat(filter.shouldNotFilter(request("POST", "/api/v1/admin/waitlist/123/resend-confirmation")))
            .isFalse();
    }

    @Test
    void shouldFilterEmailRelayPath() {
        InternalAdminApiAuthenticationFilter filter =
            new InternalAdminApiAuthenticationFilter(new InternalAdminApiProperties("test-secret"));

        assertThat(filter.shouldNotFilter(request("POST", "/api/v1/admin/email/send")))
            .isFalse();
    }

    @Test
    void shouldFilterAppUserLifecyclePaths() {
        InternalAdminApiAuthenticationFilter filter =
            new InternalAdminApiAuthenticationFilter(new InternalAdminApiProperties("test-secret"));
        UUID userId = UUID.randomUUID();

        assertThat(filter.shouldNotFilter(request("POST", "/api/v1/admin/app-users/" + userId + "/soft-delete")))
            .isFalse();
        assertThat(filter.shouldNotFilter(request("POST", "/api/v1/admin/app-users/" + userId + "/restore")))
            .isFalse();
    }

    @Test
    void shouldFilterWhopMembershipReviewPaths() {
        InternalAdminApiAuthenticationFilter filter =
            new InternalAdminApiAuthenticationFilter(new InternalAdminApiProperties("test-secret"));
        UUID correlationId = UUID.randomUUID();

        assertThat(filter.shouldNotFilter(request("GET", "/api/v1/admin/whop/membership-reviews")))
            .isFalse();
        assertThat(filter.shouldNotFilter(request(
            "POST",
            "/api/v1/admin/whop/membership-reviews/" + correlationId + "/attach"
        ))).isFalse();
    }

    @Test
    void shouldSkipUnrelatedPaths() {
        InternalAdminApiAuthenticationFilter filter =
            new InternalAdminApiAuthenticationFilter(new InternalAdminApiProperties("test-secret"));

        assertThat(filter.shouldNotFilter(request("GET", "/api/v1/market-tide")))
            .isTrue();
    }

    @Test
    void shouldRejectInternalAdminRequestWhenActorHeaderIsMissing() throws Exception {
        InternalAdminApiAuthenticationFilter filter =
            new InternalAdminApiAuthenticationFilter(new InternalAdminApiProperties("test-secret"));
        MockHttpServletRequest request = request("POST", "/api/v1/admin/customer-invites");
        request.addHeader(InternalAdminApiAuthenticationFilter.SECRET_HEADER, "test-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilterInternal(request, response, (req, res) -> chainCalled.set(true));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getErrorMessage()).isEqualTo("Missing internal admin actor id header");
        assertThat(chainCalled).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldRejectBetaWaitlistInviteRequestWhenActorHeaderIsMissing() throws Exception {
        InternalAdminApiAuthenticationFilter filter =
            new InternalAdminApiAuthenticationFilter(new InternalAdminApiProperties("test-secret"));
        MockHttpServletRequest request = request("POST", "/api/v1/admin/waitlist/beta-invites");
        request.addHeader(InternalAdminApiAuthenticationFilter.SECRET_HEADER, "test-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilterInternal(request, response, (req, res) -> chainCalled.set(true));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getErrorMessage()).isEqualTo("Missing internal admin actor id header");
        assertThat(chainCalled).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldRejectAppUserLifecycleRequestWhenActorHeaderIsMissing() throws Exception {
        InternalAdminApiAuthenticationFilter filter =
            new InternalAdminApiAuthenticationFilter(new InternalAdminApiProperties("test-secret"));
        MockHttpServletRequest request = request(
            "POST",
            "/api/v1/admin/app-users/" + UUID.randomUUID() + "/restore"
        );
        request.addHeader(InternalAdminApiAuthenticationFilter.SECRET_HEADER, "test-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilterInternal(request, response, (req, res) -> chainCalled.set(true));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getErrorMessage()).isEqualTo("Missing internal admin actor id header");
        assertThat(chainCalled).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldRejectWhopMembershipReviewRequestWhenActorHeaderIsMissing() throws Exception {
        InternalAdminApiAuthenticationFilter filter =
            new InternalAdminApiAuthenticationFilter(new InternalAdminApiProperties("test-secret"));
        MockHttpServletRequest request = request("POST", "/api/v1/admin/whop/membership-reviews/"
            + UUID.randomUUID()
            + "/attach");
        request.addHeader(InternalAdminApiAuthenticationFilter.SECRET_HEADER, "test-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilterInternal(request, response, (req, res) -> chainCalled.set(true));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getErrorMessage()).isEqualTo("Missing internal admin actor id header");
        assertThat(chainCalled).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldAuthenticateInternalAdminRequestWithActorHeader() throws Exception {
        InternalAdminApiAuthenticationFilter filter =
            new InternalAdminApiAuthenticationFilter(new InternalAdminApiProperties("test-secret"));
        UUID actorId = UUID.randomUUID();
        MockHttpServletRequest request = request("POST", "/api/v1/admin/customer-invites");
        request.addHeader(InternalAdminApiAuthenticationFilter.SECRET_HEADER, "test-secret");
        request.addHeader(InternalAdminApiAuthenticationFilter.ACTOR_ID_HEADER, actorId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilterInternal(request, response, (req, res) -> chainCalled.set(true));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chainCalled).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(new InternalAdminPrincipal(actorId));
        assertThat(authentication.getAuthorities())
            .extracting("authority")
            .contains("ROLE_INTERNAL_ADMIN");
    }

    @Test
    void shouldAllowInternalEmailRelayWithoutActorHeader() throws Exception {
        InternalAdminApiAuthenticationFilter filter =
            new InternalAdminApiAuthenticationFilter(new InternalAdminApiProperties("test-secret"));
        MockHttpServletRequest request = request("POST", "/api/v1/admin/email/send");
        request.addHeader(InternalAdminApiAuthenticationFilter.SECRET_HEADER, "test-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilterInternal(request, response, (req, res) -> chainCalled.set(true));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chainCalled).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(new InternalAdminPrincipal(null));
    }

    private MockHttpServletRequest request(String method, String servletPath) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, servletPath);
        request.setServletPath(servletPath);
        return request;
    }
}
