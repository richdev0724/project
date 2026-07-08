package com.marketmono.controller.auth;

import com.marketmono.config.security.InternalAdminPrincipal;
import com.marketmono.config.security.UserPrincipal;
import com.marketmono.dto.registration.RegistrationModeResponse;
import com.marketmono.dto.registration.UpdateRegistrationModeRequest;
import com.marketmono.entity.auth.RegistrationMode;
import com.marketmono.entity.auth.User;
import com.marketmono.service.auth.RegistrationModeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
class RegistrationModeControllerTest {

    @Mock
    private RegistrationModeService registrationModeService;

    @InjectMocks
    private RegistrationModeController registrationModeController;

    @Test
    void shouldAcceptInternalAdminPrincipalForAdminUpdate() {
        UUID adminId = UUID.randomUUID();
        UpdateRegistrationModeRequest request = new UpdateRegistrationModeRequest(RegistrationMode.OPEN);
        when(registrationModeService.updateRegistrationMode(RegistrationMode.OPEN, adminId))
            .thenReturn(response());

        registrationModeController.updateRegistrationMode(new InternalAdminPrincipal(adminId), request);

        verify(registrationModeService).updateRegistrationMode(RegistrationMode.OPEN, adminId);
    }

    @Test
    void shouldAcceptUserPrincipalForAdminUpdate() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        UpdateRegistrationModeRequest request = new UpdateRegistrationModeRequest(RegistrationMode.CLOSED);
        when(registrationModeService.updateRegistrationMode(RegistrationMode.CLOSED, userId))
            .thenReturn(response());

        registrationModeController.updateRegistrationMode(new UserPrincipal(user), request);

        verify(registrationModeService).updateRegistrationMode(RegistrationMode.CLOSED, userId);
    }

    @Test
    void shouldAcceptDirectUserForAdminUpdate() {
        UUID userId = UUID.randomUUID();
        UserDetails user = userDetailsBackedByUser(userId);
        UpdateRegistrationModeRequest request = new UpdateRegistrationModeRequest(RegistrationMode.WAITLIST);
        when(registrationModeService.updateRegistrationMode(RegistrationMode.WAITLIST, userId))
            .thenReturn(response());

        registrationModeController.updateRegistrationMode(user, request);

        verify(registrationModeService).updateRegistrationMode(RegistrationMode.WAITLIST, userId);
    }

    @Test
    void shouldPreserveNullInternalAdminActorExceptionType() {
        UpdateRegistrationModeRequest request = new UpdateRegistrationModeRequest(RegistrationMode.OPEN);

        assertThatThrownBy(() -> registrationModeController.updateRegistrationMode(new InternalAdminPrincipal(null), request))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Internal admin requests must include the admin actor id header");
    }

    private static User user(UUID userId) {
        return User.builder()
            .id(userId)
            .email("admin@example.com")
            .enabled(true)
            .emailVerified(true)
            .build();
    }

    private static UserDetails userDetailsBackedByUser(UUID userId) {
        User user = mock(User.class, withSettings().extraInterfaces(UserDetails.class));
        when(user.getId()).thenReturn(userId);
        return (UserDetails) user;
    }

    private static RegistrationModeResponse response() {
        return new RegistrationModeResponse(RegistrationMode.OPEN, true, false, LocalDateTime.now());
    }
}
