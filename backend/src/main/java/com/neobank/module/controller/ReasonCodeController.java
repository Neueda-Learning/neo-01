package com.neobank.module.controller;

import com.neobank.module.dto.CodeCount;
import com.neobank.module.service.ReasonCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-04 · View Failure Patterns.
 *
 * <p>Returns reason-code counts for the given inclusive date window, ranked descending. The
 * {@code kind} field on each entry distinguishes outright failures from review-flag codes so
 * the operator can tell a broken rule from a borderline application.</p>
 *
 * <p>The endpoint is idempotent — identical parameters always return the same result.</p>
 */
@RestController
@RequestMapping("/reason-codes")
@CrossOrigin(originPatterns = "*", allowCredentials = "true", maxAge = 3600)
@Tag(name = "Failure Patterns", description = "UC-04 — ranked reason-code counts over a date window")
public class ReasonCodeController {

    private final ReasonCodeService reasonCodeService;

    public ReasonCodeController(ReasonCodeService reasonCodeService) {
        this.reasonCodeService = reasonCodeService;
    }

    /**
     * Count reason code occurrences over the given inclusive date window.
     *
     * @param from start date (inclusive), ISO-8601 format {@code YYYY-MM-DD}
     * @param to   end date (inclusive), ISO-8601 format {@code YYYY-MM-DD}
     * @return list of {@link CodeCount} ranked descending; {@code []} when no data in window
     */
    @GetMapping
    @Operation(summary = "Ranked failure / review reason codes for a date window")
    public List<CodeCount> reasonCodes(
            @Parameter(description = "Start date (inclusive), YYYY-MM-DD")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "End date (inclusive), YYYY-MM-DD")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reasonCodeService.reasonCodeCounts(from, to);
    }
}
