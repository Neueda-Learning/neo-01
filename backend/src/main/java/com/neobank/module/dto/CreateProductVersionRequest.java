package com.neobank.module.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * What {@code POST /products} accepts — the fields needed to create a new version of product rules.
 *
 * <p>The service enforces additional rules beyond these annotations:
 * <ul>
 *   <li>{@code limitMin} must be strictly less than {@code limitMax}</li>
 *   <li>{@code channels} must contain only valid values: {@code WEB}, {@code MOBILE_APP},
 *       {@code BRANCH}, {@code PHONE}</li>
 * </ul>
 */
public record CreateProductVersionRequest(
        @NotBlank(message = "productCode must not be blank")
        String productCode,

        @NotNull(message = "minAge must not be null")
        @Min(value = 18, message = "minAge must be at least 18")
        Integer minAge,

        @NotNull(message = "limitMin must not be null")
        @Min(value = 0, message = "limitMin must be non-negative")
        Integer limitMin,

        @NotNull(message = "limitMax must not be null")
        @Min(value = 0, message = "limitMax must be non-negative")
        Integer limitMax,

        @NotNull(message = "active must not be null")
        Boolean active,

        @NotEmpty(message = "channels must not be empty")
        List<@NotBlank(message = "channel must not be blank") String> channels) {
}