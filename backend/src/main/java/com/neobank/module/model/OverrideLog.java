package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Permanent audit record of every manual override performed on a verification decision.
 *
 * <p><b>Never delete rows.</b> Regulators and compliance teams may need the full history of
 * every outcome change, who made it, and why. One {@link VerificationRecord} may accumulate
 * many overrides (1:N).</p>
 */
@Entity
@Table(name = "override_log")
public class OverrideLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The application whose outcome was overridden — FK to {@code verification_record}. */
    @Column(name = "application_id", nullable = false, length = 64)
    private String applicationId;

    /** The outcome before the override. */
    @Column(name = "old_outcome", nullable = false, length = 32)
    private String oldOutcome;

    /** The outcome after the override. */
    @Column(name = "new_outcome", nullable = false, length = 32)
    private String newOutcome;

    /** Mandatory justification — must be something a regulator could read. */
    @Column(nullable = false, length = 512)
    private String reason;

    /** Staff id or display name of the person who performed the override. */
    @Column(nullable = false, length = 255)
    private String operator;

    /** When the override was recorded. */
    @Column(name = "overridden_at", nullable = false)
    private Instant overriddenAt;

    protected OverrideLog() {
        // JPA
    }

    public OverrideLog(String applicationId, Decision oldOutcome, Decision newOutcome,
                       String reason, String operator) {
        this.applicationId = applicationId;
        this.oldOutcome = oldOutcome.name();
        this.newOutcome = newOutcome.name();
        this.reason = reason;
        this.operator = operator;
        this.overriddenAt = Instant.now();
    }

    public Long getId()             { return id; }
    public String getApplicationId(){ return applicationId; }
    public String getOldOutcome()   { return oldOutcome; }
    public String getNewOutcome()   { return newOutcome; }
    public String getReason()       { return reason; }
    public String getOperator()     { return operator; }
    public Instant getOverriddenAt(){ return overriddenAt; }
}
