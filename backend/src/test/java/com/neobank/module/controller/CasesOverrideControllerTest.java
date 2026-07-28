package com.neobank.module.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.dto.OverrideCaseRequest;
import com.neobank.module.model.Decision;
import com.neobank.module.service.CaseService;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * UC-05: Override Case — controller tests.
 */
@WebMvcTest(CasesController.class)
class CasesOverrideControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CaseService caseService;

    @Test
    void overrideCaseSuccessfully() throws Exception {
        OverrideCaseRequest request = new OverrideCaseRequest("PASSED", "DOB typo — applicant is 19", "b.dimovski");
        Map<String, Object> response = Map.of(
                "applicationId", "app-1235",
                "outcome", "PASSED",
                "reference", "ver-000123"
        );

        when(caseService.override("app-1235", request)).thenReturn(response);

        mvc.perform(post("/cases/app-1235/override")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value("app-1235"))
                .andExpect(jsonPath("$.outcome").value("PASSED"))
                .andExpect(jsonPath("$.reference").value("ver-000123"));

        verify(caseService).override(eq("app-1235"), any(OverrideCaseRequest.class));
    }

    @Test
    void overrideCaseMissingReasonReturns400() throws Exception {
        OverrideCaseRequest request = new OverrideCaseRequest("PASSED", "", "b.dimovski");

        mvc.perform(post("/cases/app-1235/override")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overrideCaseMissingOperatorReturns400() throws Exception {
        OverrideCaseRequest request = new OverrideCaseRequest("PASSED", "DOB typo", "");

        mvc.perform(post("/cases/app-1235/override")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overrideCaseInvalidOutcomeReturns400() throws Exception {
        OverrideCaseRequest request = new OverrideCaseRequest("INVALID", "DOB typo", "b.dimovski");

        mvc.perform(post("/cases/app-1235/override")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overrideCaseUnknownApplicationIdReturns404() throws Exception {
        OverrideCaseRequest request = new OverrideCaseRequest("PASSED", "DOB typo", "b.dimovski");

        when(caseService.override(eq("missing-id"), any(OverrideCaseRequest.class)))
                .thenThrow(new NoSuchElementException("Unknown applicationId: missing-id"));

        mvc.perform(post("/cases/missing-id/override")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Unknown applicationId: missing-id"));
    }
}
