package com.marketmono.dto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the upstream upstream market data provider query parameter name for a request DTO component.
 * Falls back to {@code @BindParam} when omitted.
 */
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.RUNTIME)
public @interface UnusualWhalesParam {
    String value();
}
