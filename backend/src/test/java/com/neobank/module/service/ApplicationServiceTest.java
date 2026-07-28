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
 * UC-00 acceptance criteria (unit level 鈥?no Spring, no DB, no HTTP).
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
                new Application.Applicant("Maria Nowak", "1996-04-11", "maria.nowak@example.com",
                        "+447700900111", null, null,
                        null, null,
                        new Application.Address("42 Hanbury St", null, "London", "E1 5JP", "GB"),
                        null, null),
                null,
                new Application.Employment("PERMANENT", "NeoBank Ltd", 24),
                null,
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                null, new Application.Consents(true, true, false));
        return new ApplicationRequest(id, "corr-1", "process-application", application);
    }

    /** AC-2: row exists before 202; AC-6: decide starts after commit. */
    @Test
    void insertsInProgressThenDecidesToAccepted() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_REWARDS", 3, 18, 500, 15000, true, "WEB,MOBILE_APP", null, Instant.now())));

        service.processApplicationAsync(request("SIM-01"));

        // Phase 1 (accept) 鈫?IN_PROGRESS; Phase 2 (decide) 鈫?PASSED
        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getOutcome()).isEqualTo("IN_PROGRESS");
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("PASSED");
        assertThat(saved.getAllValues().get(0).getApplicationId()).isEqualTo("SIM-01");
        assertThat(saved.getAllValues().get(0).getFullName()).isEqualTo("Maria Nowak");

        verify(orchestrator).applicationStatusUpdate("SIM-01", Decision.PASSED,
                "VER_ALL_CHECKS_PASSED");
    }

    /** AC-4: duplicate IN_PROGRESS 鈥?acknowledged, not re-queued. */
    @Test
    void idempotentForDuplicateInProgressApplication() {
        when(verificationRecords.findById("SIM-DUP")).thenReturn(Optional.of(
                new VerificationRecord("SIM-DUP", Decision.IN_PROGRESS, "pending", null, null, "Maria Nowak")));

        service.processApplicationAsync(request("SIM-DUP"));

        verify(verificationRecords, never()).save(any());
        verifyNoMoreInteractions(orchestrator);
    }

    /** AC-4: duplicate already-decided 鈥?callback replayed with stored outcome. */
    @Test
    void replaysCallbackForAlreadyDecidedApplication() {
        when(verificationRecords.findById("SIM-DEC")).thenReturn(Optional.of(
                new VerificationRecord("SIM-DEC", Decision.PASSED, "previous decision", null, null, "Maria Nowak")));

        service.processApplicationAsync(request("SIM-DEC"));

        verify(verificationRecords, never()).save(any());
        verify(orchestrator).applicationStatusUpdate("SIM-DEC", Decision.PASSED, "previous decision");
    }

    /**
     * Guard: a failure in the decide phase is still reported rather than timing out
     * the journey.
     */
    @Test
    void aFailureInTheDecidePhaseIsStillReportedAsReferred() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_REWARDS", 3, 18, 500, 15000, true, "WEB,MOBILE_APP", null, Instant.now())));

        // First save (IN_PROGRESS accept phase) succeeds; second (decide phase) throws.
        when(verificationRecords.save(any(VerificationRecord.class)))
                .thenAnswer(call -> call.getArgument(0))
                .thenThrow(new IllegalStateException("database on fire"));

        service.processApplicationAsync(request("SIM-03"));

        ArgumentCaptor<String> comment = ArgumentCaptor.forClass(String.class);
        verify(orchestrator).applicationStatusUpdate(eq("SIM-03"), eq(Decision.REVIEW),
                comment.capture());
        assertThat(comment.getValue()).contains("database on fire");
        verifyNoMoreInteractions(orchestrator);
    }

    @Test
    void theBoardShowsWhatWasStored() {
        when(verificationRecords.findAllByOrderByCreatedAtDesc())
                .thenReturn(java.util.List.of(new VerificationRecord(
                        "SIM-01", Decision.PASSED, "hello world from processApplication",
                        null, null, "Maria Nowak")));

        assertThat(service.findAll())
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.applicationId()).isEqualTo("SIM-01");
                    assertThat(view.outcome()).isEqualTo("PASSED");
                    assertThat(view.fullName()).isEqualTo("Maria Nowak");
                });
    }

    @Test
    void uc02ReturnsStoredCaseDetailsAndResolvedProductConfigVersion() {
        VerificationRecord row = new VerificationRecord(
                "SIM-CASE-1",
                Decision.PASSED,
                "decision complete",
                42L,
                "[{\"ruleName\":\"age\",\"passed\":true,\"reasonCodes\":[\"VER_ALL_CHECKS_PASSED\"]}]", null);
        when(verificationRecords.findById("SIM-CASE-1")).thenReturn(Optional.of(row));
        when(productConfigs.findById(42L)).thenReturn(Optional.of(new ProductConfig(
                "CREDIT_CARD_REWARDS", 3, 18, 500, 15000, true, "WEB,MOBILE_APP", null, Instant.now())));

        var detail = service.findCase("SIM-CASE-1");

        assertThat(detail.outcome()).isEqualTo("PASSED");
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
                        "CREDIT_CARD_REWARDS", 3, 18, 500, 15000, true, "WEB,MOBILE_APP", null, Instant.now())));

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
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("FAILED");
        assertThat(saved.getAllValues().get(1).getRuleResults()).contains("VER_AGE_BELOW_MINIMUM");
    }

    @Test
    void exactMinimumAgeIsAcceptedWhenOtherRulesPass() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_REWARDS", 3, 18, 500, 15000, true, "WEB,MOBILE_APP", null, Instant.now())));

        Application app = new Application(
                "SIM-REVIEW-WINS", "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Boundary User", LocalDate.now().minusYears(18).toString(),
                        "boundary.user@example.com", "+447700900300", "GB", "GB", java.util.List.of("GB"),
                        "RENTING", new Application.Address("1 Boundary Street", null, "Leeds", "LS1 2AB", "GB"),
                        24, 0),
                new Application.IdentityDocument("PASSPORT", "GB1234567", "GB", "2032-01-01"),
                new Application.Employment("PERMANENT", "Boundary Ltd", 36),
                new Application.Finances(35000, 900, 100),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(true, null), new Application.Consents(true, true, false));
        ApplicationRequest request = new ApplicationRequest("SIM-REVIEW-WINS", "corr-3",
                "process-application", app);

        service.processApplicationAsync(request);

        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("PASSED");
    }

    @Test
    void requestedLimitAtMaximumIsAccepted() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_REWARDS", 3, 18, 500, 10000, true, "WEB,MOBILE_APP", null, Instant.now())));

        Application app = new Application(
                "SIM-LIMIT-EDGE", "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Priya Raman", "1988-06-03",
                        "priya.raman@example.com", "+447700900203", "IN", "GB", java.util.List.of("GB"),
                        "OWNER", new Application.Address("12 Dale Street", null, "Manchester", "M1 4BT", "GB"),
                        72, 0),
                new Application.IdentityDocument("PASSPORT", "IN5540982", "IN", "2030-11-02"),
                new Application.Employment("PERMANENT", "Northgate Logistics", 84),
                new Application.Finances(60000, 1200, 300),
                new Application.Product("CREDIT_CARD_REWARDS", 10000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));
        ApplicationRequest request = new ApplicationRequest("SIM-LIMIT-EDGE", "corr-5",
                "process-application", app);

        service.processApplicationAsync(request);

        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("PASSED");
    }

    @Test
    void requestedLimitAboveMaximumIsRejected() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_REWARDS", 3, 18, 500, 10000, true, "WEB,MOBILE_APP", null, Instant.now())));

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
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("FAILED");
        assertThat(saved.getAllValues().get(1).getRuleResults()).contains("VER_LIMIT_ABOVE_MAXIMUM");
    }

    @Test
    void missingApplicantAndEmploymentFieldsAreRejected() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_REWARDS", 3, 18, 500, 10000, true, "WEB,MOBILE_APP", null, Instant.now())));

        Application app = new Application(
                "SIM-08-LIKE", "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Marcus Bell", "1990-08-17",
                        null, null, "GB", "GB", java.util.List.of("GB"), "RENTING",
                        new Application.Address("9 Duke Street", null, "Liverpool", null, "GB"),
                        20, 0),
                new Application.IdentityDocument("PASSPORT", "GB4471902", "GB", "2031-07-09"),
                new Application.Employment("PERMANENT", null, 24),
                new Application.Finances(34000, 1000, 180),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));
        ApplicationRequest request = new ApplicationRequest("SIM-08-LIKE", "corr-8",
                "process-application", app);

        service.processApplicationAsync(request);

        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("FAILED");
        assertThat(saved.getAllValues().get(1).getRuleResults())
                .contains("VER_MISSING_FIELD:applicant.email")
                .contains("VER_MISSING_FIELD:applicant.mobile")
                .contains("VER_MISSING_FIELD:applicant.currentAddress.postcode")
                .contains("VER_MISSING_FIELD:employment.employerName");
    }

    @Test
    void unsupportedTaxResidencyIsRejected() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_REWARDS", 3, 18, 500, 10000, true, "WEB,MOBILE_APP", null, Instant.now())));

        Application app = new Application(
                "SIM-11-LIKE", "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Rafael Santos", "1991-10-12",
                        "rafael.santos@example.com", "+447700900208", "BR", "GB",
                        java.util.List.of("BR"), "RENTING",
                        new Application.Address("18 Temple Way", null, "Bristol", "BS2 0FZ", "GB"),
                        18, 0),
                new Application.IdentityDocument("PASSPORT", "BR6640281", "BR", "2030-06-30"),
                new Application.Employment("PERMANENT", "Harbour Analytics", 22),
                new Application.Finances(34000, 1000, 180),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(new ApplicationRequest("SIM-11-LIKE", "corr-11",
                "process-application", app));

        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("FAILED");
        assertThat(saved.getAllValues().get(1).getRuleResults()).contains("VER_TAX_RESIDENCY_NOT_SUPPORTED");
    }

    @Test
    void expiredIdentityDocumentIsRejected() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_REWARDS", 3, 18, 500, 10000, true, "WEB,MOBILE_APP", null, Instant.now())));

        Application app = new Application(
                "SIM-13-LIKE", "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Henrik Larsen", "1987-02-08",
                        "henrik.larsen@example.com", "+447700900209", "DK", "GB",
                        java.util.List.of("GB"), "RENTING",
                        new Application.Address("6 Grey Street", null, "Newcastle", "NE1 4ST", "GB"),
                        55, 0),
                new Application.IdentityDocument("PASSPORT", "DK1180552", "DK", "2025-11-30"),
                new Application.Employment("PERMANENT", "Tyne Renewables", 70),
                new Application.Finances(34000, 1000, 180),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(new ApplicationRequest("SIM-13-LIKE", "corr-13",
                "process-application", app));

        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("FAILED");
        assertThat(saved.getAllValues().get(1).getRuleResults()).contains("VER_ID_DOCUMENT_EXPIRED");
    }

    @Test
    void identityDocumentIdMustMatchIssuingCountryPrefix() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_REWARDS", 3, 18, 500, 10000, true, "WEB,MOBILE_APP", null, Instant.now())));

        Application app = new Application(
                "SIM-14-LIKE", "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Yusuf Demir", "1992-05-23",
                        "yusuf.demir@example.com", "+447700900210", "TR", "GB",
                        java.util.List.of("GB"), "RENTING",
                        new Application.Address("44 Broad Street", null, "Birmingham", "B1 1TF", "GB"),
                        34, 0),
                new Application.IdentityDocument("PASSPORT", "ZZ0000000", "TR", "2031-09-15"),
                new Application.Employment("PERMANENT", "Midlands Freight", 41),
                new Application.Finances(34000, 1000, 180),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(new ApplicationRequest("SIM-14-LIKE", "corr-14",
                "process-application", app));

        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("FAILED");
        assertThat(saved.getAllValues().get(1).getRuleResults())
                .contains("VER_INVALID_FIELD:identityDocument.documentId");
    }

    @Test
    void exactNameMatchIsRejected() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_REWARDS", 3, 18, 500, 10000, true, "WEB,MOBILE_APP", null, Instant.now())));

        Application app = new Application(
                "SIM-15-LIKE", "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Viktor Petrov", "1975-05-14",
                        "viktor.petrov@example.com", "+447700900211", "RU", "GB",
                        java.util.List.of("GB"), "RENTING",
                        new Application.Address("27 Curzon Street", null, "London", "W1J 7NT", "GB"),
                        8, 0),
                new Application.IdentityDocument("PASSPORT", "RU9930118", "RU", "2029-03-27"),
                new Application.Employment("SELF_EMPLOYED", "Baltic Trade Partners", 96),
                new Application.Finances(88000, 2200, 400),
                new Application.Product("CREDIT_CARD_REWARDS", 9000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(new ApplicationRequest("SIM-15-LIKE", "corr-15",
                "process-application", app));

        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("FAILED");
        assertThat(saved.getAllValues().get(1).getRuleResults()).contains("VER_NAME_EXACT_MATCH");
    }

    @Test
    void partialNameMatchIsReferred() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_REWARDS", 3, 18, 500, 10000, true, "WEB,MOBILE_APP", null, Instant.now())));

        Application app = new Application(
                "SIM-16-LIKE", "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Viktoria Petrova", "1982-01-09",
                        "viktoria.petrova@example.com", "+447700900212", "BG", "GB",
                        java.util.List.of("GB"), "RENTING",
                        new Application.Address("15 Thomas Street", null, "Manchester", "M4 1HN", "GB"),
                        62, 0),
                new Application.IdentityDocument("PASSPORT", "BG4402117", "BG", "2031-10-05"),
                new Application.Employment("PERMANENT", "Pennine Foods", 88),
                new Application.Finances(34000, 1000, 180),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(new ApplicationRequest("SIM-16-LIKE", "corr-16",
                "process-application", app));

        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("REVIEW");
        assertThat(saved.getAllValues().get(1).getRuleResults()).contains("VER_NAME_PARTIAL_MATCH");
    }

    @Test
    void highRiskCountryIsReferred() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_REWARDS", 3, 18, 500, 10000, true, "WEB,MOBILE_APP", null, Instant.now())));

        Application app = new Application(
                "SIM-17-LIKE", "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Reza Karimi", "1984-07-30",
                        "reza.karimi@example.com", "+447700900213", "IR", "GB",
                        java.util.List.of("GB"), "RENTING",
                        new Application.Address("10 Belvoir Street", null, "Leicester", "LE1 6TE", "GB"),
                        15, 0),
                new Application.IdentityDocument("PASSPORT", "IR7761209", "IR", "2030-02-11"),
                new Application.Employment("PERMANENT", "Midlands Freight", 29),
                new Application.Finances(34000, 1000, 180),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(new ApplicationRequest("SIM-17-LIKE", "corr-17",
                "process-application", app));

        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("REVIEW");
        assertThat(saved.getAllValues().get(1).getRuleResults()).contains("VER_HIGH_RISK_COUNTRY");
    }

    @Test
    void affordabilityDtiAtBoundaryIsAccepted() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_STANDARD"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_STANDARD", 1, 18, 500, 5000, true, "WEB,MOBILE_APP", null, Instant.now())));

        Application app = new Application(
                "SIM-19-LIKE", "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Daniel Fischer", "1989-03-23",
                        "daniel.fischer@example.com", "+447700900215", "DE", "GB",
                        java.util.List.of("GB"), "RENTING",
                        new Application.Address("21 Norfolk Row", null, "Sheffield", "S1 4RG", "GB"),
                        39, 0),
                new Application.IdentityDocument("PASSPORT", "DE2290471", "DE", "2031-01-16"),
                new Application.Employment("PERMANENT", "Steel City Media", 65),
                new Application.Finances(30000, 1000, 100),
                new Application.Product("CREDIT_CARD_STANDARD", 3000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(new ApplicationRequest("SIM-19-LIKE", "corr-19",
                "process-application", app));

        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("PASSED");
    }

    @Test
    void affordabilityDtiAboveBoundaryIsRejected() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_STANDARD"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_STANDARD", 1, 18, 500, 5000, true, "WEB,MOBILE_APP", null, Instant.now())));

        Application app = new Application(
                "SIM-20-LIKE", "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Boundary Fail", "1989-03-23",
                        "boundary.fail@example.com", "+447700900299", "DE", "GB",
                        java.util.List.of("GB"), "RENTING",
                        new Application.Address("1 Test Street", null, "Leeds", "LS1 1AA", "GB"),
                        12, 0),
                new Application.IdentityDocument("PASSPORT", "DE9999999", "DE", "2031-01-16"),
                new Application.Employment("PERMANENT", "Example Co", 24),
                new Application.Finances(30000, 1000, 150),
                new Application.Product("CREDIT_CARD_STANDARD", 3000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(new ApplicationRequest("SIM-20-LIKE", "corr-20",
                "process-application", app));

        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("FAILED");
        assertThat(saved.getAllValues().get(1).getRuleResults()).contains("VER_AFFORDABILITY_DTI_TOO_HIGH");
    }

    @Test
    void alternateDeliveryWithoutAddressIsRejected() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_REWARDS", 3, 18, 500, 10000, true, "WEB,MOBILE_APP", null, Instant.now())));

        Application app = new Application(
                "SIM-22-LIKE", "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Ruth Kelly", "1993-09-06",
                        "ruth.kelly@example.com", "+447700900218", "IE", "GB",
                        java.util.List.of("GB"), "RENTING",
                        new Application.Address("12 Donegall Square", null, "Belfast", "BT1 5GS", "GB"),
                        51, 0),
                new Application.IdentityDocument("PASSPORT", "IE9930277", "IE", "2031-04-12"),
                new Application.Employment("PERMANENT", "Lagan Health", 77),
                new Application.Finances(34000, 1000, 180),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(false, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(new ApplicationRequest("SIM-22-LIKE", "corr-22",
                "process-application", app));

        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("FAILED");
        assertThat(saved.getAllValues().get(1).getRuleResults()).contains("VER_MISSING_FIELD:delivery.address");
    }

    @Test
    void alternateDeliveryWithAddressIsReferred() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_REWARDS", 3, 18, 500, 10000, true, "WEB,MOBILE_APP", null, Instant.now())));

        Application app = new Application(
                "SIM-21-LIKE", "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Harriet Collins", "1990-05-08",
                        "harriet.collins@example.com", "+447700900217", "GB", "GB",
                        java.util.List.of("GB"), "RENTING",
                        new Application.Address("30 Albion Street", null, "Leeds", "LS1 6HX", "GB"),
                        44, 0),
                new Application.IdentityDocument("PASSPORT", "GB6600271", "GB", "2032-03-19"),
                new Application.Employment("PERMANENT", "Northpoint Services", 72),
                new Application.Finances(42000, 1100, 200),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(false,
                        new Application.Address("Unit 4, Mill Court", null, "Leeds", "LS9 8AB", "GB")),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(new ApplicationRequest("SIM-21-LIKE", "corr-21",
                "process-application", app));

        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("REVIEW");
        assertThat(saved.getAllValues().get(1).getRuleResults())
                .contains("VER_DELIVERY_ALTERNATE_ADDRESS_REVIEW");
    }

    @Test
    void annualIncomeBelowMinimumIsRejected() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_REWARDS", 3, 18, 500, 10000, true, "WEB,MOBILE_APP", null, Instant.now())));

        Application app = new Application(
                "SIM-18-LIKE", "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Chloe Adams", "1997-11-19",
                        "chloe.adams@example.com", "+447700900214", "GB", "GB",
                        java.util.List.of("GB"), "RENTING",
                        new Application.Address("7 Fletcher Gate", null, "Nottingham", "NG1 5FS", "GB"),
                        11, 0),
                new Application.IdentityDocument("PASSPORT", "GB5518830", "GB", "2032-08-04"),
                new Application.Employment("CONTRACT", "Trent Retail Group", 9),
                new Application.Finances(18000, 500, 50),
                new Application.Product("CREDIT_CARD_REWARDS", 1500),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(new ApplicationRequest("SIM-18-LIKE", "corr-18",
                "process-application", app));

        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("FAILED");
        assertThat(saved.getAllValues().get(1).getRuleResults()).contains("VER_INCOME_BELOW_MINIMUM");
    }

    @Test
    void studentProductHasNoMinimumIncomeRequirement() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_STUDENT"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_STUDENT", 1, 18, 500, 3000, true, "WEB,MOBILE_APP", null, Instant.now())));

        Application app = new Application(
                "SIM-03-LIKE", "MOBILE_APP", "2026-07-28T09:14:00Z",
                new Application.Applicant("Amara Osei", LocalDate.now().minusYears(18).toString(),
                        "amara.osei@example.com", "+447700900201", "GB", "GB",
                        java.util.List.of("GB"), "LIVING_WITH_FAMILY",
                        new Application.Address("17 Blenheim Terrace", null, "Leeds", "LS2 9JT", "GB"),
                        120, 0),
                new Application.IdentityDocument("PASSPORT", "GB9004411", "GB", "2033-05-19"),
                new Application.Employment("STUDENT", "University of Leeds", 0),
                new Application.Finances(1, 0, 0),
                new Application.Product("CREDIT_CARD_STUDENT", 750),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(new ApplicationRequest("SIM-03-LIKE", "corr-03",
                "process-application", app));

        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("PASSED");
    }

    @Test
    void studentCardWithStudentStatusPasses() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_STUDENT"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_STUDENT", 2, 18, 500, 3000, true, "WEB,MOBILE_APP", "STUDENT", Instant.now())));

        Application app = new Application(
                "SIM-UC08-01", "WEB", "2026-07-28T10:00:00Z",
                new Application.Applicant("Emma Thompson", "2005-09-15",
                        "emma.thompson@example.com", "+447700900300", "GB", "GB",
                        java.util.List.of("GB"), "RENTING",
                        new Application.Address("10 University Avenue", null, "Cambridge", "CB2 1TN", "GB"),
                        19, 0),
                new Application.IdentityDocument("PASSPORT", "GB0000123", "GB", "2034-12-20"),
                new Application.Employment("STUDENT", "University of Cambridge", 0),
                new Application.Finances(0, 0, 0),
                new Application.Product("CREDIT_CARD_STUDENT", 2000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(new ApplicationRequest("SIM-UC08-01", "corr-uc08-01",
                "process-application", app));

        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("PASSED");
        assertThat(saved.getAllValues().get(1).getRuleResults()).contains("employmentStatus")
                .doesNotContain("VER_EMPLOYMENT_STATUS_NOT_ELIGIBLE");
    }

    @Test
    void studentCardWithPermanentStatusFails() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_STUDENT"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_STUDENT", 2, 18, 500, 3000, true, "WEB,MOBILE_APP", "STUDENT", Instant.now())));

        Application app = new Application(
                "SIM-UC08-02", "WEB", "2026-07-28T10:00:00Z",
                new Application.Applicant("James Wilson", "1995-03-20",
                        "james.wilson@example.com", "+447700900301", "GB", "GB",
                        java.util.List.of("GB"), "RENTING",
                        new Application.Address("42 High Street", null, "London", "E1 6AN", "GB"),
                        31, 24),
                new Application.IdentityDocument("PASSPORT", "GB0000456", "GB", "2032-08-15"),
                new Application.Employment("PERMANENT", "Tech Solutions Ltd", 60),
                new Application.Finances(55000, 1500, 180),
                new Application.Product("CREDIT_CARD_STUDENT", 2000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(new ApplicationRequest("SIM-UC08-02", "corr-uc08-02",
                "process-application", app));

        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("FAILED");
        assertThat(saved.getAllValues().get(1).getRuleResults()).contains("VER_EMPLOYMENT_STATUS_NOT_ELIGIBLE");
    }

    @Test
    void standardCardHasNoEmploymentRestriction() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_STANDARD"))
                .thenReturn(Optional.of(new ProductConfig(
                        "CREDIT_CARD_STANDARD", 2, 18, 500, 5000, true, "WEB,MOBILE_APP", null, Instant.now())));

        Application app = new Application(
                "SIM-UC08-03", "WEB", "2026-07-28T10:00:00Z",
                new Application.Applicant("Sofia Garcia", "1998-07-10",
                        "sofia.garcia@example.com", "+447700900302", "ES", "GB",
                        java.util.List.of("GB"), "RENTING",
                        new Application.Address("5 Elm Street", null, "Manchester", "M1 1AD", "GB"),
                        26, 12),
                new Application.IdentityDocument("PASSPORT", "ES0000789", "ES", "2031-11-03"),
                new Application.Employment("SELF_EMPLOYED", "Freelance Consultant", 24),
                new Application.Finances(48000, 1200, 180),
                new Application.Product("CREDIT_CARD_STANDARD", 3500),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(new ApplicationRequest("SIM-UC08-03", "corr-uc08-03",
                "process-application", app));

        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("PASSED");
        // Employment status restriction should not affect unrestricted products
        assertThat(saved.getAllValues().get(1).getRuleResults()).doesNotContain("VER_EMPLOYMENT_STATUS_NOT_ELIGIBLE");
    }
}
