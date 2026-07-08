package com.marketmono.dto;

import java.util.UUID;

public record WhopCheckoutSessionResponse(
    String purchaseUrl,
    UUID attemptId,
    String sessionId,
    String planId,
    String buyerEmail,
    String lockMethod
) {
}
