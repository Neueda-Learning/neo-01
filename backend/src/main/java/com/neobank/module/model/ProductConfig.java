package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * One immutable snapshot of the verification rules for one product at one version.
 *
 * <p><b>Insert-only.</b> Never UPDATE or DELETE a row — a historical decision's validity must
 * remain provable by re-reading the exact config version it was made against. When rules
 * change, add a new row with an incremented {@code version}. The highest version per
 * {@code productCode} is the one currently applied to new applications.</p>
 */
@Entity
@Table(name = "product_config",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_product_config_product_version",
               columnNames = {"product_code", "version"}))
public class ProductConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_code", nullable = false, length = 64)
    private String productCode;

    /** Monotonically increasing per product. The highest value is the current config. */
    @Column(nullable = false)
    private Integer version;

    /** Minimum applicant age in years. Null means no age restriction for this product. */
    @Column(name = "min_age")
    private Integer minAge;

    /** Minimum approved credit limit in whole GBP. */
    @Column(name = "limit_min")
    private Integer limitMin;

    /** Maximum approved credit limit in whole GBP. */
    @Column(name = "limit_max")
    private Integer limitMax;

    /**
     * Whether new applications may be submitted for this product.
     * A product with {@code active = false} is rejected immediately.
     */
    @Column(nullable = false)
    private Boolean active;

    /**
     * Comma-separated channel codes permitted for this product:
     * {@code WEB}, {@code MOBILE_APP}, {@code BRANCH}, {@code PHONE}.
     * Null means all channels are permitted.
     */
    @Column(length = 255)
    private String channels;

    /**
     * Comma-separated employment statuses permitted for this product:
     * {@code STUDENT}, {@code PERMANENT}, {@code SELF_EMPLOYED}, etc.
     * Null means all employment statuses are permitted.
     */
    @Column(name = "allowed_employment_statuses", length = 255)
    private String allowedEmploymentStatuses;

    /** When this version of the rules came into effect. */
    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    protected ProductConfig() {
        // JPA
    }

    public ProductConfig(String productCode, Integer version, Integer minAge,
                         Integer limitMin, Integer limitMax, Boolean active,
                         String channels, String allowedEmploymentStatuses, Instant effectiveFrom) {
        this.productCode = productCode;
        this.version = version;
        this.minAge = minAge;
        this.limitMin = limitMin;
        this.limitMax = limitMax;
        this.active = active;
        this.channels = channels;
        this.allowedEmploymentStatuses = allowedEmploymentStatuses;
        this.effectiveFrom = effectiveFrom;
    }

    public Long getId()            { return id; }
    public String getProductCode() { return productCode; }
    public Integer getVersion()    { return version; }
    public Integer getMinAge()     { return minAge; }
    public Integer getLimitMin()   { return limitMin; }
    public Integer getLimitMax()   { return limitMax; }
    public Boolean getActive()     { return active; }
    public String getChannels()    { return channels; }
    public String getAllowedEmploymentStatuses() { return allowedEmploymentStatuses; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
}
