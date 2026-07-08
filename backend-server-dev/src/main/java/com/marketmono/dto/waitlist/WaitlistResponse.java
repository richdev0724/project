package com.marketmono.dto.waitlist;

/**
 * Safe public waitlist submission response.
 */
public record WaitlistResponse(
    String status,
    String message
) {
    public static WaitlistResponse accepted() {
        return new WaitlistResponse(
            "ACCEPTED",
            "Thanks for joining the Market Mono waitlist. We'll send updates as registration opens."
        );
    }

    public static WaitlistResponse alreadyInSystem() {
        return new WaitlistResponse(
            "ACCEPTED",
            "Thanks. This email is already in our system, so no additional action is needed."
        );
    }
}
