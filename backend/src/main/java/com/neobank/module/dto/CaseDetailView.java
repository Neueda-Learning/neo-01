package com.neobank.module.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Response body for UC02 case review.
 */
public record CaseDetailView(
        String outcome,
        String reference,
        Integer productConfigVersion,
        JsonNode ruleResults) {
}
