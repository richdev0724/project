package com.marketmono.controller.auth;

import com.marketmono.dto.waitlist.WaitlistRequest;
import com.marketmono.dto.waitlist.WaitlistResponse;
import com.marketmono.service.auth.AuthFailureMessages;
import com.marketmono.service.auth.HCaptchaService;
import com.marketmono.service.auth.RegistrationRateLimitService;
import com.marketmono.service.auth.WaitlistService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class WaitlistControllerTest {

    @Mock
    private WaitlistService waitlistService;

    @Mock
    private HCaptchaService hCaptchaService;

    @Mock
    private RegistrationRateLimitService registrationRateLimitService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private WaitlistController waitlistController;

    @Test
    void shouldVerifyCaptchaWithRemoteAddressWhenOnlyRealIpHeaderExists() {
        WaitlistRequest request = new WaitlistRequest("User", "user@example.com");
        when(hCaptchaService.isEnabled()).thenReturn(true);
        when(registrationRateLimitService.shouldRequireCaptcha(httpServletRequest)).thenReturn(true);
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        lenient().when(httpServletRequest.getHeader("X-Real-IP")).thenReturn("198.51.100.20");
        when(httpServletRequest.getRemoteAddr()).thenReturn("10.0.0.9");
        when(waitlistService.joinWaitlist(request)).thenReturn(WaitlistResponse.accepted());

        waitlistController.joinWaitlist(request, "captcha-token", httpServletRequest);

        verify(hCaptchaService).verify("captcha-token", "10.0.0.9");
    }

    @Test
    void shouldRejectBannedWaitlistEmailBeforeRateLimitCanMaskSupportMessage() {
        WaitlistRequest request = new WaitlistRequest("Blocked", "blocked@example.com");
        doThrow(new IllegalArgumentException(AuthFailureMessages.BANNED_EMAIL_SUPPORT_MESSAGE))
            .when(waitlistService)
            .rejectIfEmailBanned("blocked@example.com");

        assertThatThrownBy(() -> waitlistController.joinWaitlist(request, null, httpServletRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(AuthFailureMessages.BANNED_EMAIL_SUPPORT_MESSAGE);

        verify(waitlistService).rejectIfEmailBanned("blocked@example.com");
        verify(registrationRateLimitService, never()).checkRateLimit(httpServletRequest);
        verify(waitlistService, never()).joinWaitlist(request);
    }
}
