package com.neobank.module.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.service.ApplicationService;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CaseController.class)
class CaseControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ApplicationService applications;

    @Test
    void returnsStoredCaseDetails() throws Exception {
        var ruleResults = objectMapper.readTree(
                "[{\"ruleName\":\"age\",\"passed\":false,\"reasonCodes\":[\"VER_AGE_BELOW_MINIMUM\"]}]");
        when(applications.findCase("app-1235"))
                .thenReturn(new CaseDetailView("FAILED", "ver-000123", 3, ruleResults));

        mvc.perform(get("/cases/app-1235"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("FAILED"))
                .andExpect(jsonPath("$.reference").value("ver-000123"))
                .andExpect(jsonPath("$.productConfigVersion").value(3))
                .andExpect(jsonPath("$.ruleResults[0].ruleName").value("age"));
    }

    @Test
    void unknownApplicationIdReturnsNotFoundJson() throws Exception {
        when(applications.findCase("missing-id"))
                .thenThrow(new NoSuchElementException("Unknown applicationId: missing-id"));

        mvc.perform(get("/cases/missing-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Unknown applicationId: missing-id"));
    }
}
