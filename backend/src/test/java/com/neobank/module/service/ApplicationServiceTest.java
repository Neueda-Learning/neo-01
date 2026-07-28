package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.Decision;
import com.neobank.module.model.ProductConfig;
import com.neobank.module.model.VerificationRecord;
import com.neobank.module.repository.ProductConfigRepository;
import com.neobank.module.repository.VerificationRecordRepository;
import java.time.LocalDate;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * UC-00 acceptance criteria (unit level — no Spring, no DB, no HTTP).
 *
 * <p>
 * The executor is replaced with {@code Runnable::run} so both the accept phase
 * and the
 * decide phase run inline and are immediately observable.
 * </p>
 */
class ApplicationServiceTest {

    private VerificationRecordRepository verificationRecords;
    private ProductConfigRepository productConfigs;
    private OrchestratorClient orchestrator;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        verificationRecords = mock(VerificationRecordRepository.class);
        productConfigs = mock(ProductConfigRepository.class);
        orchestrator = mock(OrchestratorClient.class);
        service = new ApplicationService(Runnable::run, verificationRecords, productConfigs, orchestrator);
        // Default: no existing row.
        when(verificationRecords.findById(any())).thenReturn(Optional.empty());
        when(verificationRecords.save(any(VerificationRecord.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static ApplicationRequest request(String id) {
        Application application = new Application(
                id, "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Maria Nowak", "1996-04-11", null, null, null, null,
                        null, null, null, null, null),
                null, null, null,
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                null, new Application.Consents(true, true, false));
        return new ApplicationRequest(id, "corr-1", "process-application", application);
    }

    /** AC-2: row exists before 202; AC-6: decide starts after commit. */
    @Test
    void insertsInProgressThenDecidesToAccepted() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_REWARDS", 3, 18, 500, 15000, true, "WEB,MOBILE_APP", Instant.now())));

        service.processApplicationAsync(request("SIM-01"));

        // Phase 1 (accept) → IN_PROGRESS; Phase 2 (decide) → ACCEPTED
        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getOutcome()).isEqualTo("IN_PROGRESS");
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("ACCEPTED");
        assertThat(saved.getAllValues().get(0).getApplicationId()).isEqualTo("SIM-01");

        verify(orchestrator).applicationStatusUpdate("SIM-01", Decision.ACCEPTED,
                "VER_ALL_CHECKS_PASSED");
    }

    /** AC-4: duplicate IN_PROGRESS — acknowledged, not re-queued. */
    @Test
    void idempotentForDuplicateInProgressApplication() {
        when(verificationRecords.findById("SIM-DUP")).thenReturn(Optional.of(
                new VerificationRecord("SIM-DUP", Decision.IN_PROGRESS, "pending", null, null)));

        service.processApplicationAsync(request("SIM-DUP"));

        verify(verificationRecords, never()).save(any());
        verifyNoMoreInteractions(orchestrator);
    }

    /** AC-4: duplicate already-decided — callback replayed with stored outcome. */
    @Test
    void replaysCallbackForAlreadyDecidedApplication() {
        when(verificationRecords.findById("SIM-DEC")).thenReturn(Optional.of(
                new VerificationRecord("SIM-DEC", Decision.ACCEPTED, "previous decision", null, null)));

        service.processApplicationAsync(request("SIM-DEC"));

        verify(verificationRecords, never()).save(any());
        verify(orchestrator).applicationStatusUpdate("SIM-DEC", Decision.ACCEPTED, "previous decision");
    }

    /**
     * Guard: a failure in the decide phase is still reported rather than timing out
     * the journey.
     */
    @Test
    void aFailureInTheDecidePhaseIsStillReportedAsReferred() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_REWARDS", 3, 18, 500, 15000, true, "WEB,MOBILE_APP", Instant.now())));

        // First save (IN_PROGRESS accept phase) succeeds; second (decide phase) throws.
        when(verificationRecords.save(any(VerificationRecord.class)))
                .thenAnswer(call -> call.getArgument(0))
                .thenThrow(new IllegalStateException("database on fire"));

        service.processApplicationAsync(request("SIM-03"));

