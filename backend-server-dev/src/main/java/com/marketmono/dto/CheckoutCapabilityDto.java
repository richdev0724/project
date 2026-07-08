package com.marketmono.dto;

import com.marketmono.entity.CheckoutProviderOverride;

import java.util.List;

public record CheckoutCapabilityDto(
    CheckoutProviderOverride provider,
    boolean enabled,
    String disabledReason,
    boolean allowPromoCodes,
    List<CheckoutPriceCapabilityDto> prices
) {
}
