package com.marketmono.dto;

/**
 * Feature-level subscription access flags used for finer-grained gating.
 *
 * The current simplified product schema no longer stores feature flags in the
 * database, so the first implementation keeps these flags code-driven. All
 * current paid products intentionally map to the same premium feature set.
 */
public record SubscriptionFeatureAccessDto(
    boolean dashboard,
    boolean flow,
    boolean alerts,
    boolean darkFlow,
    boolean contractInspector,
    boolean politicianTrades,
    boolean signals,
    boolean monoAi,
    boolean monoStrata
) {
    public static SubscriptionFeatureAccessDto none() {
        return new SubscriptionFeatureAccessDto(false, false, false, false, false, false, false, false, false);
    }

    public static SubscriptionFeatureAccessDto premium() {
        return new SubscriptionFeatureAccessDto(true, true, true, true, true, true, true, true, true);
    }
}