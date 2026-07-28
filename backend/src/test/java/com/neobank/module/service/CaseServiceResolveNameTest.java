package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.model.Decision;
import com.neobank.module.model.VerificationRecord;
import com.neobank.module.repository.VerificationRecordRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

/**
 * Unit tests for {@link CaseService#search} — local name search via fullName.
 *
 * <p>Verifies that name queries are resolved directly from the local DB
 * (no orchestrator calls) using the {@code searchByIdOrName} repository method.</p>
 */
class CaseServiceResolveNameTest {

    private VerificationRecordRepository repo;
    private CaseService service;

    @BeforeEach
    void setUp() {
        repo = mock(VerificationRecordRepository.class);
        service = new CaseService(repo);
    }

    @Test
    void emptyQueryReturnsEmptyWithoutHittingDb() {
        Map<String, Object> result = service.search("", 10);

        assertThat(result.get("cases")).asList().isEmpty();
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
}

