package com.neobank.module.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.dto.ProductVersionCreated;
import com.neobank.module.dto.ProductVersionView;
import com.neobank.module.service.ProductConfigService;
import java.util.List;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductConfigController.class)
class ProductConfigControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ProductConfigService productConfigService;

    @Test
    void createsProductVersionReturns201WithVersion() throws Exception {
        when(productConfigService.createVersion(any())).thenReturn(new ProductVersionCreated(2));

        mvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "CREDIT_CARD_REWARDS",
                                  "minAge": 18,
                                  "limitMin": 1000,
                                  "limitMax": 15000,
                                  "active": true,
                                  "channels": ["WEB", "MOBILE_APP"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void getVersionsReturnsAllVersionsForProduct() throws Exception {
        when(productConfigService.getVersions("CREDIT_CARD_STANDARD")).thenReturn(List.of(
                new ProductVersionView("CREDIT_CARD_STANDARD", 1, 18, 500, 5000, true,
                        List.of("WEB", "MOBILE_APP"), Instant.now(), true)
        ));

        mvc.perform(get("/products/CREDIT_CARD_STANDARD/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$[0].current").value(true));
    }

    @Test
    void getVersionsReturns404ForUnknownProduct() throws Exception {
        when(productConfigService.getVersions("UNKNOWN")).thenThrow(
                new IllegalArgumentException("Unknown productCode: UNKNOWN"));

        mvc.perform(get("/products/UNKNOWN/versions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Unknown")));
    }

    @Test
    void getAllProductCodesReturnsAllCodes() throws Exception {
        when(productConfigService.getAllProductCodes()).thenReturn(List.of(
                "CREDIT_CARD_STANDARD",
                "CREDIT_CARD_REWARDS",
                "CREDIT_CARD_STUDENT"
        ));

        mvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isNotEmpty());
    }

    @Test
    void rejectsInvalidPayloadWith400() throws Exception {
        when(productConfigService.createVersion(any())).thenThrow(
                new IllegalArgumentException("limitMin must be less than limitMax"));

        mvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "CREDIT_CARD_REWARDS",
                                  "minAge": 18,
                                  "limitMin": 5000,
                                  "limitMax": 5000,
                                  "active": true,
                                  "channels": ["WEB"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("limitMin must be less than")));
    }

    @Test
    void rejectsMissingRequiredFields() throws Exception {
        mvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "minAge": 18,
                                  "limitMin": 1000,
                                  "limitMax": 10000,
                                  "active": true,
                                  "channels": ["WEB"]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}