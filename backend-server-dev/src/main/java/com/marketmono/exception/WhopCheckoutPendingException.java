package com.marketmono.exception;

import com.marketmono.dto.WhopCheckoutPendingResponse;
import lombok.Getter;

@Getter
public class WhopCheckoutPendingException extends RuntimeException {

    private final WhopCheckoutPendingResponse pending;

    public WhopCheckoutPendingException(WhopCheckoutPendingResponse pending) {
        super("Whop checkout is already in progress");
        this.pending = pending;
    }
}
