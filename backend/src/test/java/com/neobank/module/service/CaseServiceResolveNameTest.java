package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.integrations.orchestrator.OrchestratorUnavailableException;
import com.neobank.module.model.Decision;
import com.neobank.module.model.VerificationRecord;
import com.neobank.module.repository.VerificationRecordRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * Unit tests for {@link CaseService#search} — local name search via fullName.
 *
 * <p>Verifies that name queries are resolved directly from the local DB
 * (no orchestrator calls) using the {@code searchByIdOrName} repository method.</p>
 */
class CaseServiceResolveNameTest {

    private VerificationRecordRepository repo;
    private OrchestratorClient orchestrator;
    private CaseService service;

    @BeforeEach
    void setUp() {
        repo = mock(VerificationRecordRepository.class);
        orchestrator = mock(OrchestratorClient.class);
        service = new CaseService(repo, orchestrator);
    }

    @Test
    void emptyQueryReturnsAllRecords() {
        VerificationRecord rec = new VerificationRecord(
                "SIM-01", Decision.ACCEPTED, "ok", null, null, "Maria Chen");
        when(repo.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(rec)));

        Map<String, Object> result = service.search("", 10);

        assertThat(result.get("cases")).asList().hasSize(1);
        assertThat(result.get("more")).isEqualTo(false);
    }

    @Test
    void nameQuerySearchesLocalDb() {
        VerificationRecord rec = new VerificationRecord(
                "SIM-01", Decision.ACCEPTED, "ok", null, null, "Maria Chen");
        when(repo.searchByIdOrName(eq("Maria"), any(Pageable.class)))
                .thenReturn(List.of(rec));

        Map<String, Object> result = service.search("Maria", 10);

        assertThat(result.get("cases")).asList().hasSize(1);
        verify(repo).searchByIdOrName(eq("Maria"), any(Pageable.class));
    }

    @Test
    void returnsEmptyWhenNoMatchInDb() {
        when(repo.searchByIdOrName(any(), any(Pageable.class))).thenReturn(List.of());

        Map<String, Object> result = service.search("unknown", 10);

        assertThat(result.get("cases")).asList().isEmpty();
        assertThat(result.get("more")).isEqualTo(false);
    }

    @Test
    void moreFlagSetWhenResultsExceedLimit() {
        List<VerificationRecord> eleven = java.util.stream.IntStream.rangeClosed(1, 11)
                .mapToObj(i -> new VerificationRecord(
                        "SIM-" + i, Decision.ACCEPTED, "ok", null, null, "Maria Chen"))
                .toList();
        when(repo.searchByIdOrName(eq("Maria"), any(Pageable.class))).thenReturn(eleven);

        Map<String, Object> result = service.search("Maria", 10);

        assertThat(result.get("cases")).asList().hasSize(10);
        assertThat(result.get("more")).isEqualTo(true);
    }

    @Test
    void applicantPayloadIsMappedFromOrchestratorApplication() {
        Application app = new Application(
                "app-1234",
                "MOBILE_APP",
                "2026-07-25T09:14:00Z",
                new Application.Applicant(
                        "Maria Nowak",
                        "1996-04-11",
                        null,
                        null,
                        null,
                        "PL",
                        null,
                        null,
                        null,
                        null,
                        null),
                null,
                null,
                null,
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                null,
                new Application.Consents(true, null, null));
        when(orchestrator.getApplication("app-1234")).thenReturn(app);

        var result = service.findApplicant("app-1234");

        assertThat(result.fullName()).isEqualTo("Maria Nowak");
        assertThat(result.dateOfBirth()).isEqualTo("1996-04-11");
        assertThat(result.product().productCode()).isEqualTo("CREDIT_CARD_REWARDS");
        assertThat(result.product().requestedCreditLimit()).isEqualTo(3000);
        assertThat(result.channel()).isEqualTo("MOBILE_APP");
        assertThat(result.countryOfResidence()).isEqualTo("PL");
        assertThat(result.consents().termsAccepted()).isTrue();
    }

    @Test
    void orchestratorUnavailabilityIsPropagated() {
        when(orchestrator.getApplication("app-1234"))
                .thenThrow(new OrchestratorUnavailableException(
                        "Orchestrator is unreachable. Please retry.",
                        new RuntimeException("connection refused")));

        assertThatThrownBy(() -> service.findApplicant("app-1234"))
                .isInstanceOf(OrchestratorUnavailableException.class)
                .hasMessageContaining("Orchestrator is unreachable");
    }
}

