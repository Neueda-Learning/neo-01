package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The module's single decision record for one application.
 *
 * <p><b>Only {@code applicationId} is stored from the application payload.</b> No applicant name,
 * date of birth, address or any other personal data belongs in this table. The id is the only
 * handle back to the orchestrator's copy of the full form.</p>
 *
 * <p>One row per application — {@code applicationId} is the primary key, so a duplicate
 * {@code POST} will fail at the database rather than silently overwrite a decision.</p>
 */
@Entity
@Table(name = "verification_record")
public class VerificationRecord {

    /** The id from the request envelope — the one the orchestrator matches on. PK. */
    @Id
    @Column(name = "application_id", nullable = false, length = 64)
    private String applicationId;

    /** ACCEPTED · REJECTED · REFERRED, stored as text so the database is readable. */
    @Column(nullable = false, length = 32)
    private String outcome;

    /** Human-readable reason suitable for a bank employee to relay to a customer. */
    @Column(length = 512)
    private String reference;

    /**
     * FK to the {@code product_config} row whose rules were applied for this decision.
     * Null when no config exists for the submitted product code.
     */
    @Column(name = "product_config_id")
    private Long productConfigId;

    /**
     * JSON array of per-rule pass/fail results, e.g.
     * {@code [{"rule":"min_age","pass":true},{"rule":"limit_range","pass":false}]}.
     * Null until the module implements its rule engine.
     */
    @Lob
    @Column(name = "rule_results")
    private String ruleResults;

    /** When this module answered. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VerificationRecord() {
        // JPA
    }

    public VerificationRecord(String applicationId, Decision outcome, String reference,
                              Long productConfigId, String ruleResults) {
        this.applicationId = applicationId;
        this.outcome = outcome.name();
        this.reference = reference;
        this.productConfigId = productConfigId;
        this.ruleResults = ruleResults;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getApplicationId() { return applicationId; }
    public String getOutcome()       { return outcome; }
    public String getReference()     { return reference; }
    public Long getProductConfigId() { return productConfigId; }
    public String getRuleResults()   { return ruleResults; }
    public Instant getCreatedAt()    { return createdAt; }
}
