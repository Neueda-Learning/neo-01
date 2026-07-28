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
import com.neobank.module.model.VerificationRecord;
import com.neobank.module.repository.VerificationRecordRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * UC-00 acceptance criteria (unit level — no Spring, no DB, no HTTP).
 *
 * <p>The executor is replaced with {@code Runnable::run} so both the accept phase and the
 * decide phase run inline and are immediately observable.</p>
 */
class ApplicationServiceTest {

    private VerificationRecordRepository verificationRecords;
    private OrchestratorClient orchestrator;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        verificationRecords = mock(VerificationRecordRepository.class);
        orchestrator = mock(OrchestratorClient.class);
        service = new ApplicationService(Runnable::run, verificationRecords, orchestrator);
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
                null, null);
        return new ApplicationRequest(id, "corr-1", "process-application", application);
    }

    /** AC-2: row exists before 202; AC-6: decide starts after commit. */
    @Test
    void insertsInProgressThenDecidesToAccepted() {
        service.processApplicationAsync(request("SIM-01"));

        // Phase 1 (accept) → IN_PROGRESS; Phase 2 (decide) → ACCEPTED
        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getOutcome()).isEqualTo("IN_PROGRESS");
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("ACCEPTED");
        assertThat(saved.getAllValues().get(0).getApplicationId()).isEqualTo("SIM-01");

        verify(orchestrator).applicationStatusUpdate("SIM-01", Decision.ACCEPTED,
                "hello world from processApplication");
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

    /** Guard: a failure in the decide phase is still reported rather than timing out the journey. */
    @Test
    void aFailureInTheDecidePhaseIsStillReportedAsReferred() {
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
                        "SIM-01", Decision.ACCEPTED, "hello world from processApplication",
                        null, null)));

        assertThat(service.findAll())
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.applicationId()).isEqualTo("SIM-01");
                    assertThat(view.outcome()).isEqualTo("ACCEPTED");
                });
    }
}
