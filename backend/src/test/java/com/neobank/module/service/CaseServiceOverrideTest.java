package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.dto.OverrideCaseRequest;
import com.neobank.module.model.Decision;
import com.neobank.module.model.OverrideLog;
import com.neobank.module.model.VerificationRecord;
import com.neobank.module.repository.OverrideLogRepository;
import com.neobank.module.repository.VerificationRecordRepository;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * UC-05: Override Case — service tests.
 */
@ExtendWith(MockitoExtension.class)
class CaseServiceOverrideTest {

    @Mock
    private VerificationRecordRepository verificationRecords;

    @Mock
    private OverrideLogRepository overrideLogs;

    @Mock
    private OrchestratorClient orchestrator;

    @InjectMocks
    private CaseService caseService;

    @Test
    void overrideCaseSuccessfully() {
        // Setup
        String applicationId = "app-1235";
        VerificationRecord existingRecord = new VerificationRecord(
                applicationId, Decision.FAILED, "ver-000123", 3L, "[]", "John Doe");
        OverrideCaseRequest request = new OverrideCaseRequest("PASSED", "DOB typo — applicant is 19", "b.dimovski");

        when(verificationRecords.findById(applicationId)).thenReturn(Optional.of(existingRecord));

        // Execute
        Map<String, Object> result = caseService.override(applicationId, request);

        // Verify result
        assertThat(result).containsEntry("applicationId", applicationId)
                .containsEntry("outcome", "PASSED")
                .containsEntry("reference", "ver-000123");

        // Verify record was updated
        verify(verificationRecords).save(any(VerificationRecord.class));

        // Verify override log was saved
        ArgumentCaptor<OverrideLog> logCaptor = ArgumentCaptor.forClass(OverrideLog.class);
        verify(overrideLogs).save(logCaptor.capture());
        OverrideLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getApplicationId()).isEqualTo(applicationId);
        assertThat(savedLog.getOldOutcome()).isEqualTo("FAILED");
        assertThat(savedLog.getNewOutcome()).isEqualTo("PASSED");
        assertThat(savedLog.getReason()).isEqualTo("DOB typo — applicant is 19");
        assertThat(savedLog.getOperator()).isEqualTo("b.dimovski");

        // Verify orchestrator was notified
        verify(orchestrator).applicationStatusUpdate(eq(applicationId), eq(Decision.PASSED), any(String.class));
    }

    @Test
    void overrideCaseFromREVIEWToFAILED() {
        // Setup
        String applicationId = "app-5678";
        VerificationRecord existingRecord = new VerificationRecord(
                applicationId, Decision.REVIEW, "ver-000456", 2L, "[]", "Jane Smith");
        OverrideCaseRequest request = new OverrideCaseRequest("FAILED", "Fraud detected", "a.johnson");

        when(verificationRecords.findById(applicationId)).thenReturn(Optional.of(existingRecord));

        // Execute
        Map<String, Object> result = caseService.override(applicationId, request);

        // Verify
        assertThat(result).containsEntry("outcome", "FAILED");
        verify(overrideLogs).save(any(OverrideLog.class));
        verify(orchestrator).applicationStatusUpdate(eq(applicationId), eq(Decision.FAILED), any(String.class));
    }

    @Test
    void overrideCaseWithUnknownApplicationIdThrows() {
        // Setup
        String unknownId = "missing-id";
        OverrideCaseRequest request = new OverrideCaseRequest("PASSED", "Test reason", "operator");

        when(verificationRecords.findById(unknownId)).thenReturn(Optional.empty());

        // Execute & Verify
        assertThatThrownBy(() -> caseService.override(unknownId, request))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Unknown applicationId: missing-id");
    }
}
