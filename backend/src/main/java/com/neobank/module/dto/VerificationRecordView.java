package com.neobank.module.dto;

import com.neobank.module.model.VerificationRecord;
import java.time.Instant;

/**
 * What {@code GET /api/v1/applications} returns — the operator's view of each decision.
 *
 * <p>Contains only the fields that belong to this module's own data. No applicant personal
 * data is ever stored, so none can be returned here.</p>
 */
public record VerificationRecordView(
        String applicationId,
        String outcome,
        String reference,
        String ruleResults,
        Instant createdAt) {

    public static VerificationRecordView of(VerificationRecord row) {
        return new VerificationRecordView(
                row.getApplicationId(),
                row.getOutcome(),
                row.getReference(),
                row.getRuleResults(),
                row.getCreatedAt());
    }
}
