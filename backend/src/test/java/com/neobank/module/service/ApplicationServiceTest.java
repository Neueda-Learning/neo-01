package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.Decision;
import com.neobank.module.model.VerificationRecord;
import com.neobank.module.repository.VerificationRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The three things the placeholder does, and the guard that keeps a failure reportable.
 *
 * <p>No Spring, no database, no HTTP — the service takes a request and calls two collaborators, so
 * the test is a handful of lines. Keep it that way as you replace the body: logic that needs a
 * running container to test is logic you will stop testing.</p>
 */
class ApplicationServiceTest {

    private VerificationRecordRepository verificationRecords;
    private OrchestratorClient orchestrator;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        verificationRecords = mock(VerificationRecordRepository.class);
        orchestrator = mock(OrchestratorClient.class);
        // Runnable::run — the work happens inline, so there is nothing to wait for.
        service = new ApplicationService(Runnable::run, verificationRecords, orchestrator);
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

    @Test
    void storesTheApplicationAndReportsItAccepted() {
        service.processApplication(request("SIM-01"));

        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords).save(saved.capture());
        assertThat(saved.getValue().getApplicationId()).isEqualTo("SIM-01");
        assertThat(saved.getValue().getOutcome()).isEqualTo("ACCEPTED");

        verify(orchestrator).applicationStatusUpdate("SIM-01", Decision.ACCEPTED,
                "hello world from processApplication");
    }

    @Test
    void theAsyncEntryPointDoesTheSameWorkThroughTheExecutor() {
        service.processApplicationAsync(request("SIM-02"));

        verify(verificationRecords).save(any(VerificationRecord.class));
        verify(orchestrator).applicationStatusUpdate(eq("SIM-02"), eq(Decision.ACCEPTED), any());
    }

    @Test
    void aFailureIsStillReportedRatherThanLeavingTheJourneyToTimeOut() {
        doThrow(new IllegalStateException("database on fire"))
                .when(verificationRecords).save(any(VerificationRecord.class));

        service.processApplication(request("SIM-03"));

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
