package com.marketmono.dto;

import java.util.UUID;

public record CheckoutPriceCapabilityDto(
    UUID productId,
    UUID priceId,
    String billingPeriod,
    boolean stripeConfigured,
    boolean whopConfigured,
    boolean checkoutConfigured,
    String disabledReason
) {
}
