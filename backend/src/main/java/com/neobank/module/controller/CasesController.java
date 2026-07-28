package com.neobank.module.controller;

import com.neobank.module.dto.ApplicantView;
import com.neobank.module.dto.OverrideCaseRequest;
import com.neobank.module.service.CaseService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-01 · Search Cases.
 *
 * <p>The board starts empty — no {@code q} means no rows, not a full table scan. Rows are
 * capped at 10 so the UI's live hydration never makes more than 10 orchestrator calls per
 * render. A {@code more} flag tells the UI to refine the search rather than paginate.</p>
 *
 * <p>The {@code /cases/{id}/applicant} proxy (UC-03) lives here too — it fetches the
 * applicant block live from the orchestrator so applicant data never touches this module's
 * schema.</p>
 */
@RestController
@RequestMapping("/cases")
public class CasesController {

    private final CaseService caseService;

    public CasesController(CaseService caseService) {
        this.caseService = caseService;
    }

    /**
     * Search for verification cases.
     *
     * <p>Pass {@code q} to search by applicationId (local) or applicant name (via orchestrator).
     * Omit {@code q} and the response is {@code {"cases":[],"more":false}} — the board is empty
     * until the user types.</p>
     *
     * @param q     applicationId fragment or applicant name; absent → empty result
     * @param limit maximum rows; defaults to and is capped at 10
     */
    @GetMapping
    public Map<String, Object> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "10") int limit) {
        return caseService.search(q, limit);
    }

    @GetMapping("/{id}/applicant")
    public ApplicantView applicant(@PathVariable("id") String applicationId) {
        return caseService.findApplicant(applicationId);
    }

    /**
     * UC-05: Override Case — manually change a verification outcome.
     *
     * <p>Updates the outcome and logs the override for audit purposes. Validates that
     * {@code reason} and {@code operator} are provided, and that {@code newOutcome}
     * is one of PASSED, FAILED, or REVIEW. Notifies the orchestrator of the change.</p>
     *
     * @param applicationId the id of the case to override
     * @param request       {newOutcome, reason, operator} — all mandatory
     * @return the updated case
     */
    @PostMapping("/{id}/override")
    public Map<String, Object> overrideCase(
            @PathVariable("id") String applicationId,
            @Valid @RequestBody OverrideCaseRequest request) {
        return caseService.override(applicationId, request);
    }
}
