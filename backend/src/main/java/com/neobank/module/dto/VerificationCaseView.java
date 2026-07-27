package com.neobank.module.dto;

import com.neobank.module.model.VerificationCase;
import java.time.Instant;

/**
 * What {@code GET /api/v1/applications} returns — this module's own view of its decisions.
 *
 * <p>Includes the fields an operator needs to understand the outcome at a glance: who applied,
 * for what product, what the decision was, and why. Add further fields here as the UI grows.</p>
 */
public record VerificationCaseView(
        String applicationId,
        String status,
        String decisionReason,
        String applicantName,
        String productCode,
        Integer requestedLimit,
        Boolean termsAccepted,
        String documentType,
        String documentExpiry,
        Instant createdAt) {

    public static VerificationCaseView of(VerificationCase row) {
        return new VerificationCaseView(
                row.getApplicationId(),
                row.getStatus(),
                row.getDecisionReason(),
                row.getApplicantName(),
                row.getProductCode(),
                row.getRequestedLimit(),
                row.getTermsAccepted(),
                row.getDocumentType(),
                row.getDocumentExpiry(),
                row.getCreatedAt());
    }
}
