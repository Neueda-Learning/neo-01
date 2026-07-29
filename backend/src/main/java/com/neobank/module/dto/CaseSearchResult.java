package com.neobank.module.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.model.VerificationRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * One row returned by {@code GET /cases?q=...}.
 *
 * <p>Contains only data from this module's own schema. The applicant name is intentionally
 * absent — it is never stored here and the UI hydrates it live via the applicant proxy.</p>
 */
public record CaseSearchResult(
        String applicationId,
        String fullName,
        /** ISO-8601 instant — when this module received the application. */
        Instant submittedAt,
        /** IN_PROGRESS · PASSED · FAILED · REVIEW */
        String outcome,
        /** Count of failed rules in ruleResults; 0 if rules not yet run. */
        int reasonCount) {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> RULE_LIST =
            new TypeReference<>() {};

    public static CaseSearchResult of(VerificationRecord r) {
        return new CaseSearchResult(
                r.getApplicationId(),
                r.getFullName(),
                r.getCreatedAt(),
                r.getOutcome(),
                countFailedRules(r.getRuleResults()));
    }

    /**
     * Deserialises {@code ruleResults} — expected shape:
     * {@code [{"rule":"min_age","pass":true}, {"rule":"limit_range","pass":false}]}.
     *
     * @return number of entries where {@code "pass"} is {@code false}; 0 on null, blank, or
     *         malformed JSON so a broken rule store never prevents the row from rendering.
     */
    static int countFailedRules(String ruleResults) {
        if (ruleResults == null || ruleResults.isBlank()) return 0;
        try {
            List<Map<String, Object>> rules = MAPPER.readValue(ruleResults, RULE_LIST);
            return (int) rules.stream()
                    .filter(r -> Boolean.FALSE.equals(r.get("passed")))
                    .count();
        } catch (Exception e) {
            return 0;
        }
    }
}
