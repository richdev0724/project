package com.marketmono.controller.auth;

import com.marketmono.dto.waitlist.WaitlistRequest;
import com.marketmono.dto.waitlist.WaitlistResponse;
import com.marketmono.service.auth.HCaptchaService;
import com.marketmono.service.auth.RegistrationRateLimitService;
import com.marketmono.service.auth.WaitlistService;
import com.marketmono.util.HttpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public waitlist capture endpoint.
 */
@RestController
@RequestMapping("/api/v1/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;
    private final HCaptchaService hCaptchaService;
    private final RegistrationRateLimitService registrationRateLimitService;

    @PostMapping
    public ResponseEntity<WaitlistResponse> joinWaitlist(
            @Valid @RequestBody WaitlistRequest request,
            @RequestHeader(value = "X-Captcha-Token", required = false) String captchaToken,
            HttpServletRequest httpRequest) {

        waitlistService.rejectIfEmailBanned(request.email());

        registrationRateLimitService.checkRateLimit(httpRequest);

        boolean captchaRequired = hCaptchaService.isEnabled()
            && (registrationRateLimitService.shouldRequireCaptcha(httpRequest) || captchaToken != null);

        if (captchaRequired) {
            hCaptchaService.verify(captchaToken, HttpUtils.getClientIpWithoutRealIpFallback(httpRequest));
        }

        try {
            return ResponseEntity.ok(waitlistService.joinWaitlist(request));
        } finally {
            registrationRateLimitService.recordAttempt(httpRequest);
        }
    }

}
