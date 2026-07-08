package com.marketmono.service;

import com.marketmono.dto.*;
import com.marketmono.entity.StripeConfig;
import com.marketmono.entity.StripeConfigAuditLog;
import com.marketmono.entity.StripeMode;
import com.marketmono.repository.StripeConfigAuditLogRepository;
import com.marketmono.repository.StripeConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service for Stripe configuration management using key-value storage.
 * Handles key storage and audit logging.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeConfigService {
    
    private final StripeConfigRepository stripeConfigRepository;
    private final StripeConfigAuditLogRepository auditLogRepository;
    
    // ============================================
    // Read Operations - Individual Values
    // ============================================
    
    /**
     * Get a configuration value by name.
     */
    @Transactional(readOnly = true)
    public Optional<String> getValue(String name) {
        return stripeConfigRepository.findValueByName(name);
    }
    
    /**
     * Get a configuration value by name, with default.
     */
    @Transactional(readOnly = true)
    public String getValue(String name, String defaultValue) {
        return stripeConfigRepository.findValueByName(name).orElse(defaultValue);
    }
    
    /**
     * Get configuration value as integer.
     */
    @Transactional(readOnly = true)
    public int getValueAsInt(String name, int defaultValue) {
        return stripeConfigRepository.findByName(name)
            .map(config -> config.getValueAsInt(defaultValue))
            .orElse(defaultValue);
    }
    
    /**
     * Get the grace period days for PAST_DUE subscriptions.
     * @return Number of days to allow access for PAST_DUE subscriptions
     */
    @Transactional(readOnly = true)
    public int getPastDueGracePeriodDays() {
        return getValueAsInt(StripeConfig.PAST_DUE_GRACE_PERIOD_DAYS, 1);
    }
    
    /**
     * Get the default Stripe mode.
     */
    @Transactional(readOnly = true)
    public StripeMode getDefaultMode() {
        String mode = getValue(StripeConfig.DEFAULT_MODE, "TEST");
        try {
            return StripeMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            return StripeMode.TEST;
        }
    }
    
    // ============================================
    // Read Operations - Keys
    // ============================================
    
    /**
     * Get public key for a specific mode.
     */
    @Transactional(readOnly = true)
    public String getPublicKey(StripeMode mode) {
        StripeMode effectiveMode = resolveMode(mode);
        String configName = effectiveMode == StripeMode.PROD 
            ? StripeConfig.PROD_PUBLIC_KEY 
            : StripeConfig.TEST_PUBLIC_KEY;
        return getValue(configName).orElse(null);
    }
    
    /**
     * Get secret key for a specific mode (internal use only).
     */
    @Transactional(readOnly = true)
    public String getSecretKey(StripeMode mode) {
        StripeMode effectiveMode = resolveMode(mode);
        String configName = effectiveMode == StripeMode.PROD 
            ? StripeConfig.PROD_SECRET_KEY 
            : StripeConfig.TEST_SECRET_KEY;
        return getValue(configName)
            .orElseThrow(() -> new IllegalStateException("Stripe secret key not configured for mode: " + effectiveMode));
    }
    
    /**
     * Get webhook secret for a specific mode.
     */
    @Transactional(readOnly = true)
    public String getWebhookSecret(StripeMode mode) {
        StripeMode effectiveMode = resolveMode(mode);
        String configName = effectiveMode == StripeMode.PROD 
            ? StripeConfig.PROD_WEBHOOK_SECRET 
            : StripeConfig.TEST_WEBHOOK_SECRET;
        return getValue(configName).orElse(null);
    }
    
    /**
     * Resolve DEFAULT mode to actual mode.
     */
    private StripeMode resolveMode(StripeMode mode) {
        if (mode == null || mode == StripeMode.DEFAULT) {
            return getDefaultMode();
        }
        return mode;
    }
    
    // ============================================
    // Read Operations - Aggregated Config
    // ============================================
    
    /**
     * Get current Stripe configuration with masked keys.
     */
    @Transactional(readOnly = true)
    public StripeConfigDto getConfig() {
        Map<String, String> configMap = loadAllConfigAsMap();
        return StripeConfigDto.fromMap(configMap, false);
    }
    
    /**
     * Get config with public keys unmasked (for frontend Stripe.js initialization).
     */
    @Transactional(readOnly = true)
    public StripeConfigDto getConfigWithPublicKeys() {
        Map<String, String> configMap = loadAllConfigAsMap();
        return StripeConfigDto.fromMap(configMap, true);
    }
    
    /**
     * Load all configuration as a map.
     */
    @Transactional(readOnly = true)
    public Map<String, String> loadAllConfigAsMap() {
        List<StripeConfig> configs = stripeConfigRepository.findAll();
        Map<String, String> configMap = new HashMap<>();
        for (StripeConfig config : configs) {
            configMap.put(config.getName(), config.getValue());
        }
        return configMap;
    }
    
    /**
     * Check if Stripe is configured.
     */
    @Transactional(readOnly = true)
    public boolean isConfigured() {
        return stripeConfigRepository.hasAnyKeysConfigured();
    }
    
    /**
     * Check if test keys are configured.
     */
    @Transactional(readOnly = true)
    public boolean hasTestKeysConfigured() {
        return stripeConfigRepository.hasTestKeysConfigured();
    }
    
    /**
     * Check if production keys are configured.
     */
    @Transactional(readOnly = true)
    public boolean hasProdKeysConfigured() {
        return stripeConfigRepository.hasProdKeysConfigured();
    }
    
    // ============================================
    // Update Operations
    // ============================================
    
    /**
     * Set a single configuration value with audit logging.
     */
    @Transactional
    public void setValue(String name, String value, UUID adminId, String ipAddress) {
        String oldValue = getValue(name).orElse(null);
        
        // Create or update config
        stripeConfigRepository.upsertConfig(name, value);
        
        // Create audit log
        StripeConfigAuditLog auditLog = createAuditLog(name, oldValue, value, adminId, ipAddress);
        auditLogRepository.save(auditLog);
        
        log.info("Updated Stripe config '{}' by admin {}", name, adminId);
    }
    
    /**
     * Update Stripe configuration with audit logging.
     * @param request The update request containing new configuration values
     * @param adminId The ID of the admin making the change
     * @param ipAddress The IP address of the request
     */
    @Transactional
    public StripeConfigDto updateConfig(UpdateStripeConfigRequest request, UUID adminId, String ipAddress) {
        Map<String, String> currentConfig = loadAllConfigAsMap();
        List<StripeConfigAuditLog> auditLogs = new ArrayList<>();
        
        // Track and update each field if provided
        if (request.testPublicKey() != null) {
            updateConfigValue(StripeConfig.TEST_PUBLIC_KEY, request.testPublicKey(), currentConfig, auditLogs, adminId, ipAddress);
        }
        if (request.testSecretKey() != null) {
            updateConfigValue(StripeConfig.TEST_SECRET_KEY, request.testSecretKey(), currentConfig, auditLogs, adminId, ipAddress);
        }
        if (request.testWebhookSecret() != null) {
            updateConfigValue(StripeConfig.TEST_WEBHOOK_SECRET, request.testWebhookSecret(), currentConfig, auditLogs, adminId, ipAddress);
        }
        if (request.prodPublicKey() != null) {
            updateConfigValue(StripeConfig.PROD_PUBLIC_KEY, request.prodPublicKey(), currentConfig, auditLogs, adminId, ipAddress);
        }
        if (request.prodSecretKey() != null) {
            updateConfigValue(StripeConfig.PROD_SECRET_KEY, request.prodSecretKey(), currentConfig, auditLogs, adminId, ipAddress);
        }
        if (request.prodWebhookSecret() != null) {
            updateConfigValue(StripeConfig.PROD_WEBHOOK_SECRET, request.prodWebhookSecret(), currentConfig, auditLogs, adminId, ipAddress);
        }
        if (request.defaultMode() != null) {
            updateConfigValue(StripeConfig.DEFAULT_MODE, request.defaultMode().name(), currentConfig, auditLogs, adminId, ipAddress);
        }
        if (request.pastDueGracePeriodDays() != null) {
            updateConfigValue(
                StripeConfig.PAST_DUE_GRACE_PERIOD_DAYS,
                request.pastDueGracePeriodDays().toString(),
                currentConfig,
                auditLogs,
                adminId,
                ipAddress
            );
        }
        if (request.betaPromoCode() != null) {
            updateConfigValue(StripeConfig.BETA_PROMO_CODE, request.betaPromoCode(), currentConfig, auditLogs, adminId, ipAddress);
        }
        if (request.betaPromotionCodeId() != null) {
            updateConfigValue(
                StripeConfig.BETA_PROMOTION_CODE_ID,
                request.betaPromotionCodeId(),
                currentConfig,
                auditLogs,
                adminId,
                ipAddress
            );
        }
        if (request.betaTrialDays() != null) {
            updateConfigValue(
                StripeConfig.BETA_TRIAL_DAYS,
                request.betaTrialDays().toString(),
                currentConfig,
                auditLogs,
                adminId,
                ipAddress
            );
        }
        
        // Save audit logs
        if (!auditLogs.isEmpty()) {
            auditLogRepository.saveAll(auditLogs);
            log.info("Updated Stripe config with {} field changes by admin {}", auditLogs.size(), adminId);
        }
        
        return getConfig();
    }
    
    private void updateConfigValue(String name, String newValue, Map<String, String> currentConfig,
            List<StripeConfigAuditLog> auditLogs, UUID adminId, String ipAddress) {
        String oldValue = currentConfig.get(name);
        stripeConfigRepository.upsertConfig(name, newValue);
        auditLogs.add(createAuditLog(name, oldValue, newValue, adminId, ipAddress));
    }
    
    // ============================================
    // Audit Log Operations
    // ============================================
    
    /**
     * Get recent audit log entries.
     */
    @Transactional(readOnly = true)
    public List<StripeConfigAuditLogDto> getRecentAuditLogs() {
        return auditLogRepository.findTop20ByOrderByChangedAtDesc().stream()
            .map(StripeConfigAuditLogDto::from)
            .toList();
    }
    
    /**
     * Get all audit log entries.
     */
    @Transactional(readOnly = true)
    public List<StripeConfigAuditLogDto> getAllAuditLogs() {
        return auditLogRepository.findAllByOrderByChangedAtDesc().stream()
            .map(StripeConfigAuditLogDto::from)
            .toList();
    }
    
    // ============================================
    // Helper Methods
    // ============================================
    
    private StripeConfigAuditLog createAuditLog(String fieldName, String oldValue, String newValue, 
            UUID adminId, String ipAddress) {
        return StripeConfigAuditLog.createEntry(fieldName, oldValue, newValue, adminId, ipAddress);
    }
}
