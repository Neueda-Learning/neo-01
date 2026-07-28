package com.neobank.module.dto;

/**
 * One entry in the {@code GET /reason-codes} response — UC-04.
 *
 * @param code  the VER_ reason code, e.g. {@code VER_MISSING_FIELD}
 * @param count occurrences within the requested window (per entry, not per case)
 * @param kind  {@code "failure"} or {@code "review"} — derived from the code, never stored
 */
public record CodeCount(String code, long count, String kind) {}
