package com.marketmono.service.auth;

import com.marketmono.dto.registration.RegistrationModeResponse;
import com.marketmono.entity.StripeConfig;
import com.marketmono.entity.auth.RegistrationMode;
import com.marketmono.repository.StripeConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationModeServiceTest {

    @Mock
    private StripeConfigRepository stripeConfigRepository;

    @InjectMocks
    private RegistrationModeService registrationModeService;

    @Test
    void shouldDefaultToWaitlistWhenNoPersistedConfigExists() {
        when(stripeConfigRepository.findByName(StripeConfig.REGISTRATION_MODE)).thenReturn(Optional.empty());

        RegistrationModeResponse response = registrationModeService.getRegistrationMode();

        assertThat(response.mode()).isEqualTo(RegistrationMode.WAITLIST);
        assertThat(response.registrationOpen()).isFalse();
        assertThat(response.waitlistOpen()).isTrue();
        assertThat(response.updatedAt()).isNull();
    }

    @Test
    void shouldExposePersistedOpenMode() {
        LocalDateTime updatedAt = LocalDateTime.now();
        StripeConfig config = StripeConfig.builder()
            .name(StripeConfig.REGISTRATION_MODE)
            .value("OPEN")
            .updatedAt(updatedAt)
            .build();

        when(stripeConfigRepository.findByName(StripeConfig.REGISTRATION_MODE)).thenReturn(Optional.of(config));

        RegistrationModeResponse response = registrationModeService.getRegistrationMode();

        assertThat(response.mode()).isEqualTo(RegistrationMode.OPEN);
        assertThat(response.registrationOpen()).isTrue();
        assertThat(response.waitlistOpen()).isFalse();
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void shouldDefaultInvalidPersistedModeToWaitlist() {
        StripeConfig config = StripeConfig.builder()
            .name(StripeConfig.REGISTRATION_MODE)
            .value("not-a-mode")
            .build();

        when(stripeConfigRepository.findByName(StripeConfig.REGISTRATION_MODE)).thenReturn(Optional.of(config));

        RegistrationModeResponse response = registrationModeService.getRegistrationMode();

        assertThat(response.mode()).isEqualTo(RegistrationMode.WAITLIST);
        assertThat(response.registrationOpen()).isFalse();
        assertThat(response.waitlistOpen()).isTrue();
    }

    @Test
    void shouldUpdateModeWithAdminActor() {
        UUID adminId = UUID.randomUUID();
        StripeConfig initialConfig = StripeConfig.builder()
            .name(StripeConfig.REGISTRATION_MODE)
            .value("WAITLIST")
            .build();
        StripeConfig updatedConfig = StripeConfig.builder()
            .name(StripeConfig.REGISTRATION_MODE)
            .value("CLOSED")
            .build();

        when(stripeConfigRepository.findByName(StripeConfig.REGISTRATION_MODE))
            .thenReturn(Optional.of(initialConfig))
            .thenReturn(Optional.of(updatedConfig));

        RegistrationModeResponse response = registrationModeService.updateRegistrationMode(RegistrationMode.CLOSED, adminId);

        assertThat(response.mode()).isEqualTo(RegistrationMode.CLOSED);
        verify(stripeConfigRepository).upsertConfig(StripeConfig.REGISTRATION_MODE, "CLOSED");
    }

    @Test
    void shouldRejectPublicRegistrationUnlessModeIsOpen() {
        StripeConfig config = StripeConfig.builder()
            .name(StripeConfig.REGISTRATION_MODE)
            .value("WAITLIST")
            .build();

        when(stripeConfigRepository.findByName(StripeConfig.REGISTRATION_MODE)).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> registrationModeService.requireOpenForRegistration())
            .isInstanceOf(RegistrationModeService.RegistrationModeUnavailableException.class)
            .hasMessage("Registration is currently waitlist-only");
    }

    @Test
    void shouldRejectWaitlistWhenModeIsOpen() {
        StripeConfig config = StripeConfig.builder()
            .name(StripeConfig.REGISTRATION_MODE)
            .value("OPEN")
            .build();

        when(stripeConfigRepository.findByName(StripeConfig.REGISTRATION_MODE)).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> registrationModeService.requireWaitlistMode())
            .isInstanceOf(RegistrationModeService.RegistrationModeUnavailableException.class)
            .hasMessage("Waitlist is currently closed because registration is open");
    }
}
