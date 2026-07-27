package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One verification outcome for one application received from the orchestrator.
 *
 * <p>Captures the fields this module inspected when it made its decision — enough for an operator
 * to understand why an application was accepted, rejected or referred without having to re-fetch
 * the original form.</p>
 */
@Entity
@Table(name = "verification_case")
public class VerificationCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The id from the request envelope — the one the orchestrator matches on. */
    @Column(name = "application_id", nullable = false, length = 64)
    private String applicationId;

    /** ACCEPTED · REJECTED · REFERRED, stored as text so the database is readable. */
    @Column(nullable = false, length = 32)
    private String status;

    /** Human-readable reason suitable for a bank employee to relay to a customer. */
    @Column(name = "decision_reason", nullable = false, length = 512)
    private String decisionReason;

    @Column(name = "applicant_name", length = 255)
    private String applicantName;

    @Column(name = "product_code", length = 64)
    private String productCode;

    /** Requested credit limit in whole GBP. */
    @Column(name = "requested_limit")
    private Integer requestedLimit;

    /** Whether the applicant accepted terms — a hard stop if false. */
    @Column(name = "terms_accepted")
    private Boolean termsAccepted;

    /** Identity document type: PASSPORT · NATIONAL_ID · DRIVING_LICENCE. */
    @Column(name = "document_type", length = 32)
    private String documentType;

    /**
     * Document expiry stored as a string, consistent with Application.java rule 1.
     * The module validates the date itself; the database just stores what was received.
     */
    @Column(name = "document_expiry", length = 32)
    private String documentExpiry;

    /** When this module answered. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VerificationCase() {
        // JPA
    }

    public VerificationCase(String applicationId, Decision status, String decisionReason,
                            String applicantName, String productCode, Integer requestedLimit,
                            Boolean termsAccepted, String documentType, String documentExpiry) {
        this.applicationId = applicationId;
        this.status = status.name();
        this.decisionReason = decisionReason;
        this.applicantName = applicantName;
        this.productCode = productCode;
        this.requestedLimit = requestedLimit;
        this.termsAccepted = termsAccepted;
        this.documentType = documentType;
        this.documentExpiry = documentExpiry;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public String getApplicationId() { return applicationId; }
    public String getStatus() { return status; }
    public String getDecisionReason() { return decisionReason; }
    public String getApplicantName() { return applicantName; }
    public String getProductCode() { return productCode; }
    public Integer getRequestedLimit() { return requestedLimit; }
    public Boolean getTermsAccepted() { return termsAccepted; }
    public String getDocumentType() { return documentType; }
    public String getDocumentExpiry() { return documentExpiry; }
    public Instant getCreatedAt() { return createdAt; }
}
