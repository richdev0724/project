package com.marketmono.service;

import lombok.Getter;

@Getter
public class HonorCodeException extends RuntimeException {

    private final String publicMessage;

    public HonorCodeException(String publicMessage) {
        super(publicMessage);
        this.publicMessage = publicMessage;
    }
}
