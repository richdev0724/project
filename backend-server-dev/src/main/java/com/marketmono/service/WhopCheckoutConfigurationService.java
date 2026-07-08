package com.marketmono.service;

import com.marketmono.config.WhopCheckoutProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WhopCheckoutConfigurationService {

    private final WhopCheckoutProperties properties;

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public boolean allowPromoCodes() {
        return properties.isAllowPromoCodes();
    }
}
