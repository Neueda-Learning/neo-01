package com.neobank.module.controller;

import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.service.ApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC02 read endpoint: review one case that was already decided and stored.
 */
@RestController
@RequestMapping("/cases")
public class CaseController {

    private final ApplicationService applications;

    public CaseController(ApplicationService applications) {
        this.applications = applications;
    }

    @GetMapping("/{applicationId}")
    public CaseDetailView getCase(@PathVariable String applicationId) {
        return applications.findCase(applicationId);
    }
}
