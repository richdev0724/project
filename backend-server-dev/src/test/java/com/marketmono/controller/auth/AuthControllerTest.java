package com.marketmono.controller.auth;

import com.marketmono.dto.auth.GoogleLoginRequest;
import com.marketmono.dto.auth.LoginRequest;
import com.marketmono.dto.auth.LoginResponse;
import com.marketmono.dto.auth.RegisterRequest;
import com.marketmono.dto.auth.VerifyEmailRequest;
import com.marketmono.dto.auth.VerifyEmailResponse;
import com.marketmono.service.auth.AuthFailureMessages;
import com.marketmono.service.auth.AuthService;
import com.marketmono.service.auth.HCaptchaService;
import com.marketmono.service.auth.RegistrationRateLimitService;
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
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private HCaptchaService hCaptchaService;

    @Mock
    private RegistrationRateLimitService registrationRateLimitService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private AuthController authController;

    @Test
    void shouldRejectBannedRegistrationBeforeRateLimitCanMaskSupportMessage() {
        RegisterRequest request = new RegisterRequest(
            "blocked@example.com",
            "Blocked",
            null,
            "long-enough-password"
        );
        doThrow(new IllegalArgumentException(AuthFailureMessages.BANNED_EMAIL_SUPPORT_MESSAGE))
            .when(authService)
            .rejectIfEmailBanned("blocked@example.com");

        assertThatThrownBy(() -> authController.register(request, null, httpServletRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(AuthFailureMessages.BANNED_EMAIL_SUPPORT_MESSAGE);

        verify(authService).rejectIfEmailBanned("blocked@example.com");
        verify(registrationRateLimitService, never()).checkRateLimit(httpServletRequest);
        verify(authService, never()).register(request);
        verify(registrationRateLimitService, never()).recordAttempt(httpServletRequest);
    }

    @Test
    void shouldResolveLoginIpFromForwardedForBeforeRemoteAddress() {
        LoginRequest request = new LoginRequest("user@example.com", "password");
        LoginResponse response = new LoginResponse(false, null, null, null, null);
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("Browser");
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10, 10.0.0.5");
        when(authService.login(request, "Browser", "203.0.113.10")).thenReturn(response);

        authController.login(request, httpServletRequest);

        verify(authService).login(request, "Browser", "203.0.113.10");
    }

    @Test
    void shouldResolveLoginIpFromRemoteAddressWithoutRealIpFallback() {
        LoginRequest request = new LoginRequest("user@example.com", "password");
        LoginResponse response = new LoginResponse(false, null, null, null, null);
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("Browser");
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        lenient().when(httpServletRequest.getHeader("X-Real-IP")).thenReturn("198.51.100.20");
        when(httpServletRequest.getRemoteAddr()).thenReturn("10.0.0.9");
        when(authService.login(request, "Browser", "10.0.0.9")).thenReturn(response);

        authController.login(request, httpServletRequest);

        verify(authService).login(request, "Browser", "10.0.0.9");
    }

    @Test
    void shouldResolveGoogleLoginIpFromForwardedForBeforeRemoteAddress() {
        GoogleLoginRequest request = new GoogleLoginRequest("google-id-token");
        LoginResponse response = new LoginResponse(false, null, null, null, null);
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("Browser");
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.11, 10.0.0.5");
        when(authService.loginWithGoogle(request, "Browser", "203.0.113.11")).thenReturn(response);

        authController.googleLogin(request, httpServletRequest);

        verify(authService).loginWithGoogle(request, "Browser", "203.0.113.11");
    }

    @Test
    void shouldPassContinuationTokenAndClientContextForEmailVerification() {
        VerifyEmailRequest request = new VerifyEmailRequest("verify-token", "continuation-token");
        VerifyEmailResponse response = VerifyEmailResponse.verifiedOnly(
            "Email verified successfully. You can now log in."
        );
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("Browser");
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.12, 10.0.0.5");
        when(authService.verifyEmail("verify-token", "continuation-token", "Browser", "203.0.113.12"))
            .thenReturn(response);

        authController.verifyEmail(request, httpServletRequest);

        verify(authService).verifyEmail("verify-token", "continuation-token", "Browser", "203.0.113.12");
    }
}
