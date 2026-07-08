package com.marketmono.entity;

/**
 * Subscription status tracking the lifecycle of a subscription.
 */
public enum SubscriptionStatus {
    /** In trial period */
    TRIALING,
    
    /** Active and paid */
    ACTIVE,
    
    /** Payment failed, in grace period */
    PAST_DUE,
    
    /** Canceled by user or system */
    CANCELED,
    
    /** Waiting for first payment to complete */
    INCOMPLETE,
    
    /** Manually paused (future feature) */
    PAUSED
}
