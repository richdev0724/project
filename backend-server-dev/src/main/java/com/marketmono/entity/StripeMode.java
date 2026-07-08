package com.marketmono.entity;

/**
 * Stripe mode for determining which API keys to use.
 * - TEST: Use test keys for development/testing
 * - PROD: Use production keys for live transactions
 * - DEFAULT: Inherit from global stripe config setting
 */
public enum StripeMode {
    TEST,
    PROD,
    DEFAULT
}
