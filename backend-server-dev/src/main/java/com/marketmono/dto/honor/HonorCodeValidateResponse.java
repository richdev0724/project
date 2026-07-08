package com.marketmono.dto.honor;

public record HonorCodeValidateResponse(
    boolean valid,
    String promoCode,
    String message
) {
    public static HonorCodeValidateResponse accepted(String promoCode) {
        return new HonorCodeValidateResponse(true, promoCode, "HONOR code accepted.");
    }

    public static HonorCodeValidateResponse rejected(String message) {
        return new HonorCodeValidateResponse(false, null, message);
    }
}
