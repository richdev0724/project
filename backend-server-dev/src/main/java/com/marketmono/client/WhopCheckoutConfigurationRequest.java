package com.marketmono.client;

import java.util.Map;

public record WhopCheckoutConfigurationRequest(
    String companyId,
    String planId,
    Map<String, String> metadata,
    String redirectUrl,
    String sourceUrl,
    boolean allowPromoCodes
) {
}
