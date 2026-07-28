package com.neobank.module.service;

import com.neobank.module.dto.ApplicantView;
import com.neobank.module.dto.CaseSearchResult;
import com.neobank.module.dto.OverrideCaseRequest;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.Decision;
import com.neobank.module.model.OverrideLog;
import com.neobank.module.model.VerificationRecord;
import com.neobank.module.repository.OverrideLogRepository;
import com.neobank.module.repository.VerificationRecordRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Case search — UC-01.
 *
 * <p>Search is local-only: the query string is matched against both {@code application_id}
 * and {@code full_name} in a single query against this module's table — zero network calls.</p>
 *
 * <p>The board is <b>empty by default</b> — an absent or blank {@code q} returns {@code []},
 * never a full table scan. Results are capped at {@code limit} (default 10) so the UI's
 * live hydration stays bounded. One extra row is fetched to detect a {@code more} flag
 * without a separate COUNT query.</p>
 */
@Service
public class CaseService {

    private static final int DEFAULT_LIMIT = 10;

    private final VerificationRecordRepository cases;
    private final OverrideLogRepository overrideLogs;
    private final OrchestratorClient orchestrator;

    public CaseService(VerificationRecordRepository cases, 
                       OverrideLogRepository overrideLogs,
                       OrchestratorClient orchestrator) {
        this.cases = cases;
        this.overrideLogs = overrideLogs;
        this.orchestrator = orchestrator;
    }

    /**
     * Search for cases matching {@code q} against applicationId or applicant fullName.
     * When {@code q} is absent or blank, returns all records newest-first (still capped).
     *
     * @param q     applicationId fragment or applicant name; {@code null}/{@code ""} → all records
     * @param limit maximum rows to return; capped at {@value #DEFAULT_LIMIT}
     * @return map with {@code "cases"} (list of {@link CaseSearchResult}) and {@code "more"} (boolean)
     */
    public Map<String, Object> search(String q, int limit) {
        int cap = Math.min(limit, DEFAULT_LIMIT);
        Pageable pageable = PageRequest.of(0, cap + 1, Sort.by(Sort.Direction.DESC, "createdAt"));

        if (q == null || q.isBlank()) {
            List<VerificationRecord> all = cases.findAll(pageable).getContent();
            return toResponse(all, cap);
        }

        List<VerificationRecord> hits = cases.searchByIdOrName(q, pageable);
        return toResponse(hits, cap);
    }

    /**
     * UC-03: fetch applicant fields live from orchestrator using applicationId as-is.
     */
    public ApplicantView findApplicant(String applicationId) {
        Application app = orchestrator.getApplication(applicationId);
        if (app == null) {
            throw new NoSuchElementException("Unknown applicationId: " + applicationId);
        }

        Application.Applicant applicant = app.applicant();
        Application.Product product = app.product();
        Application.Consents consents = app.consents();

        return new ApplicantView(
                applicant == null ? null : applicant.fullName(),
                applicant == null ? null : applicant.dateOfBirth(),
                new ApplicantView.ProductView(
                        product == null ? null : product.productCode(),
                        product == null ? null : product.requestedCreditLimit()),
                app.channel(),
                applicant == null ? null : applicant.countryOfResidence(),
                new ApplicantView.ConsentsView(
                        consents == null ? null : consents.termsAccepted()));
    }

    /**
     * UC-05: Override Case — manually change a case's outcome.
     *
     * <p>Updates the verification record's outcome and logs the change to the override_log.
     * Then notifies the orchestrator with the new outcome so a parked journey resumes.</p>
     *
     * @param applicationId the case to override
     * @param request       {newOutcome, reason, operator}
     * @return the updated case details
     * @throws NoSuchElementException if the applicationId does not exist
     */
    @Transactional
    public Map<String, Object> override(String applicationId, OverrideCaseRequest request) {
        // Find the existing case
        VerificationRecord record = cases.findById(applicationId)
                .orElseThrow(() -> new NoSuchElementException("Unknown applicationId: " + applicationId));

        // Capture the old outcome
        Decision oldDecision = Decision.valueOf(record.getOutcome());
        Decision newDecision = Decision.valueOf(request.newOutcome());

        // Update the verification record
        record.setOutcome(newDecision.name());
        cases.save(record);

        // Log the override
        OverrideLog log = new OverrideLog(applicationId, oldDecision, newDecision, 
                request.reason(), request.operator());
        overrideLogs.save(log);

        // Notify the orchestrator
        orchestrator.applicationStatusUpdate(applicationId, newDecision, 
                "local-manual override: " + request.reason());

        // Return updated case details
        return Map.of(
                "applicationId", applicationId,
                "outcome", record.getOutcome(),
                "reference", record.getReference()
        );
    }


    // ── internal helpers ────────────────────────────────────────────────────────

    private Map<String, Object> toResponse(List<VerificationRecord> rows, int cap) {
        boolean more = rows.size() > cap;
        List<CaseSearchResult> results = rows.stream()
                .limit(cap)
                .map(CaseSearchResult::of)
                .toList();
        return response(results, more);
    }

    private static Map<String, Object> response(List<CaseSearchResult> cases, boolean more) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cases", cases);
        body.put("more", more);
        return body;
    }
}


