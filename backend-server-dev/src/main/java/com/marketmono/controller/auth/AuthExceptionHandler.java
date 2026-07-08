package com.marketmono.controller.auth;

import com.marketmono.dto.honor.HonorCodeValidateResponse;
import com.marketmono.service.HonorCodeException;
import com.marketmono.service.auth.EmailValidationService.DisposableEmailException;
import com.marketmono.service.auth.HCaptchaService.CaptchaValidationException;
import com.marketmono.service.auth.RegistrationRateLimitService.RegistrationRateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Exception handler for authentication-related endpoints.
 */
@RestControllerAdvice(basePackages = "com.marketmono.controller.auth")
@Slf4j
public class AuthExceptionHandler {
    
    private static final String ERROR_KEY = "error";
    
    /**
     * Handle validation errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        Map<String, Object> response = new HashMap<>();
        response.put(ERROR_KEY, "Validation failed");
        response.put("details", errors);
        
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * Handle disposable email rejection.
     * Returns 422 Unprocessable Entity to indicate the email is not acceptable.
     */
    @ExceptionHandler(DisposableEmailException.class)
    public ResponseEntity<Map<String, String>> handleDisposableEmail(DisposableEmailException ex) {
        log.warn("Disposable email rejected: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(Map.of(
                ERROR_KEY, ex.getMessage(),
                "code", "DISPOSABLE_EMAIL"
            ));
    }
    
    /**
     * Handle CAPTCHA validation failures.
     * Returns 400 Bad Request with specific error code for frontend handling.
     */
    @ExceptionHandler(CaptchaValidationException.class)
    public ResponseEntity<Map<String, String>> handleCaptchaValidation(CaptchaValidationException ex) {
        log.warn("CAPTCHA validation failed: {}", ex.getMessage());
        return ResponseEntity
            .badRequest()
            .body(Map.of(
                ERROR_KEY, ex.getMessage(),
                "code", "CAPTCHA_FAILED"
            ));
    }
    
    /**
     * Handle registration rate limit exceeded.
     * Returns 429 Too Many Requests.
     */
    @ExceptionHandler(RegistrationRateLimitException.class)
    public ResponseEntity<Map<String, String>> handleRegistrationRateLimit(RegistrationRateLimitException ex) {
        log.warn("Registration rate limit exceeded: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .body(Map.of(
                ERROR_KEY, ex.getMessage(),
                "code", "RATE_LIMIT_EXCEEDED"
            ));
    }

    @ExceptionHandler(HonorCodeException.class)
    public ResponseEntity<HonorCodeValidateResponse> handleHonorCode(HonorCodeException ex) {
        log.warn("HONOR code rejected: {}", ex.getPublicMessage());
        return ResponseEntity
            .badRequest()
            .body(HonorCodeValidateResponse.rejected(ex.getPublicMessage()));
    }
    
    /**
     * Handle bad credentials (wrong password).
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(Map.of(ERROR_KEY, ex.getMessage()));
    }
    
    /**
     * Handle illegal argument (invalid token, email exists, etc.).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid request: {}", ex.getMessage());
        return ResponseEntity
            .badRequest()
            .body(Map.of(ERROR_KEY, ex.getMessage()));
    }
    
    /**
     * Handle illegal state (account locked, email not verified, etc.).
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        log.warn("Invalid state: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(Map.of(ERROR_KEY, ex.getMessage()));
    }
    
    /**
     * Handle unexpected errors.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        log.error("Unexpected error in auth controller", ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of(ERROR_KEY, "An unexpected error occurred. Please try again later."));
    }
}
