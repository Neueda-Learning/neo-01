package com.neobank.module.service;

import com.neobank.module.dto.CaseSearchResult;
import com.neobank.module.model.VerificationRecord;
import com.neobank.module.repository.VerificationRecordRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

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

    public CaseService(VerificationRecordRepository cases) {
        this.cases = cases;
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


