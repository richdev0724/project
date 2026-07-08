package com.marketmono.dto.honor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HonorCodeValidateRequest(
    @NotBlank(message = "HONOR code is required")
    @Size(max = 32, message = "HONOR code is too long")
    String code
) {
}
