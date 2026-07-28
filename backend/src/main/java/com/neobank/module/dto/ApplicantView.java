package com.neobank.module.dto;

/**
 * UC-03 sidebar payload. Applicant data is fetched live from the orchestrator and never persisted.
 */
public record ApplicantView(
        String fullName,
        String dateOfBirth,
        ProductView product,
        String channel,
        String countryOfResidence,
        ConsentsView consents) {

    public record ProductView(
            String productCode,
            Integer requestedCreditLimit) {
    }

    public record ConsentsView(
            Boolean termsAccepted) {
    }
}