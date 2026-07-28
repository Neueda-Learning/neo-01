package com.neobank.module.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.dto.CaseSearchResult;
import com.neobank.module.service.CaseService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * UC-01 web-slice: verifies the HTTP surface of {@link CasesController}.
 *
 * <p>{@link CaseService} is mocked — search logic and orchestrator calls are tested in
 * {@code CaseServiceTest}.</p>
 */
@WebMvcTest(CasesController.class)
class CasesControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CaseService caseService;

    @Test
    void emptyByDefaultWhenNoQueryIsGiven() throws Exception {
        when(caseService.search(isNull(), anyInt()))
                .thenReturn(Map.of("cases", List.of(), "more", false));

        mvc.perform(get("/cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases", hasSize(0)))
                .andExpect(jsonPath("$.more", is(false)));
    }

    @Test
    void returnsMatchingCasesForAnIdQuery() throws Exception {
        CaseSearchResult row = new CaseSearchResult("SIM-01", null, Instant.parse("2026-07-21T21:40:00Z"),
                "PASSED", 1);
        when(caseService.search(eq("SIM-01"), anyInt()))
                .thenReturn(Map.of("cases", List.of(row), "more", false));

        mvc.perform(get("/cases").param("q", "SIM-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases", hasSize(1)))
                .andExpect(jsonPath("$.cases[0].applicationId", is("SIM-01")))
                .andExpect(jsonPath("$.cases[0].outcome", is("PASSED")))
                .andExpect(jsonPath("$.cases[0].reasonCount", is(1)))
                .andExpect(jsonPath("$.more", is(false)));
    }

    @Test
    void flagsMoreWhenResultsExceedLimit() throws Exception {
        when(caseService.search(eq("SIM"), anyInt()))
                .thenReturn(Map.of("cases", List.of(), "more", true));

        mvc.perform(get("/cases").param("q", "SIM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.more", is(true)));
    }

    @Test
    void emptyListWhenNoMatchesFound() throws Exception {
        when(caseService.search(eq("nobody"), anyInt()))
                .thenReturn(Map.of("cases", List.of(), "more", false));

        mvc.perform(get("/cases").param("q", "nobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases", hasSize(0)));
    }

    @Test
    void nameLookupPassesThroughToService() throws Exception {
        CaseSearchResult row = new CaseSearchResult("app-1234", "Maria Chen", Instant.now(), "PASSED", 0);
        when(caseService.search(eq("Maria"), anyInt()))
                .thenReturn(Map.of("cases", List.of(row), "more", false));

        mvc.perform(get("/cases").param("q", "Maria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases[0].outcome", is("PASSED")));
    }
}
