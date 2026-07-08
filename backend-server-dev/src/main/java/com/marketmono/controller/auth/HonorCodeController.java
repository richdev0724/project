package com.marketmono.controller.auth;

import com.marketmono.dto.honor.HonorCodeValidateRequest;
import com.marketmono.dto.honor.HonorCodeValidateResponse;
import com.marketmono.service.HonorCodeService;
import com.marketmono.service.auth.HCaptchaService;
import com.marketmono.service.auth.RegistrationRateLimitService;
import com.marketmono.util.HttpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/honor-codes")
@RequiredArgsConstructor
public class HonorCodeController {

    private final HonorCodeService honorCodeService;
    private final HCaptchaService hCaptchaService;
    private final RegistrationRateLimitService registrationRateLimitService;

    @PostMapping("/validate")
    public ResponseEntity<HonorCodeValidateResponse> validateHonorCode(
            @Valid @RequestBody HonorCodeValidateRequest request,
            @RequestHeader(value = "X-Captcha-Token", required = false) String captchaToken,
            HttpServletRequest httpRequest) {

        registrationRateLimitService.checkRateLimit(httpRequest);

        boolean captchaRequired = hCaptchaService.isEnabled()
            && (registrationRateLimitService.shouldRequireCaptcha(httpRequest) || captchaToken != null);

        if (captchaRequired) {
            hCaptchaService.verify(captchaToken, HttpUtils.getClientIpWithoutRealIpFallback(httpRequest));
        }

        try {
            HonorCodeValidateResponse response = honorCodeService.validate(request.code());
            if (!response.valid()) {
                return ResponseEntity.badRequest().body(response);
            }
            return ResponseEntity.ok(response);
        } finally {
            registrationRateLimitService.recordAttempt(httpRequest);
        }
    }
}
