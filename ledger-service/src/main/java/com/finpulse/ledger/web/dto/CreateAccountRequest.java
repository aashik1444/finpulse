package com.finpulse.ledger.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotBlank String ownerName,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @Positive long openingBalanceMinor
) {
}
