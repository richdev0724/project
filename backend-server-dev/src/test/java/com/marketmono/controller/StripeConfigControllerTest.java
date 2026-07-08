package com.marketmono.controller;

import com.marketmono.config.security.InternalAdminPrincipal;
import com.marketmono.config.security.UserPrincipal;
import com.marketmono.dto.StripeConfigDto;
import com.marketmono.dto.UpdateStripeConfigRequest;
import com.marketmono.entity.StripeMode;
import com.marketmono.entity.auth.User;
import com.marketmono.service.StripeConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
class StripeConfigControllerTest {

    @Mock
    private StripeConfigService stripeConfigService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private StripeConfigController stripeConfigController;

    @Test
    void shouldUseUserStripeOverrideWhenReturningPublicKey() {
        User user = User.builder()
            .id(UUID.randomUUID())
            .email("override@example.com")
            .emailLowercase("override@example.com")
            .stripeMode(StripeMode.PROD)
            .build();

        when(stripeConfigService.getPublicKey(StripeMode.PROD)).thenReturn("pk_live_override");

        ResponseEntity<StripeConfigController.StripePublicKeyResponse> response =
            stripeConfigController.getPublicKey(new UserPrincipal(user));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().publicKey()).isEqualTo("pk_live_override");
        assertThat(response.getBody().mode()).isEqualTo("PROD");
        assertThat(response.getBody().configured()).isTrue();
        verify(stripeConfigService).getPublicKey(StripeMode.PROD);
    }

    @Test
    void shouldFallBackToDefaultModeWhenUserOverrideIsDefault() {
        User user = User.builder()
            .id(UUID.randomUUID())
            .email("default@example.com")
            .emailLowercase("default@example.com")
            .stripeMode(StripeMode.DEFAULT)
            .build();

        when(stripeConfigService.getDefaultMode()).thenReturn(StripeMode.TEST);
        when(stripeConfigService.getPublicKey(StripeMode.TEST)).thenReturn("pk_test_default");

        ResponseEntity<StripeConfigController.StripePublicKeyResponse> response =
            stripeConfigController.getPublicKey(new UserPrincipal(user));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().publicKey()).isEqualTo("pk_test_default");
        assertThat(response.getBody().mode()).isEqualTo("TEST");
        assertThat(response.getBody().configured()).isTrue();
        verify(stripeConfigService).getDefaultMode();
        verify(stripeConfigService).getPublicKey(StripeMode.TEST);
    }

    @Test
    void shouldAcceptInternalAdminPrincipalForAdminConfigUpdate() {
        UUID adminId = UUID.randomUUID();
        UpdateStripeConfigRequest request = updateRequest();
        StripeConfigDto response = stripeConfigDto();
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10");
        when(stripeConfigService.updateConfig(request, adminId, "203.0.113.10")).thenReturn(response);

        stripeConfigController.updateConfig(new InternalAdminPrincipal(adminId), request, httpServletRequest);

        verify(stripeConfigService).updateConfig(request, adminId, "203.0.113.10");
    }

    @Test
    void shouldAcceptDirectUserForAdminConfigUpdate() {
        UUID userId = UUID.randomUUID();
        UserDetails user = userDetailsBackedByUser(userId);
        UpdateStripeConfigRequest request = updateRequest();
        StripeConfigDto response = stripeConfigDto();
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10");
        when(stripeConfigService.updateConfig(request, userId, "203.0.113.10")).thenReturn(response);

        stripeConfigController.updateConfig(user, request, httpServletRequest);

        verify(stripeConfigService).updateConfig(request, userId, "203.0.113.10");
    }

    @Test
    void shouldRejectUserPrincipalForAdminConfigUpdate() {
        assertThatThrownBy(() -> stripeConfigController.updateConfig(
            new UserPrincipal(user(UUID.randomUUID())),
            updateRequest(),
            httpServletRequest
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to extract user ID");
    }

    private static User user(UUID userId) {
        return User.builder()
            .id(userId)
            .email("admin@example.com")
            .emailLowercase("admin@example.com")
            .enabled(true)
            .emailVerified(true)
            .build();
    }

    private static UserDetails userDetailsBackedByUser(UUID userId) {
        User user = mock(User.class, withSettings().extraInterfaces(UserDetails.class));
        when(user.getId()).thenReturn(userId);
        return (UserDetails) user;
    }

    private static UpdateStripeConfigRequest updateRequest() {
        return new UpdateStripeConfigRequest(
            "pk_test_new",
            "sk_test_new",
            "whsec_test",
            "pk_live_new",
            "sk_live_new",
            "whsec_live",
            StripeMode.TEST,
            1
        );
    }

    private static StripeConfigDto stripeConfigDto() {
        return new StripeConfigDto(
            "pk_test_***",
            "sk_test_***",
            "whsec_***",
            "pk_live_***",
            "sk_live_***",
            "whsec_***",
            StripeMode.TEST,
            1,
            true,
            true
        );
    }
}
