package com.marketmono.service.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * Service for verifying hCaptcha tokens.
 * 
 * hCaptcha is a privacy-focused CAPTCHA alternative that:
 * - Does not harvest user data for advertising
 * - Is GDPR compliant
 * - Has a generous free tier (1M+ verifications/month)
 * - Challenges less than 0.1% of legitimate users in invisible mode
 */
@Service
@Slf4j
public class HCaptchaService {
    
    private static final String HCAPTCHA_VERIFY_URL = "https://hcaptcha.com/siteverify";
    
    private final WebClient webClient;
    
    @Value("${hcaptcha.secret-key:}")
    private String secretKey;
    
    @Value("${hcaptcha.site-key:}")
    private String siteKey;
    
    @Value("${hcaptcha.enabled:false}")
    private boolean enabled;
    
    public HCaptchaService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }
    
    /**
     * Verifies an hCaptcha token.
     * 
     * @param token The hCaptcha response token from the frontend
     * @param remoteIp Optional client IP address for additional verification
     * @throws CaptchaValidationException if the token is invalid or verification fails
     */
    public void verify(String token, String remoteIp) {
        if (!enabled) {
            log.debug("hCaptcha verification is disabled");
            return;
        }
        
        if (token == null || token.isBlank()) {
            throw new CaptchaValidationException("CAPTCHA verification required");
        }
        
        if (secretKey == null || secretKey.isBlank()) {
            log.error("hCaptcha secret key is not configured");
            throw new CaptchaValidationException("CAPTCHA service is not configured");
        }
        
        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("secret", secretKey);
            formData.add("response", token);
            if (remoteIp != null && !remoteIp.isBlank()) {
                formData.add("remoteip", remoteIp);
            }
            
            HCaptchaResponse response = webClient.post()
                .uri(HCAPTCHA_VERIFY_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(HCaptchaResponse.class)
                .block();
            
            if (response == null || !response.success()) {
                String errorCodes = response != null && response.errorCodes() != null 
                    ? String.join(", ", response.errorCodes()) 
                    : "unknown";
                log.warn("hCaptcha verification failed: {}", errorCodes);
                throw new CaptchaValidationException("CAPTCHA verification failed. Please try again.");
            }
            
            log.debug("hCaptcha verification successful");
            
        } catch (CaptchaValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("hCaptcha verification error", e);
            throw new CaptchaValidationException("CAPTCHA verification service unavailable. Please try again later.");
        }
    }
    
    /**
     * Verifies an hCaptcha token without IP address.
     * 
     * @param token The hCaptcha response token from the frontend
     * @throws CaptchaValidationException if the token is invalid or verification fails
     */
    public void verify(String token) {
        verify(token, null);
    }
    
    /**
     * Checks if hCaptcha is enabled.
     * 
     * @return true if hCaptcha verification is enabled
     */
    public boolean isEnabled() {
        return enabled && secretKey != null && !secretKey.isBlank();
    }
    
    /**
     * Gets the site key for frontend integration.
     * 
     * @return The hCaptcha site key
     */
    public String getSiteKey() {
        return siteKey;
    }
    
    /**
     * Response record for hCaptcha verification API.
     * Note: hCaptcha API uses "error-codes" (kebab-case) but Jackson maps to errorCodes
     */
    public record HCaptchaResponse(
        boolean success,
        String challenge_ts,
        String hostname,
        List<String> errorCodes
    ) {}
    
    /**
     * Custom exception for CAPTCHA validation failures.
     */
    public static class CaptchaValidationException extends RuntimeException {
        public CaptchaValidationException(String message) {
            super(message);
        }
    }
}
