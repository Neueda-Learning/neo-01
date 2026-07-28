package com.neobank.module.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.dto.CodeCount;
import com.neobank.module.model.VerificationRecord;
import com.neobank.module.repository.VerificationRecordRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-04 · View Failure Patterns.
 *
 * <p>Aggregates reason codes from the embedded {@code ruleResults} JSON over a date window.
 * Counting is per reason entry, not per case — one case can contribute multiple entries for
 * the same code. The {@code kind} ("failure" or "review") is derived from the record's
 * {@code outcome}: {@code REVIEW} → review, anything else → failure.</p>
 *
 * <p>No rows are written — this service is strictly read-only.</p>
 */
@Service
public class ReasonCodeService {

    /** Composite key: same code in different-outcome records is tracked separately. */
    private record CodeKey(String code, String kind) {}

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> RULE_LIST =
            new TypeReference<>() {};

    private final VerificationRecordRepository repository;

    public ReasonCodeService(VerificationRecordRepository repository) {
        this.repository = repository;
    }

    /**
     * Count reason code occurrences within the inclusive date window [{@code from}, {@code to}].
     *
     * <p>Results are ranked descending by count. An empty window returns an empty list,
     * never throws (AC4). The {@code kind} field is derived from the record's {@code outcome}:
     * REVIEW → "review", anything else → "failure".</p>
     *
     * @param from start date (inclusive), treated as midnight UTC
     * @param to   end date (inclusive), treated as end-of-day UTC (exclusive next day)
     * @return ranked list of {@link CodeCount}; empty when no records fall in the window
     */
    @Transactional(readOnly = true)
    public List<CodeCount> reasonCodeCounts(LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<VerificationRecord> records = repository.findRecordsInWindow(start, end);

        Map<CodeKey, Long> counts = new LinkedHashMap<>();
        for (VerificationRecord record : records) {
            String kind = "REVIEW".equals(record.getOutcome()) ? "review" : "failure";
            for (String code : extractCodes(record.getRuleResults())) {
                counts.merge(new CodeKey(code, kind), 1L, Long::sum);
            }
        }

        return counts.entrySet().stream()
                .sorted(Map.Entry.<CodeKey, Long>comparingByValue().reversed())
                .map(e -> new CodeCount(e.getKey().code(), e.getValue(), e.getKey().kind()))
                .toList();
    }

    /**
     * Parse one {@code ruleResults} JSON string and return all reason codes found.
     * Supports both formats: {@code reasonCodes} array (current) and {@code reasonCode} scalar (legacy).
     * Malformed JSON is silently skipped so a corrupt row never prevents the endpoint from returning.
     *
     * @return list of reason code strings; empty on null, blank, or malformed input
     */
    static List<String> extractCodes(String ruleResults) {
        if (ruleResults == null || ruleResults.isBlank()) return List.of();
        try {
            List<Map<String, Object>> rules = MAPPER.readValue(ruleResults, RULE_LIST);
            List<String> codes = new java.util.ArrayList<>();
            
            for (Map<String, Object> rule : rules) {
                // Current format: reasonCodes array
                Object reasonCodesObj = rule.get("reasonCodes");
                if (reasonCodesObj instanceof List) {
                    ((List<?>) reasonCodesObj).stream()
                            .filter(c -> c != null && !c.toString().isBlank())
                            .map(Object::toString)
                            .forEach(codes::add);
                } else if (reasonCodesObj != null && !reasonCodesObj.toString().isBlank()) {
                    // Single value fallback
                    codes.add(reasonCodesObj.toString());
                }
                
                // Legacy format: reasonCode scalar
                Object reasonCodeObj = rule.get("reasonCode");
                if (reasonCodeObj != null && !reasonCodeObj.toString().isBlank()) {
                    codes.add(reasonCodeObj.toString());
                }
            }
            
            return codes;
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
