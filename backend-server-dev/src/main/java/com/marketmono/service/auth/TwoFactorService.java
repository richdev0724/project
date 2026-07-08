package com.marketmono.service.auth;

import com.marketmono.entity.auth.RecoveryCode;
import com.marketmono.entity.auth.User;
import com.marketmono.exception.TwoFactorQrCodeException;
import com.marketmono.repository.auth.RecoveryCodeRepository;
import com.marketmono.repository.auth.UserRepository;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import static dev.samstevens.totp.util.Utils.getDataUriForImage;

/**
 * Service for managing Two-Factor Authentication (2FA) using TOTP.
 * Supports Google Authenticator, Authy, and other TOTP-compatible apps.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TwoFactorService {
    
    private final UserRepository userRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final PasswordEncoder passwordEncoder;
    
    @Value("${app.name:MarketMono}")
    private String appName;
    
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int RECOVERY_CODE_LENGTH = 8;
    private static final SecureRandom secureRandom = new SecureRandom();
    
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    
    /**
     * Initialize 2FA setup for a user.
     * Returns the secret and QR code data URI for the user to scan.
     */
    @Transactional
    public TwoFactorSetupResponse initializeSetup(User user) {
        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            throw new IllegalStateException("2FA is already enabled for this account");
        }
        
        // Generate new secret
        String secret = secretGenerator.generate();
        
        // Save secret (not enabled yet, will be enabled after verification)
        user.setTwoFactorSecret(secret);
        userRepository.save(user);
        
        // Generate QR code
        QrData qrData = new QrData.Builder()
            .label(user.getEmail())
            .secret(secret)
            .issuer(appName)
            .algorithm(HashingAlgorithm.SHA1)
            .digits(6)
            .period(30)
            .build();
        
        String qrCodeUri;
        try {
            byte[] imageData = qrGenerator.generate(qrData);
            qrCodeUri = getDataUriForImage(imageData, qrGenerator.getImageMimeType());
        } catch (QrGenerationException e) {
            log.error("Failed to generate QR code for user: {}", user.getEmail(), e);
            throw new TwoFactorQrCodeException("Failed to generate QR code", e);
        }
        
        // Return the manual entry key (Base32 encoded secret) for users who can't scan QR
        String manualEntryKey = secret;
        
        log.info("2FA setup initialized for user: {}", user.getEmail());
        
        return new TwoFactorSetupResponse(qrCodeUri, manualEntryKey);
    }
    
    /**
     * Verify the TOTP code and enable 2FA for the user.
     * Also generates recovery codes.
     */
    @Transactional
    public List<String> verifyAndEnable(User user, String totpCode) {
        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            throw new IllegalStateException("2FA is already enabled for this account");
        }
        
        String secret = user.getTwoFactorSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Please initialize 2FA setup first");
        }
        
        // Verify the TOTP code
        if (!verifyCode(secret, totpCode)) {
            throw new IllegalArgumentException("Invalid verification code. Please try again.");
        }
        
        // Enable 2FA
        user.setTwoFactorEnabled(true);
        userRepository.save(user);
        
        // Generate recovery codes
        List<String> recoveryCodes = generateRecoveryCodes(user);
        
        log.info("2FA enabled for user: {}", user.getEmail());
        
        return recoveryCodes;
    }
    
    /**
     * Verify a TOTP code during login.
     */
    public boolean verifyTotpCode(User user, String code) {
        if (!Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            return true; // 2FA not enabled, no verification needed
        }
        
        String secret = user.getTwoFactorSecret();
        if (secret == null) {
            log.error("2FA enabled but no secret found for user: {}", user.getEmail());
            return false;
        }
        
        return verifyCode(secret, code);
    }
    
    /**
     * Verify a recovery code during login.
     * Recovery codes are single-use and will be invalidated after use.
     */
    @Transactional
    public boolean verifyRecoveryCode(User user, String code) {
        if (!Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            return false; // Recovery codes only work when 2FA is enabled
        }
        
        List<RecoveryCode> recoveryCodes = recoveryCodeRepository.findByUserAndUsedFalse(user);
        
        for (RecoveryCode recoveryCode : recoveryCodes) {
            if (passwordEncoder.matches(normalizeCode(code), recoveryCode.getCodeHash())) {
                // Mark as used
                recoveryCode.markAsUsed();
                recoveryCodeRepository.save(recoveryCode);
                
                log.info("Recovery code used for user: {}", user.getEmail());
                
                // Check if user is running low on recovery codes
                long remainingCodes = recoveryCodes.stream().filter(rc -> !rc.isUsed()).count() - 1;
                if (remainingCodes <= 2) {
                    log.warn("User {} has only {} recovery codes remaining", user.getEmail(), remainingCodes);
                }
                
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Disable 2FA for a user.
     * Requires password verification for security.
     */
    @Transactional
    public void disable(User user, String password) {
        if (!Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            throw new IllegalStateException("2FA is not enabled for this account");
        }
        
        // Verify password
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid password");
        }
        
        // Disable 2FA
        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        userRepository.save(user);
        
        // Delete all recovery codes
        recoveryCodeRepository.deleteByUser(user);
        
        log.info("2FA disabled for user: {}", user.getEmail());
    }
    
    /**
     * Regenerate recovery codes for a user.
     * Invalidates all existing recovery codes.
     */
    @Transactional
    public List<String> regenerateRecoveryCodes(User user, String totpCode) {
        if (!Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            throw new IllegalStateException("2FA must be enabled to regenerate recovery codes");
        }
        
        // Verify TOTP code
        if (!verifyTotpCode(user, totpCode)) {
            throw new IllegalArgumentException("Invalid verification code");
        }
        
        // Delete existing recovery codes
        recoveryCodeRepository.deleteByUser(user);
        
        // Generate new recovery codes
        List<String> newCodes = generateRecoveryCodes(user);
        
        log.info("Recovery codes regenerated for user: {}", user.getEmail());
        
        return newCodes;
    }
    
    /**
     * Get the count of remaining (unused) recovery codes.
     */
    public int getRemainingRecoveryCodeCount(User user) {
        return (int) recoveryCodeRepository.countByUserAndUsedFalse(user);
    }
    
    /**
     * Check if 2FA is required for the given user during login.
     */
    public boolean isTwoFactorRequired(User user) {
        return Boolean.TRUE.equals(user.getTwoFactorEnabled());
    }
    
    // --- Private Helper Methods ---
    
    private boolean verifyCode(String secret, String code) {
        try {
            return codeVerifier.isValidCode(secret, code);
        } catch (Exception e) {
            log.error("Error verifying TOTP code", e);
            return false;
        }
    }
    
    private List<String> generateRecoveryCodes(User user) {
        List<String> plainCodes = new ArrayList<>();
        
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String code = generateRandomCode();
            plainCodes.add(formatCode(code));
            
            RecoveryCode recoveryCode = RecoveryCode.builder()
                .user(user)
                .codeHash(passwordEncoder.encode(normalizeCode(code)))
                .build();
            
            recoveryCodeRepository.save(recoveryCode);
        }
        
        return plainCodes;
    }
    
    private String generateRandomCode() {
        byte[] bytes = new byte[RECOVERY_CODE_LENGTH / 2];
        secureRandom.nextBytes(bytes);
        return bytesToHex(bytes).toUpperCase();
    }
    
    private String formatCode(String code) {
        // Format as XXXX-XXXX for readability
        if (code.length() == 8) {
            return code.substring(0, 4) + "-" + code.substring(4);
        }
        return code;
    }
    
    private String normalizeCode(String code) {
        // Remove formatting (dashes, spaces) for comparison
        return code.replaceAll("[\\s-]", "").toUpperCase();
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * Response record for 2FA setup initialization.
     */
    public record TwoFactorSetupResponse(
        String qrCodeDataUri,
        String manualEntryKey
    ) {}
}
