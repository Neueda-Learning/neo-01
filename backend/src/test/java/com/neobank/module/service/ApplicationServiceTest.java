package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for ApplicationService — covers all 26 sidecar scenarios (SIM-01 to SIM-26)
 * plus UC-02 case retrieval and UC-08 employment status rules.
 *
 * <p>The executor is replaced with {@code Runnable::run} so both the accept phase
 * and the decide phase run inline. A fixed Clock (2026-07-28T00:00:00Z) is injected
 * so the age-boundary scenarios (SIM-03/SIM-04) are deterministic.</p>
 */
class ApplicationServiceTest {

    private VerificationRecordRepository verificationRecords;
    private ProductConfigRepository productConfigs;
    private OrchestratorClient orchestrator;
    private ApplicationService service;

    /** Fixed to 2026-07-28 — the anchor date the sidecar resolves dynamic dates against. */
    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDate.of(2026, 7, 28).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    /** REWARDS v6 config (limit 500–10000, min_age 18). */
    private static final ProductConfig REWARDS_V6 = new ProductConfig(
            "CREDIT_CARD_REWARDS", 6, 18, 500, 10000, true,
            "WEB,MOBILE_APP,BRANCH", null, Instant.now());

    /** STANDARD v3 config (limit 250–5000, min_age 18, includes BRANCH). */
    private static final ProductConfig STANDARD_V2 = new ProductConfig(
            "CREDIT_CARD_STANDARD", 3, 18, 250, 5000, true,
            "WEB,MOBILE_APP,BRANCH", null, Instant.now());

    /** STUDENT v2 config (limit 500–3000, min_age 18, STUDENT only). */
    private static final ProductConfig STUDENT_V2 = new ProductConfig(
            "CREDIT_CARD_STUDENT", 2, 18, 500, 3000, true,
            "WEB,MOBILE_APP", "STUDENT", Instant.now());

    @BeforeEach
    void setUp() {
        verificationRecords = mock(VerificationRecordRepository.class);
        productConfigs = mock(ProductConfigRepository.class);
        orchestrator = mock(OrchestratorClient.class);
        service = new ApplicationService(Runnable::run, verificationRecords, productConfigs,
                orchestrator, FIXED_CLOCK);
        when(verificationRecords.findById(any())).thenReturn(Optional.empty());
        when(verificationRecords.save(any(VerificationRecord.class))).thenAnswer(call -> call.getArgument(0));
    }

    // ─── Helper ───────────────────────────────────────────────────────────

    private ApplicationRequest scenario(String id, Application app) {
        return new ApplicationRequest(id, "corr-" + id, "process-application", app);
    }

    private Application.Applicant applicant(String fullName, String dob, String email,
            String mobile, String nationality, String countryOfResidence,
            List<String> taxResidencies, String residentialStatus,
            Application.Address address, Integer monthsAtAddress, Integer dependants) {
        return new Application.Applicant(fullName, dob, email, mobile, nationality,
                countryOfResidence, taxResidencies, residentialStatus, address,
                monthsAtAddress, dependants);
    }

    private Application.Address addr(String line1, String city, String postcode, String country) {
        return new Application.Address(line1, null, city, postcode, country);
    }

    private VerificationRecord decideAndGetResult() {
        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        return saved.getAllValues().get(1);
    }

    // ─── SIM-01: Happy path — rewards card ────────────────────────────────

