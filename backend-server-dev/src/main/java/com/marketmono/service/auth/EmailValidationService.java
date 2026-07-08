package com.marketmono.service.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service for validating email addresses.
 * Checks for disposable/temporary email domains that should be blocked during registration.
 * 
 * Uses the community-maintained disposable-email-domains list from:
 * https://github.com/disposable/disposable-email-domains
 */
@Service
@Slf4j
public class EmailValidationService {
    
    private static final String DISPOSABLE_DOMAINS_URL = 
        "https://disposable.github.io/disposable-email-domains/domains.txt";
    
    private final WebClient webClient;
    
    // Thread-safe set of known disposable email domains
    private final Set<String> disposableDomains = ConcurrentHashMap.newKeySet();
    
    // Cache for domain lookups (with subdomain support)
    private final Cache<String, Boolean> domainCache;
    
    @Value("${email.validation.enabled:true}")
    private boolean validationEnabled;
    
    public EmailValidationService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();
        this.domainCache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();
    }
    
    /**
     * Load disposable domains on startup.
     */
    @PostConstruct
    public void init() {
        if (validationEnabled) {
            refreshDisposableDomains();
        }
    }
    
    /**
     * Refresh disposable domains list daily.
     */
    @Scheduled(cron = "0 0 3 * * ?") // Daily at 3 AM
    @SchedulerLock(name = "emailValidation_refreshDisposableDomains", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    public void scheduledRefresh() {
        if (validationEnabled) {
            refreshDisposableDomains();
        }
    }
    
    /**
     * Downloads and updates the disposable domains list.
     */
    private void refreshDisposableDomains() {
        try {
            String response = webClient.get()
                .uri(DISPOSABLE_DOMAINS_URL)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();
            
            if (response != null && !response.isBlank()) {
                Set<String> newDomains = new HashSet<>();
                for (String line : response.split("\n")) {
                    String domain = line.trim().toLowerCase();
                    if (!domain.isEmpty() && !domain.startsWith("#")) {
                        newDomains.add(domain);
                    }
                }
                
                if (!newDomains.isEmpty()) {
                    disposableDomains.clear();
                    disposableDomains.addAll(newDomains);
                    domainCache.invalidateAll();
                    log.info("Loaded {} disposable email domains", newDomains.size());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to refresh disposable domains list: {}", e.getMessage());
            // Keep using existing list if refresh fails
        }
    }
    
    /**
     * Validates an email address for registration.
     * 
     * @param email The email address to validate
     * @throws DisposableEmailException if the email is from a known disposable/temporary email provider
     */
    public void validateEmail(String email) {
        if (!validationEnabled) {
            return;
        }
        
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        
        String normalizedEmail = email.trim().toLowerCase();
        
        // Check for basic email format
        if (!normalizedEmail.contains("@") || !normalizedEmail.contains(".")) {
            throw new IllegalArgumentException("Invalid email format");
        }
        
        // Check if the email domain is a known disposable email provider
        if (isDisposableEmail(normalizedEmail)) {
            log.warn("Blocked registration attempt with disposable email: {}", maskEmail(normalizedEmail));
            throw new DisposableEmailException("Temporary or disposable email addresses are not allowed. Please use a permanent email address.");
        }
        
        log.debug("Email validated successfully: {}", maskEmail(normalizedEmail));
    }
    
    /**
     * Checks if an email is from a disposable domain.
     * Supports subdomain matching (e.g., blocks "mail.tempmail.com" if "tempmail.com" is in the list).
     * 
     * @param email The email address to check
     * @return true if the email is from a disposable domain, false otherwise
     */
    public boolean isDisposableEmail(String email) {
        if (!validationEnabled || email == null || email.isBlank()) {
            return false;
        }
        
        String domain = extractDomain(email);
        if (domain == null) {
            return false;
        }
        
        // Check cache first
        Boolean cached = domainCache.getIfPresent(domain);
        if (cached != null) {
            return cached;
        }
        
        // Check domain and all parent domains (for subdomain matching)
        boolean isDisposable = checkDomainAndParents(domain);
        domainCache.put(domain, isDisposable);
        
        return isDisposable;
    }
    
    /**
     * Checks if the domain or any of its parent domains are in the disposable list.
     */
    private boolean checkDomainAndParents(String domain) {
        // Check exact match
        if (disposableDomains.contains(domain)) {
            return true;
        }
        
        // Check parent domains (for subdomain matching)
        // e.g., mail.tempmail.com -> tempmail.com -> com
        int dotIndex = domain.indexOf('.');
        while (dotIndex > 0) {
            String parentDomain = domain.substring(dotIndex + 1);
            if (disposableDomains.contains(parentDomain)) {
                return true;
            }
            dotIndex = parentDomain.indexOf('.');
            if (dotIndex > 0) {
                domain = parentDomain;
            } else {
                break;
            }
        }
        
        return false;
    }
    
    /**
     * Extracts the domain from an email address.
     */
    private String extractDomain(String email) {
        int atIndex = email.lastIndexOf('@');
        if (atIndex > 0 && atIndex < email.length() - 1) {
            return email.substring(atIndex + 1).toLowerCase().trim();
        }
        return null;
    }
    
    /**
     * Gets the count of loaded disposable domains.
     */
    public int getDisposableDomainCount() {
        return disposableDomains.size();
    }
    
    /**
     * Masks email for logging purposes (privacy).
     * Example: "user@example.com" -> "u***@example.com"
     */
    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return email;
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
    
    /**
     * Custom exception for disposable email addresses.
     */
    public static class DisposableEmailException extends RuntimeException {
        public DisposableEmailException(String message) {
            super(message);
        }
    }
}
