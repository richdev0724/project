package com.marketmono.dto;

import com.marketmono.entity.BillingProvider;

public record BillingPortalResponse(
    String url,
    BillingProvider billingProvider,
    String source,
    String message
) {}