    @Test
    void sim01_happyPathRewardsCard_passes() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(REWARDS_V6));

        Application app = new Application("SIM-01", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Maria Nowak", "1996-04-11", "maria.nowak@example.com",
                        "+447700900123", "PL", "GB", List.of("GB"), "RENTING",
                        addr("42 Hanbury Street", "London", "E1 5JP", "GB"), 14, 0),
                new Application.IdentityDocument("PASSPORT", "ZS1234567", "PL", "2031-02-28"),
                new Application.Employment("PERMANENT", "Trellis Health Ltd", 11),
                new Application.Finances(34000, 1000, 180),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-01", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("PASSED");
        assertThat(result.getReference()).isEqualTo("VER_ALL_CHECKS_PASSED");
    }

    // ─── SIM-02: Happy path — standard card ───────────────────────────────

    @Test
    void sim02_happyPathStandardCard_passes() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_STANDARD"))
                .thenReturn(Optional.of(STANDARD_V2));

        Application app = new Application("SIM-02", "WEB", "2026-07-25T09:14:00Z",
                applicant("Jonas Meyer", "1979-02-14", "jonas.meyer@example.com",
                        "+447700900456", "DE", "GB", List.of("GB"), "MORTGAGE",
                        addr("8 Wellington Road", "Leeds", "LS1 4DY", "GB"), 96, 2),
                new Application.IdentityDocument("DRIVING_LICENCE", "MEYER701794JM9AB", "GB", "2029-08-31"),
                new Application.Employment("PERMANENT", "Pennine Foods", 140),
                new Application.Finances(26000, 800, 120),
                new Application.Product("CREDIT_CARD_STANDARD", 2500),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-02", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("PASSED");
        assertThat(result.getReference()).isEqualTo("VER_ALL_CHECKS_PASSED");
    }

    // ─── SIM-03: Age boundary — exactly 18 → REVIEW (UC-02 AC-5) ─────────

    @Test
    void sim03_ageExactly18_review() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_STUDENT"))
                .thenReturn(Optional.of(STUDENT_V2));

        Application app = new Application("SIM-03", "MOBILE_APP", "2026-07-28T09:14:00Z",
                applicant("Amara Osei", "2008-07-28", "amara.osei@example.com",
                        "+447700900201", "GB", "GB", List.of("GB"), "LIVING_WITH_FAMILY",
                        addr("17 Blenheim Terrace", "Leeds", "LS2 9JT", "GB"), 120, 0),
                new Application.IdentityDocument("PASSPORT", "GB9004411", "GB", "2033-05-19"),
                new Application.Employment("STUDENT", "University of Leeds", 0),
                new Application.Finances(9000, 200, 0),
                new Application.Product("CREDIT_CARD_STUDENT", 750),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-03", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("REVIEW");
        assertThat(result.getReference()).isEqualTo("VER_AGE_EXACT_MINIMUM");
        assertThat(result.getRuleResults()).contains("VER_AGE_EXACT_MINIMUM");
    }

    // ─── SIM-04: Age boundary — one day short of 18 ───────────────────────

    @Test
    void sim04_ageOneDayShortOf18_fails() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_STUDENT"))
                .thenReturn(Optional.of(STUDENT_V2));

        Application app = new Application("SIM-04", "MOBILE_APP", "2026-07-28T09:14:00Z",
                applicant("Liam Doyle", "2008-07-29", "liam.doyle@example.com",
                        "+447700900202", "IE", "GB", List.of("GB"), "LIVING_WITH_FAMILY",
                        addr("4 Redcliffe Parade", "Bristol", "BS1 6QF", "GB"), 60, 0),
                new Application.IdentityDocument("PASSPORT", "IE7712304", "IE", "2032-03-14"),
                new Application.Employment("STUDENT", "Harbour Analytics", 0),
                new Application.Finances(9000, 200, 0),
                new Application.Product("CREDIT_CARD_STUDENT", 750),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-04", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("FAILED");
        assertThat(result.getReference()).isEqualTo("VER_AGE_BELOW_MINIMUM");
        assertThat(result.getRuleResults()).contains("VER_AGE_BELOW_MINIMUM");
    }

    // ─── SIM-05: Limit at product max → REVIEW (UC-00: exactly at max → REVIEW)

    @Test
    void sim05_limitAtProductMax_review() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(REWARDS_V6));

        Application app = new Application("SIM-05", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Priya Raman", "1988-06-03", "priya.raman@example.com",
                        "+447700900203", "IN", "GB", List.of("GB"), "OWNER",
                        addr("12 Dale Street", "Manchester", "M1 4BT", "GB"), 72, 0),
                new Application.IdentityDocument("PASSPORT", "IN5540982", "IN", "2030-11-02"),
                new Application.Employment("PERMANENT", "Northgate Logistics", 84),
                new Application.Finances(60000, 1200, 300),
                new Application.Product("CREDIT_CARD_REWARDS", 10000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-05", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("REVIEW");
        assertThat(result.getReference()).isEqualTo("VER_LIMIT_EXACT_MAXIMUM");
        assertThat(result.getRuleResults()).contains("VER_LIMIT_EXACT_MAXIMUM");
    }

    // ─── SIM-06: Limit above product max ──────────────────────────────────

    @Test
    void sim06_limitAboveProductMax_fails() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(REWARDS_V6));

        Application app = new Application("SIM-06", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Stefan Bauer", "1986-09-21", "stefan.bauer@example.com",
                        "+447700900204", "AT", "GB", List.of("GB"), "OWNER",
                        addr("3 Arundel Gate", "Sheffield", "S1 2HE", "GB"), 48, 0),
                new Application.IdentityDocument("PASSPORT", "AT3391027", "AT", "2030-04-18"),
                new Application.Employment("PERMANENT", "Steel City Media", 60),
                new Application.Finances(60000, 1200, 300),
                new Application.Product("CREDIT_CARD_REWARDS", 10500),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-06", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("FAILED");
        assertThat(result.getReference()).isEqualTo("VER_LIMIT_OUTSIDE_PRODUCT_RANGE");
        assertThat(result.getRuleResults()).contains("VER_LIMIT_OUTSIDE_PRODUCT_RANGE");
    }

    // ─── SIM-07: Terms not accepted ───────────────────────────────────────

    @Test
    void sim07_termsNotAccepted_fails() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(REWARDS_V6));

        Application app = new Application("SIM-07", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Elena Varga", "1994-12-05", "elena.varga@example.com",
                        "+447700900205", "HU", "GB", List.of("GB"), "RENTING",
                        addr("22 Bute Street", "Cardiff", "CF10 1EP", "GB"), 30, 0),
                new Application.IdentityDocument("PASSPORT", "HU8820113", "HU", "2029-12-01"),
                new Application.Employment("PERMANENT", "Severn Utilities", 36),
                new Application.Finances(34000, 1000, 180),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(true, null),
                new Application.Consents(false, true, false));

        service.processApplicationAsync(scenario("SIM-07", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("FAILED");
        assertThat(result.getReference()).isEqualTo("VER_TERMS_NOT_ACCEPTED");
        assertThat(result.getRuleResults()).contains("VER_TERMS_NOT_ACCEPTED");
    }

    // ─── SIM-08: Required fields missing ──────────────────────────────────

    @Test
    void sim08_requiredFieldsMissing_fails() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(REWARDS_V6));

        Application app = new Application("SIM-08", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Marcus Bell", "1990-08-17", null, null, "GB", "GB",
                        List.of("GB"), "RENTING",
                        new Application.Address("9 Duke Street", null, "Liverpool", null, "GB"),
                        20, 0),
                new Application.IdentityDocument("PASSPORT", "GB4471902", "GB", "2031-07-09"),
                new Application.Employment("PERMANENT", null, 24),
                new Application.Finances(34000, 1000, 180),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-08", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("FAILED");
        assertThat(result.getRuleResults())
                .contains("VER_MISSING_FIELD:applicant.email")
                .contains("VER_MISSING_FIELD:applicant.mobile")
                .contains("VER_MISSING_FIELD:applicant.currentAddress.postcode")
                .contains("VER_MISSING_FIELD:employment.employerName");
    }

    // ─── SIM-09: Field formats invalid ────────────────────────────────────

    @Test
    void sim09_fieldFormatsInvalid_fails() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(REWARDS_V6));

        Application app = new Application("SIM-09", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Ines Da Costa", "12/03/1990", "ines.dacosta(at)example.com",
                        "07700 900 999", "PRT", "gbr", List.of("GB"), "RENTING",
                        new Application.Address("5 Bothwell Street", null, "Glasgow", "g2 1du", "gbr"),
                        40, 0),
                new Application.IdentityDocument("PASSPORT", "PT2210984", "PRT", "31-12-2030"),
                new Application.Employment("PERMANENT", "Clyde Robotics", 50),
                new Application.Finances(34000, 1000, 180),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-09", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("FAILED");
        assertThat(result.getRuleResults())
                .contains("VER_INVALID_FIELD:applicant.dateOfBirth")
                .contains("VER_INVALID_FIELD:applicant.email")
                .contains("VER_INVALID_FIELD:applicant.mobile")
                .contains("VER_INVALID_FIELD:applicant.nationality")
                .contains("VER_INVALID_FIELD:applicant.countryOfResidence")
                .contains("VER_INVALID_FIELD:identityDocument.issuingCountry")
                .contains("VER_INVALID_FIELD:identityDocument.expiryDate")
                .contains("VER_INVALID_FIELD:applicant.currentAddress.country");
    }

    // ─── SIM-10: US tax residency (policy module concern) ─────────────────

    @Test
    void sim10_usTaxResidency_passesVerification() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(REWARDS_V6));

        Application app = new Application("SIM-10", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Grace Miller", "1983-04-29", "grace.miller@example.com",
                        "+447700900207", "US", "GB", List.of("GB", "US"), "RENTING",
                        addr("31 Marsham Street", "London", "SW1P 3BT", "GB"), 26, 0),
                new Application.IdentityDocument("PASSPORT", "US7719023", "US", "2032-01-22"),
                new Application.Employment("PERMANENT", "Atlantic Reinsurance", 44),
                new Application.Finances(52000, 1400, 200),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-10", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("PASSED");
        assertThat(result.getReference()).isEqualTo("VER_ALL_CHECKS_PASSED");
    }

    // ─── SIM-11: BR tax residency (policy module concern) ─────────────────

    @Test
    void sim11_brTaxResidency_passesVerification() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(REWARDS_V6));

        Application app = new Application("SIM-11", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Rafael Santos", "1991-10-12", "rafael.santos@example.com",
                        "+447700900208", "BR", "GB", List.of("BR"), "RENTING",
                        addr("18 Temple Way", "Bristol", "BS2 0FZ", "GB"), 18, 0),
                new Application.IdentityDocument("PASSPORT", "BR6640281", "BR", "2030-06-30"),
                new Application.Employment("PERMANENT", "Harbour Analytics", 22),
                new Application.Finances(34000, 1000, 180),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-11", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("PASSED");
        assertThat(result.getReference()).isEqualTo("VER_ALL_CHECKS_PASSED");
    }

    // ─── SIM-12: Existing customer second card (policy/account concern) ───

    @Test
    void sim12_existingCustomerSecondCard_passesVerification() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_STANDARD"))
                .thenReturn(Optional.of(STANDARD_V2));

        Application app = new Application("SIM-12", "BRANCH", "2026-07-25T14:02:00Z",
                applicant("Maria Nowak", "1996-04-11", "maria.nowak@example.com",
                        "+447700900123", "PL", "GB", List.of("GB"), "RENTING",
                        addr("42 Hanbury Street", "London", "E1 5JP", "GB"), 14, 0),
                new Application.IdentityDocument("PASSPORT", "ZS1234567", "PL", "2031-02-28"),
                new Application.Employment("PERMANENT", "Trellis Health Ltd", 11),
                new Application.Finances(34000, 1000, 180),
                new Application.Product("CREDIT_CARD_STANDARD", 1500),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-12", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("PASSED");
        assertThat(result.getReference()).isEqualTo("VER_ALL_CHECKS_PASSED");
    }

    // ─── SIM-13: ID document expired (KYC module concern) ─────────────────

    @Test
    void sim13_idDocumentExpired_passesVerification() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(REWARDS_V6));

        Application app = new Application("SIM-13", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Henrik Larsen", "1987-02-08", "henrik.larsen@example.com",
                        "+447700900209", "DK", "GB", List.of("GB"), "RENTING",
                        addr("6 Grey Street", "Newcastle", "NE1 4ST", "GB"), 55, 0),
                new Application.IdentityDocument("PASSPORT", "DK1180552", "DK", "2025-11-30"),
                new Application.Employment("PERMANENT", "Tyne Renewables", 70),
                new Application.Finances(34000, 1000, 180),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-13", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("PASSED");
        assertThat(result.getReference()).isEqualTo("VER_ALL_CHECKS_PASSED");
    }

    // ─── SIM-14: ID provider unavailable (KYC module concern) ─────────────

    @Test
    void sim14_idProviderUnavailable_passesVerification() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(REWARDS_V6));

        Application app = new Application("SIM-14", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Yusuf Demir", "1992-05-23", "yusuf.demir@example.com",
                        "+447700900210", "TR", "GB", List.of("GB"), "RENTING",
                        addr("44 Broad Street", "Birmingham", "B1 1TF", "GB"), 34, 0),
                new Application.IdentityDocument("PASSPORT", "ZZ0000000", "TR", "2031-09-15"),
                new Application.Employment("PERMANENT", "Midlands Freight", 41),
                new Application.Finances(34000, 1000, 180),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-14", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("PASSED");
        assertThat(result.getReference()).isEqualTo("VER_ALL_CHECKS_PASSED");
    }

    // ─── SIM-15: Sanctions exact match (screening module concern) ─────────

    @Test
    void sim15_sanctionsExactMatch_passesVerification() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(REWARDS_V6));

        Application app = new Application("SIM-15", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Viktor Petrov", "1975-05-14", "viktor.petrov@example.com",
                        "+447700900211", "RU", "GB", List.of("GB"), "RENTING",
                        addr("27 Curzon Street", "London", "W1J 7NT", "GB"), 8, 0),
                new Application.IdentityDocument("PASSPORT", "RU9930118", "RU", "2029-03-27"),
                new Application.Employment("SELF_EMPLOYED", "Baltic Trade Partners", 96),
                new Application.Finances(88000, 2200, 400),
                new Application.Product("CREDIT_CARD_REWARDS", 9000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-15", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("PASSED");
        assertThat(result.getReference()).isEqualTo("VER_ALL_CHECKS_PASSED");
    }

    // ─── SIM-16: Sanctions partial match (screening module concern) ───────

    @Test
    void sim16_sanctionsPartialMatch_passesVerification() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(REWARDS_V6));

        Application app = new Application("SIM-16", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Viktoria Petrova", "1982-01-09", "viktoria.petrova@example.com",
                        "+447700900212", "BG", "GB", List.of("GB"), "RENTING",
                        addr("15 Thomas Street", "Manchester", "M4 1HN", "GB"), 62, 0),
                new Application.IdentityDocument("PASSPORT", "BG4402117", "BG", "2031-10-05"),
                new Application.Employment("PERMANENT", "Pennine Foods", 88),
                new Application.Finances(34000, 1000, 180),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-16", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("PASSED");
        assertThat(result.getReference()).isEqualTo("VER_ALL_CHECKS_PASSED");
    }

    // ─── SIM-17: High-risk country (screening module concern) ─────────────

    @Test
    void sim17_highRiskCountry_passesVerification() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(REWARDS_V6));

        Application app = new Application("SIM-17", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Reza Karimi", "1984-07-30", "reza.karimi@example.com",
                        "+447700900213", "IR", "GB", List.of("GB"), "RENTING",
                        addr("10 Belvoir Street", "Leicester", "LE1 6TE", "GB"), 15, 0),
                new Application.IdentityDocument("PASSPORT", "IR7761209", "IR", "2030-02-11"),
                new Application.Employment("PERMANENT", "Midlands Freight", 29),
                new Application.Finances(34000, 1000, 180),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-17", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("PASSED");
        assertThat(result.getReference()).isEqualTo("VER_ALL_CHECKS_PASSED");
    }

    // ─── SIM-18: Income below min (credit module concern) ─────────────────

    @Test
    void sim18_incomeBelowMinimum_passesVerification() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(REWARDS_V6));

        Application app = new Application("SIM-18", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Chloe Adams", "1997-11-19", "chloe.adams@example.com",
                        "+447700900214", "GB", "GB", List.of("GB"), "RENTING",
                        addr("7 Fletcher Gate", "Nottingham", "NG1 5FS", "GB"), 11, 0),
                new Application.IdentityDocument("PASSPORT", "GB5518830", "GB", "2032-08-04"),
                new Application.Employment("CONTRACT", "Trent Retail Group", 9),
                new Application.Finances(18000, 500, 50),
                new Application.Product("CREDIT_CARD_REWARDS", 1500),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-18", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("PASSED");
        assertThat(result.getReference()).isEqualTo("VER_ALL_CHECKS_PASSED");
    }

    // ─── SIM-19: DTI 0.44 just inside (credit module concern) ─────────────

    @Test
    void sim19_dtiJustInside_passesVerification() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_STANDARD"))
                .thenReturn(Optional.of(STANDARD_V2));

        Application app = new Application("SIM-19", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Daniel Fischer", "1989-03-23", "daniel.fischer@example.com",
                        "+447700900215", "DE", "GB", List.of("GB"), "RENTING",
                        addr("21 Norfolk Row", "Sheffield", "S1 4RG", "GB"), 39, 0),
                new Application.IdentityDocument("PASSPORT", "DE2290471", "DE", "2031-01-16"),
                new Application.Employment("PERMANENT", "Steel City Media", 65),
                new Application.Finances(30000, 1000, 100),
                new Application.Product("CREDIT_CARD_STANDARD", 3000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-19", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("PASSED");
        assertThat(result.getReference()).isEqualTo("VER_ALL_CHECKS_PASSED");
    }

    // ─── SIM-20: DTI 0.46 just over (credit module concern) ───────────────

    @Test
    void sim20_dtiJustOver_passesVerification() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_STANDARD"))
                .thenReturn(Optional.of(STANDARD_V2));

        Application app = new Application("SIM-20", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Nadia Haddad", "1990-06-14", "nadia.haddad@example.com",
                        "+447700900216", "FR", "GB", List.of("GB"), "RENTING",
                        addr("3 Hertford Street", "Coventry", "CV1 5FB", "GB"), 27, 0),
                new Application.IdentityDocument("PASSPORT", "FR3308814", "FR", "2030-09-08"),
                new Application.Employment("PERMANENT", "Midlands Freight", 58),
                new Application.Finances(30000, 1000, 150),
                new Application.Product("CREDIT_CARD_STANDARD", 3000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-20", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("PASSED");
        assertThat(result.getReference()).isEqualTo("VER_ALL_CHECKS_PASSED");
    }

    // ─── SIM-21: Alternate delivery address (card module concern) ─────────

    @Test
    void sim21_alternateDeliveryAddress_passesVerification() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(REWARDS_V6));

        Application app = new Application("SIM-21", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Oscar Lindqvist", "1985-12-02", "oscar.lindqvist@example.com",
                        "+447700900217", "SE", "GB", List.of("GB"), "RENTING",
                        addr("88 Curtain Road", "London", "EC2A 4NE", "GB"), 4, 0),
                new Application.IdentityDocument("PASSPORT", "SE7719044", "SE", "2032-05-25"),
                new Application.Employment("PERMANENT", "Baltic Trade Partners", 33),
                new Application.Finances(47000, 1500, 0),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(false,
                        new Application.Address("Baltic Trade Partners",
                                "4th Floor, 120 Moorgate", "London", "EC2M 6UR", "GB")),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-21", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("PASSED");
        assertThat(result.getReference()).isEqualTo("VER_ALL_CHECKS_PASSED");
    }

    // ─── SIM-22: No delivery address given (card module concern) ──────────

    @Test
    void sim22_noDeliveryAddressGiven_passesVerification() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(REWARDS_V6));

        Application app = new Application("SIM-22", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Ruth Kelly", "1993-09-06", "ruth.kelly@example.com",
                        "+447700900218", "IE", "GB", List.of("GB"), "RENTING",
                        addr("12 Donegall Square", "Belfast", "BT1 5GS", "GB"), 51, 0),
                new Application.IdentityDocument("PASSPORT", "IE9930277", "IE", "2031-04-12"),
                new Application.Employment("PERMANENT", "Lagan Health", 77),
                new Application.Finances(34000, 1000, 180),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(false, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-22", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("PASSED");
        assertThat(result.getReference()).isEqualTo("VER_ALL_CHECKS_PASSED");
    }

    // ─── SIM-23: Unknown fields ignored ───────────────────────────────────

    @Test
    void sim23_unknownFieldsIgnored_passes() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(REWARDS_V6));

        Application app = new Application("SIM-23", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Ana Ruiz", "1993-06-27", "ana.ruiz@example.com",
                        "+447700900219", "ES", "GB", List.of("GB"), "RENTING",
                        addr("2 Anchor Road", "Bristol", "BS1 6QF", "GB"), 44, 0),
                new Application.IdentityDocument("PASSPORT", "ES2214760", "ES", "2030-12-19"),
                new Application.Employment("PERMANENT", "Harbour Analytics", 63),
                new Application.Finances(34000, 1000, 180),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-23", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("PASSED");
        assertThat(result.getReference()).isEqualTo("VER_ALL_CHECKS_PASSED");
    }

    // ─── SIM-24: Unknown product code ─────────────────────────────────────

    @Test
    void sim24_unknownProductCode_fails() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_PREMIUM"))
                .thenReturn(Optional.empty());

        Application app = new Application("SIM-24", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Peter Novak", "1985-01-19", "peter.novak@example.com",
                        "+447700900220", "SK", "GB", List.of("GB"), "RENTING",
                        addr("5 Churchill Way", "Cardiff", "CF10 2EH", "GB"), 88, 0),
                new Application.IdentityDocument("PASSPORT", "SK6612903", "SK", "2029-07-21"),
                new Application.Employment("PERMANENT", "Severn Utilities", 110),
                new Application.Finances(45000, 1100, 250),
                new Application.Product("CREDIT_CARD_PREMIUM", 7500),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-24", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("FAILED");
        assertThat(result.getReference()).isEqualTo("VER_INVALID_FIELD:product.productCode");
        assertThat(result.getRuleResults()).contains("VER_INVALID_FIELD:product.productCode");
    }

    // ─── SIM-25: Duplicate applicationId (idempotent) ─────────────────────

    @Test
    void sim25_duplicateApplicationId_isIdempotent() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(REWARDS_V6));
        // First request: no existing row
        when(verificationRecords.findById("SIM-01"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new VerificationRecord(
                        "SIM-01", Decision.PASSED, "VER_ALL_CHECKS_PASSED", null, null, "Maria Nowak")));

        Application app1 = new Application("SIM-01", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Maria Nowak", "1996-04-11", "maria.nowak@example.com",
                        "+447700900123", "PL", "GB", List.of("GB"), "RENTING",
                        addr("42 Hanbury Street", "London", "E1 5JP", "GB"), 14, 0),
                new Application.IdentityDocument("PASSPORT", "ZS1234567", "PL", "2031-02-28"),
                new Application.Employment("PERMANENT", "Trellis Health Ltd", 11),
                new Application.Finances(34000, 1000, 180),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-01", app1));

        // Second request: same applicationId, different limit — should be idempotent
        Application app2 = new Application("SIM-01", "MOBILE_APP", "2026-07-25T09:19:00Z",
                applicant("Maria Nowak", "1996-04-11", "maria.nowak@example.com",
                        "+447700900123", "PL", "GB", List.of("GB"), "RENTING",
                        addr("42 Hanbury Street", "London", "E1 5JP", "GB"), 14, 0),
                new Application.IdentityDocument("PASSPORT", "ZS1234567", "PL", "2031-02-28"),
                new Application.Employment("PERMANENT", "Trellis Health Ltd", 11),
                new Application.Finances(34000, 1000, 180),
                new Application.Product("CREDIT_CARD_REWARDS", 4000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-01", app2));

        // Only 2 saves (first accept + first decide); second request does not save
        verify(verificationRecords, times(2)).save(any(VerificationRecord.class));
        // Callback: first from decide phase, second from replay
        verify(orchestrator, times(2)).applicationStatusUpdate("SIM-01", Decision.PASSED,
                "VER_ALL_CHECKS_PASSED");
    }

    // ─── SIM-26: Missing applicationId (400 at envelope level) ────────────
    // Tested in ApplicationControllerTest — @NotBlank on ApplicationRequest.applicationId
    // causes a 400 before the service is called. Nothing is written to the DB.

    // ─── AC-2 / AC-6: IN_PROGRESS then decide ──────────────────────────────

    @Test
    void insertsInProgressThenDecidesToPassed() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(REWARDS_V6));

        service.processApplicationAsync(scenario("SIM-01", new Application(
                "SIM-01", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Maria Nowak", "1996-04-11", "maria.nowak@example.com",
                        "+447700900123", "PL", "GB", List.of("GB"), "RENTING",
                        addr("42 Hanbury Street", "London", "E1 5JP", "GB"), 14, 0),
                new Application.IdentityDocument("PASSPORT", "ZS1234567", "PL", "2031-02-28"),
                new Application.Employment("PERMANENT", "Trellis Health Ltd", 11),
                new Application.Finances(34000, 1000, 180),
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false))));

        ArgumentCaptor<VerificationRecord> saved = ArgumentCaptor.forClass(VerificationRecord.class);
        verify(verificationRecords, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getOutcome()).isEqualTo("IN_PROGRESS");
        assertThat(saved.getAllValues().get(1).getOutcome()).isEqualTo("PASSED");
        assertThat(saved.getAllValues().get(0).getApplicationId()).isEqualTo("SIM-01");
        assertThat(saved.getAllValues().get(0).getFullName()).isEqualTo("Maria Nowak");
    }

    // ─── AC-4: Duplicate IN_PROGRESS — acknowledged, not re-queued ────────

    @Test
    void idempotentForDuplicateInProgressApplication() {
        when(verificationRecords.findById("SIM-DUP")).thenReturn(Optional.of(
                new VerificationRecord("SIM-DUP", Decision.IN_PROGRESS, "pending", null, null, "Maria Nowak")));

        Application app = new Application("SIM-DUP", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Maria Nowak", "1996-04-11", "maria.nowak@example.com",
                        "+447700900123", "PL", "GB", List.of("GB"), "RENTING",
                        addr("42 Hanbury Street", "London", "E1 5JP", "GB"), 14, 0),
                null, null, null,
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                null, new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-DUP", app));

        verify(verificationRecords, never()).save(any());
        verifyNoMoreInteractions(orchestrator);
    }

    // ─── AC-4: Duplicate already-decided — callback replayed ──────────────

    @Test
    void replaysCallbackForAlreadyDecidedApplication() {
        when(verificationRecords.findById("SIM-DEC")).thenReturn(Optional.of(
                new VerificationRecord("SIM-DEC", Decision.PASSED, "previous decision", null, null, "Maria Nowak")));

        Application app = new Application("SIM-DEC", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Maria Nowak", "1996-04-11", "maria.nowak@example.com",
                        "+447700900123", "PL", "GB", List.of("GB"), "RENTING",
                        addr("42 Hanbury Street", "London", "E1 5JP", "GB"), 14, 0),
                null, null, null,
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                null, new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-DEC", app));

        verify(verificationRecords, never()).save(any());
        verify(orchestrator).applicationStatusUpdate("SIM-DEC", Decision.PASSED, "previous decision");
    }

    // ─── Guard: decide-phase failure is reported as REVIEW ─────────────────

    @Test
    void aFailureInTheDecidePhaseIsStillReportedAsReferred() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_REWARDS"))
                .thenReturn(Optional.of(REWARDS_V6));
        when(verificationRecords.save(any(VerificationRecord.class)))
                .thenAnswer(call -> call.getArgument(0))
                .thenThrow(new IllegalStateException("database on fire"));

        service.processApplicationAsync(scenario("SIM-03", new Application(
                "SIM-03", "MOBILE_APP", "2026-07-25T09:14:00Z",
                applicant("Test", "1990-01-01", "test@example.com",
                        "+447700900111", "GB", "GB", List.of("GB"), "RENTING",
                        addr("1 Test St", "London", "E1 1AA", "GB"), 12, 0),
                null, null, null,
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                null, new Application.Consents(true, true, false))));

        ArgumentCaptor<String> comment = ArgumentCaptor.forClass(String.class);
        verify(orchestrator).applicationStatusUpdate(
                org.mockito.ArgumentMatchers.eq("SIM-03"),
                org.mockito.ArgumentMatchers.eq(Decision.REVIEW),
                comment.capture());
        assertThat(comment.getValue()).contains("database on fire");
    }

    // ─── UC-02: case retrieval ─────────────────────────────────────────────

    @Test
    void uc02ReturnsStoredCaseDetailsAndResolvedProductConfigVersion() {
        VerificationRecord row = new VerificationRecord(
                "SIM-CASE-1", Decision.PASSED, "decision complete",
                42L, "[{\"ruleName\":\"age\",\"passed\":true,\"reasonCodes\":[\"VER_ALL_CHECKS_PASSED\"]}]",
                "Maria Nowak");
        when(verificationRecords.findById("SIM-CASE-1")).thenReturn(Optional.of(row));
        when(productConfigs.findById(42L)).thenReturn(Optional.of(REWARDS_V6));

        var detail = service.findCase("SIM-CASE-1");

        assertThat(detail.outcome()).isEqualTo("PASSED");
        assertThat(detail.reference()).isEqualTo("decision complete");
        assertThat(detail.productConfigVersion()).isEqualTo(6);
        assertThat(detail.ruleResults().isArray()).isTrue();
        assertThat(detail.ruleResults().get(0).get("ruleName").asText()).isEqualTo("age");
    }

    @Test
    void uc02UnknownCaseIdThrowsNotFound() {
        when(verificationRecords.findById("SIM-NOT-FOUND")).thenReturn(Optional.empty());
        org.junit.jupiter.api.Assertions.assertThrows(NoSuchElementException.class,
                () -> service.findCase("SIM-NOT-FOUND"));
    }

    // ─── UC-08: employment status eligibility ──────────────────────────────

    @Test
    void studentCardWithStudentStatusPasses() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_STUDENT"))
                .thenReturn(Optional.of(STUDENT_V2));

        Application app = new Application("SIM-UC08-01", "WEB", "2026-07-28T10:00:00Z",
                applicant("Emma Thompson", "2005-09-15", "emma.thompson@example.com",
                        "+447700900300", "GB", "GB", List.of("GB"), "RENTING",
                        addr("10 University Avenue", "Cambridge", "CB2 1TN", "GB"), 19, 0),
                new Application.IdentityDocument("PASSPORT", "GB0000123", "GB", "2034-12-20"),
                new Application.Employment("STUDENT", "University of Cambridge", 0),
                new Application.Finances(0, 0, 0),
                new Application.Product("CREDIT_CARD_STUDENT", 2000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-UC08-01", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("PASSED");
        assertThat(result.getRuleResults()).doesNotContain("VER_EMPLOYMENT_STATUS_NOT_ELIGIBLE");
    }

    @Test
    void studentCardWithPermanentStatusFails() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_STUDENT"))
                .thenReturn(Optional.of(STUDENT_V2));

        Application app = new Application("SIM-UC08-02", "WEB", "2026-07-28T10:00:00Z",
                applicant("James Wilson", "1995-03-20", "james.wilson@example.com",
                        "+447700900301", "GB", "GB", List.of("GB"), "RENTING",
                        addr("42 High Street", "London", "E1 6AN", "GB"), 31, 24),
                new Application.IdentityDocument("PASSPORT", "GB0000456", "GB", "2032-08-15"),
                new Application.Employment("PERMANENT", "Tech Solutions Ltd", 60),
                new Application.Finances(55000, 1500, 180),
                new Application.Product("CREDIT_CARD_STUDENT", 2000),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-UC08-02", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("FAILED");
        assertThat(result.getRuleResults()).contains("VER_EMPLOYMENT_STATUS_NOT_ELIGIBLE");
    }

    @Test
    void standardCardHasNoEmploymentRestriction() {
        when(productConfigs.findTopByProductCodeOrderByVersionDesc("CREDIT_CARD_STANDARD"))
                .thenReturn(Optional.of(STANDARD_V2));

        Application app = new Application("SIM-UC08-03", "WEB", "2026-07-28T10:00:00Z",
                applicant("Sofia Garcia", "1998-07-10", "sofia.garcia@example.com",
                        "+447700900302", "ES", "GB", List.of("GB"), "RENTING",
                        addr("5 Elm Street", "Manchester", "M1 1AD", "GB"), 26, 12),
                new Application.IdentityDocument("PASSPORT", "ES0000789", "ES", "2031-11-03"),
                new Application.Employment("SELF_EMPLOYED", "Freelance Consultant", 24),
                new Application.Finances(48000, 1200, 180),
                new Application.Product("CREDIT_CARD_STANDARD", 3500),
                new Application.Delivery(true, null),
                new Application.Consents(true, true, false));

        service.processApplicationAsync(scenario("SIM-UC08-03", app));

        VerificationRecord result = decideAndGetResult();
        assertThat(result.getOutcome()).isEqualTo("PASSED");
        assertThat(result.getRuleResults()).doesNotContain("VER_EMPLOYMENT_STATUS_NOT_ELIGIBLE");
    }

    // ─── Board view ───────────────────────────────────────────────────────

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
}
