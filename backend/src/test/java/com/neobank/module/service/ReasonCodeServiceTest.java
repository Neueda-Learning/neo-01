package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.neobank.module.dto.CodeCount;
import com.neobank.module.model.Decision;
import com.neobank.module.model.VerificationRecord;
import com.neobank.module.repository.VerificationRecordRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * UC-04 acceptance criteria — unit level, no Spring, no DB, no HTTP.
 *
 * kind is derived from outcome: REFERRED -> "review", REJECTED -> "failure".
 *
 * Fixture design (mirrors 007-seed-failure-patterns-fixture.yaml):
 *   record-001 REJECTED  VER_MISSING_FIELD x3
 *   record-002 REJECTED  VER_MISSING_FIELD x2
 *   record-003 REFERRED  VER_AGE_EXACT_MINIMUM x2
 * Window 2026-07-01->07-14: VER_MISSING_FIELD=5 (failure), VER_AGE_EXACT_MINIMUM=2 (review).
 */
class ReasonCodeServiceTest {

    private VerificationRecordRepository repository;
    private ReasonCodeService service;

    private static final String RULE_RESULTS_001 =
            "[{\"rule\":\"wellFormedness\",\"pass\":false,\"reasonCode\":\"VER_MISSING_FIELD\"},"
            + "{\"rule\":\"wellFormedness\",\"pass\":false,\"reasonCode\":\"VER_MISSING_FIELD\"},"
            + "{\"rule\":\"wellFormedness\",\"pass\":false,\"reasonCode\":\"VER_MISSING_FIELD\"},"
            + "{\"rule\":\"age\",\"pass\":true},{\"rule\":\"productActive\",\"pass\":true}]";

    private static final String RULE_RESULTS_002 =
            "[{\"rule\":\"wellFormedness\",\"pass\":false,\"reasonCode\":\"VER_MISSING_FIELD\"},"
            + "{\"rule\":\"wellFormedness\",\"pass\":false,\"reasonCode\":\"VER_MISSING_FIELD\"},"
            + "{\"rule\":\"age\",\"pass\":true},{\"rule\":\"productActive\",\"pass\":true}]";

    private static final String RULE_RESULTS_003 =
            "[{\"rule\":\"wellFormedness\",\"pass\":true},"
            + "{\"rule\":\"age\",\"pass\":false,\"reasonCode\":\"VER_AGE_EXACT_MINIMUM\"},"
            + "{\"rule\":\"age\",\"pass\":false,\"reasonCode\":\"VER_AGE_EXACT_MINIMUM\"},"
            + "{\"rule\":\"productActive\",\"pass\":true}]";

    private static VerificationRecord rec(String id, Decision outcome, String ruleResults) {
        return new VerificationRecord(id, outcome, "ref", null, ruleResults, "Test User");
    }

    @BeforeEach
    void setUp() {
        repository = mock(VerificationRecordRepository.class);
        service = new ReasonCodeService(repository);
    }

    /** AC1 + AC3 checkpoint: top code must be VER_MISSING_FIELD with count 5. */
    @Test
    void seedWindowTopCodeIsVER_MISSING_FIELD_with5() {
        when(repository.findRecordsInWindow(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(
                        rec("fp-001", Decision.FAILED, RULE_RESULTS_001),
                        rec("fp-002", Decision.FAILED, RULE_RESULTS_002),
                        rec("fp-003", Decision.REVIEW, RULE_RESULTS_003)));

        List<CodeCount> result = service.reasonCodeCounts(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 14));

        assertThat(result.get(0).code()).isEqualTo("VER_MISSING_FIELD");
        assertThat(result.get(0).count()).isEqualTo(5L);
    }

    /** AC1: results are ranked descending by count. */
    @Test
    void resultsAreRankedDescending() {
        when(repository.findRecordsInWindow(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(
                        rec("fp-001", Decision.FAILED, RULE_RESULTS_001),
                        rec("fp-002", Decision.FAILED, RULE_RESULTS_002),
                        rec("fp-003", Decision.REVIEW, RULE_RESULTS_003)));

        List<CodeCount> result = service.reasonCodeCounts(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 14));

        assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result.get(0).count()).isGreaterThanOrEqualTo(result.get(1).count());
    }

    /** AC2: kind derived from outcome — REFERRED -> review, REJECTED -> failure. */
    @Test
    void kindIsDerivedFromOutcome() {
        when(repository.findRecordsInWindow(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(
                        rec("fp-001", Decision.FAILED, RULE_RESULTS_001),
                        rec("fp-003", Decision.REVIEW, RULE_RESULTS_003)));

        List<CodeCount> result = service.reasonCodeCounts(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 14));

        assertThat(result).anySatisfy(c -> {
            assertThat(c.code()).isEqualTo("VER_MISSING_FIELD");
            assertThat(c.kind()).isEqualTo("failure");
        });
        assertThat(result).anySatisfy(c -> {
            assertThat(c.code()).isEqualTo("VER_AGE_EXACT_MINIMUM");
            assertThat(c.kind()).isEqualTo("review");
        });
    }

    /** AC4: empty window returns empty list, never throws. */
    @Test
    void emptyWindowReturnsEmptyList() {
        when(repository.findRecordsInWindow(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        assertThat(service.reasonCodeCounts(
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2))).isEmpty();
    }

    /** AC4: records with no reason codes produce empty list. */
    @Test
    void windowWithNoReasonCodesReturnsEmptyList() {
        String noReasonCodes = "[{\"rule\":\"age\",\"pass\":true}]";
        when(repository.findRecordsInWindow(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(rec("fp-x", Decision.PASSED, noReasonCodes)));

        assertThat(service.reasonCodeCounts(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 14))).isEmpty();
    }

    /** Malformed JSON is silently skipped. */
    @Test
    void malformedRuleResultsAreSkippedGracefully() {
        when(repository.findRecordsInWindow(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(
                        rec("fp-bad", Decision.FAILED, "not-json"),
                        rec("fp-003", Decision.REVIEW, RULE_RESULTS_003)));

        List<CodeCount> result = service.reasonCodeCounts(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 14));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("VER_AGE_EXACT_MINIMUM");
    }

    // extractCodes unit tests

    @Test
    void extractCodesReturnsAllReasonCodeStrings() {
        List<String> codes = ReasonCodeService.extractCodes(RULE_RESULTS_001);
        assertThat(codes).containsExactly(
                "VER_MISSING_FIELD", "VER_MISSING_FIELD", "VER_MISSING_FIELD");
    }

    @Test
    void extractCodesReturnsEmptyListOnMalformedJson() {
        assertThat(ReasonCodeService.extractCodes("not valid json")).isEmpty();
    }
}