        ArgumentCaptor<String> comment = ArgumentCaptor.forClass(String.class);
        verify(orchestrator).applicationStatusUpdate(eq("SIM-03"), eq(Decision.REFERRED),
                comment.capture());
        assertThat(comment.getValue()).contains("database on fire");
        verifyNoMoreInteractions(orchestrator);
    }

    @Test
    void theBoardShowsWhatWasStored() {
        when(verificationRecords.findAllByOrderByCreatedAtDesc())
                .thenReturn(java.util.List.of(new VerificationRecord(
                        "SIM-01", Decision.ACCEPTED, "VER_ALL_CHECKS_PASSED",
                        null, null)));

        assertThat(service.findAll())
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.applicationId()).isEqualTo("SIM-01");
                    assertThat(view.outcome()).isEqualTo("ACCEPTED");
                });
    }

    @Test
    void uc02ReturnsStoredCaseDetailsAndResolvedProductConfigVersion() {
        VerificationRecord row = new VerificationRecord(
                "SIM-CASE-1",
                Decision.ACCEPTED,
                "decision complete",
                42L,
                "[{\"ruleName\":\"age\",\"passed\":true,\"reasonCodes\":[\"VER_ALL_CHECKS_PASSED\"]}]");
        when(verificationRecords.findById("SIM-CASE-1")).thenReturn(Optional.of(row));
        when(productConfigs.findById(42L)).thenReturn(Optional.of(new ProductConfig(
                "CREDIT_CARD_REWARDS", 3, 18, 500, 15000, true, "WEB,MOBILE_APP", Instant.now())));

        var detail = service.findCase("SIM-CASE-1");

        assertThat(detail.outcome()).isEqualTo("ACCEPTED");
        assertThat(detail.reference()).isEqualTo("decision complete");
        assertThat(detail.productConfigVersion()).isEqualTo(3);
        assertThat(detail.ruleResults().isArray()).isTrue();
        assertThat(detail.ruleResults().get(0).get("ruleName").asText()).isEqualTo("age");
    }

    @Test
    void uc02UnknownCaseIdThrowsNotFound() {
        when(verificationRecords.findById("SIM-NOT-FOUND")).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(NoSuchElementException.class,
                () -> service.findCase("SIM-NOT-FOUND"));
    }

    @Test
    void belowMinimumAgeIsRejected() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_REWARDS", 3, 18, 500, 15000, true, "WEB,MOBILE_APP", Instant.now())));

        Application app = new Application(
                "SIM-AGE-FAIL", "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Priya Raman", LocalDate.now().minusYears(17).toString(),
                        null, null, null, null, null, null, null, null, null),
                null, null, null,
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                null, new Application.Consents(true, true, false));
        ApplicationRequest request = new ApplicationRequest("SIM-AGE-FAIL", "corr-2",
                "process-application", app);

        service.processApplicationAsync(request);

        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("REJECTED");
        assertThat(saved.getAllValues().get(1).getRuleResults()).contains("VER_AGE_BELOW_MINIMUM");
    }

    @Test
    void exactMinimumAgeTriggersReferredEvenIfAnotherRuleFails() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_REWARDS", 3, 18, 500, 15000, false, "WEB", Instant.now())));

        Application app = new Application(
                "SIM-REVIEW-WINS", "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Boundary User", LocalDate.now().minusYears(18).toString(),
                        null, null, null, null, null, null, null, null, null),
                null, null, null,
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                null, new Application.Consents(true, true, false));
        ApplicationRequest request = new ApplicationRequest("SIM-REVIEW-WINS", "corr-3",
                "process-application", app);

        service.processApplicationAsync(request);

        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("REFERRED");
        assertThat(saved.getAllValues().get(1).getRuleResults()).contains("VER_AGE_EXACT_MINIMUM");
    }

    @Test
    void requestedLimitAboveMaximumIsRejected() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_REWARDS", 3, 18, 500, 10000, true, "WEB,MOBILE_APP", Instant.now())));

        Application app = new Application(
                "SIM-LIMIT-HIGH", "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Stefan Bauer", "1986-09-21",
                        null, null, null, null, null, null, null, null, null),
                null, null, null,
                new Application.Product("CREDIT_CARD_REWARDS", 10500),
                null, new Application.Consents(true, true, false));
        ApplicationRequest request = new ApplicationRequest("SIM-LIMIT-HIGH", "corr-4",
                "process-application", app);

        service.processApplicationAsync(request);

        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("REJECTED");
        assertThat(saved.getAllValues().get(1).getRuleResults()).contains("VER_LIMIT_ABOVE_MAXIMUM");
    }
}
