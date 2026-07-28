package com.neobank.module.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * UC-05: Override Case — request body for POST /cases/{id}/override.
 *
 * <p>Mandatory fields: {@code newOutcome}, {@code reason}, {@code operator}.
 * Each override is logged for audit and re-notifies the orchestrator.</p>
 */
public record OverrideCaseRequest(
        @NotBlank(message = "newOutcome must not be blank")
        @Pattern(regexp = "PASSED|FAILED|REVIEW", message = "newOutcome must be PASSED, FAILED or REVIEW")
        String newOutcome,

        @NotBlank(message = "reason must not be blank")
        String reason,

        @NotBlank(message = "operator must not be blank")
        String operator) {
}
