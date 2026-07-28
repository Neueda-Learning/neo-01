package com.neobank.module.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.dto.VerificationRecordView;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.Decision;
import com.neobank.module.model.ProductConfig;
import com.neobank.module.model.VerificationRecord;
import com.neobank.module.repository.ProductConfigRepository;
import com.neobank.module.repository.VerificationRecordRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The module's business logic.
 *
 * <h3>UC-00 flow</h3>
 * <ol>
 * <li>{@link #processApplicationAsync} is called on the request thread.</li>
 * <li>{@link #acceptRequest} inserts an {@code IN_PROGRESS} row
 * <b>synchronously and
 * durably</b> before this method returns — so the 202 is never sent before the
 * row
 * exists (AC-2).</li>
 * <li>If the applicationId already has a row the request is acknowledged but
 * not
 * re-processed (AC-4 idempotency).</li>
 * <li>The async decision worker {@link #processApplication} starts only after
 * the row
 * is committed (AC-6).</li>
 * </ol>
 */
@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String RULE_WELL_FORMEDNESS = "wellFormedness";
    private static final String RULE_AGE = "age";
    private static final String RULE_PRODUCT_ACTIVE = "productActive";
    private static final String RULE_CHANNEL = "channel";

    private final Executor executor;
    private final VerificationRecordRepository verificationRecords;
    private final ProductConfigRepository productConfigs;
    private final OrchestratorClient orchestrator;

    public ApplicationService(@Qualifier("applicationTaskExecutor") Executor executor,
            VerificationRecordRepository verificationRecords,
            ProductConfigRepository productConfigs,
            OrchestratorClient orchestrator) {
        this.executor = executor;
        this.verificationRecords = verificationRecords;
        this.productConfigs = productConfigs;
        this.orchestrator = orchestrator;
    }

    /**
     * Entry point from the controller.
     *
     * <p>
     * Inserts the {@code IN_PROGRESS} row <b>synchronously</b> so the commit has
     * happened before the calling thread returns and the 202 is sent. The decision
     * worker is then submitted to the thread pool and runs independently.
     * </p>
     */
    public void processApplicationAsync(ApplicationRequest request) {
        boolean isNew = acceptRequest(request);
        if (isNew) {
            executor.execute(() -> processApplication(request));
        }
        // Duplicate applicationId: acknowledged, not re-queued.
    }

    /**
     * Inserts an {@code IN_PROGRESS} row for the given application.
     *
     * <p>
     * Idempotent by applicationId:
     * </p>
     * <ul>
     * <li>No existing row — inserts IN_PROGRESS and returns {@code true}.</li>
     * <li>Existing row still IN_PROGRESS — returns {@code false} (worker is already
     * running).</li>
     * <li>Existing row already decided — replays the stored callback async and
     * returns
     * {@code false} (no re-processing, AC-4).</li>
     * </ul>
     */
    boolean acceptRequest(ApplicationRequest request) {
        String id = request.applicationId();
        Optional<VerificationRecord> existing = verificationRecords.findById(id);
        if (existing.isPresent()) {
            VerificationRecord record = existing.get();
            if (!record.getOutcome().equals(Decision.IN_PROGRESS.name())) {
                // Already decided — replay the stored outcome as the callback (AC-4).
                log.info("Duplicate /execute for {} (decided: {}) — replaying callback",
                        id, record.getOutcome());
                executor.execute(() -> {
                    try {
                        orchestrator.applicationStatusUpdate(
                                id, Decision.valueOf(record.getOutcome()), record.getReference());
                    } catch (RuntimeException e) {
                        log.error("Callback replay failed for {}", id, e);
                    }
                });
            } else {
                log.info("Duplicate /execute for {} (still in-progress) — acknowledged", id);
            }
            return false;
        }
        verificationRecords.save(new VerificationRecord(
                id, Decision.IN_PROGRESS, "pending verification", null, null));
        log.info("Accepted — {}", request.summary());
        return true;
    }

    /**
     * The async decision worker — runs off the request thread after the IN_PROGRESS
     * row
     * is committed.
     *
     * <p>
     * Package-private so unit tests can call it directly on the test thread without
     * a thread pool. Always reports back to the orchestrator, even on failure, so
     * the
     * journey never silently times out.
     * </p>
     */
    void processApplication(ApplicationRequest request) {
        String applicationId = request.applicationId();
        try {
            log.info("Deciding — {}", request.summary());

            DecisionResult result = evaluate(request);
            verificationRecords.save(new VerificationRecord(
                    applicationId,
                    result.decision(),
                    result.reference(),
                    result.productConfigId(),
                    result.ruleResultsJson()));
            orchestrator.applicationStatusUpdate(applicationId, result.decision(), result.reference());
        } catch (RuntimeException e) {
            log.error("processApplication failed for {} — referring", applicationId, e);
            orchestrator.applicationStatusUpdate(applicationId, Decision.REFERRED,
                    "module error: " + e);
        }
    }

    /**
     * Everything this module has answered, newest first — what its own UI reads.
     */
    @Transactional(readOnly = true)
    public List<VerificationRecordView> findAll() {
        return verificationRecords.findAllByOrderByCreatedAtDesc().stream()
                .map(VerificationRecordView::of)
                .toList();
    }

    /** UC02: read one already-stored case result; never re-runs the engine. */
    @Transactional(readOnly = true)
    public CaseDetailView findCase(String applicationId) {
        VerificationRecord row = verificationRecords.findById(applicationId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Unknown applicationId: " + applicationId));

        Integer productConfigVersion = null;
        if (row.getProductConfigId() != null) {
            productConfigVersion = productConfigs.findById(row.getProductConfigId())
                    .map(ProductConfig::getVersion)
                    .orElse(null);
        }

        return new CaseDetailView(
                row.getOutcome(),
                row.getReference(),
                productConfigVersion,
                toRuleResultsJson(row.getRuleResults()));
    }

    private JsonNode toRuleResultsJson(String rawRuleResults) {
        if (rawRuleResults == null || rawRuleResults.isBlank()) {
            return JSON.createArrayNode();
        }
        try {
            return JSON.readTree(rawRuleResults);
        } catch (Exception e) {
            // Keep reads resilient for legacy rows that predate structured rule output.
            ArrayNode fallback = JSON.createArrayNode();
            fallback.add(rawRuleResults);
            return fallback;
        }
    }

    private DecisionResult evaluate(ApplicationRequest request) {
        Application app = request.application();
        ArrayNode ruleResults = JSON.createArrayNode();

        ProductConfig config = findCurrentConfig(app);
        Long productConfigId = config == null ? null : config.getId();

        List<String> wellFormedReasons = evaluateWellFormedness(app, config);
        ruleResults.add(ruleNode(RULE_WELL_FORMEDNESS, wellFormedReasons.isEmpty(),
                reasonsOrAllPassed(wellFormedReasons)));

        boolean hardFailure = !wellFormedReasons.isEmpty();
        boolean reviewFlag = false;

        AgeEvaluation ageEvaluation = evaluateAge(app, config);
        ruleResults.add(ruleNode(RULE_AGE, !ageEvaluation.hardFailure(),
                reasonsOrAllPassed(ageEvaluation.reasons())));
        hardFailure = hardFailure || ageEvaluation.hardFailure();
        reviewFlag = reviewFlag || ageEvaluation.reviewFlag();

        List<String> activeReasons = evaluateProductActive(config);
        ruleResults.add(ruleNode(RULE_PRODUCT_ACTIVE, activeReasons.isEmpty(),
                reasonsOrAllPassed(activeReasons)));
        hardFailure = hardFailure || !activeReasons.isEmpty();

        List<String> channelReasons = evaluateChannel(app, config);
        ruleResults.add(ruleNode(RULE_CHANNEL, channelReasons.isEmpty(),
                reasonsOrAllPassed(channelReasons)));
        hardFailure = hardFailure || !channelReasons.isEmpty();

        LimitEvaluation limitEvaluation = evaluateRequestedLimit(app, config);
        hardFailure = hardFailure || limitEvaluation.hardFailure();
        reviewFlag = reviewFlag || limitEvaluation.reviewFlag();
        if (!limitEvaluation.reasons().isEmpty()) {
            // Limit checks are part of the sweep (well-formedness) section in the UC02
            // response.
            JsonNode wellFormednessRule = ruleResults.get(0);
            ArrayNode reasonCodes = (ArrayNode) wellFormednessRule.get("reasonCodes");
            if (reasonCodes.size() == 1 && "VER_ALL_CHECKS_PASSED".equals(reasonCodes.get(0).asText())) {
                reasonCodes.removeAll();
            }
            for (String reason : limitEvaluation.reasons()) {
                reasonCodes.add(reason);
            }
            ((ObjectNode) wellFormednessRule).put("passed", false);
        }

        Decision decision;
        if (reviewFlag) {
            decision = Decision.REFERRED;
        } else if (hardFailure) {
            decision = Decision.REJECTED;
        } else {
            decision = Decision.ACCEPTED;
        }

        String reference = buildReference(decision, ruleResults);
        return new DecisionResult(decision, reference, productConfigId, ruleResults.toString());
    }

    private ProductConfig findCurrentConfig(Application app) {
        String productCode = app == null || app.product() == null ? null : app.product().productCode();
        if (!hasText(productCode)) {
            return null;
        }
        return productConfigs.findTopByProductCodeOrderByVersionDesc(productCode).orElse(null);
    }

    private List<String> evaluateWellFormedness(Application app, ProductConfig config) {
        List<String> reasons = new java.util.ArrayList<>();
        if (app == null) {
            reasons.add("VER_MISSING_FIELD:application");
            return reasons;
        }

        if (!hasText(app.channel())) {
            reasons.add("VER_MISSING_FIELD:channel");
        }

        if (app.applicant() == null) {
            reasons.add("VER_MISSING_FIELD:applicant");
        } else {
            if (!hasText(app.applicant().fullName())) {
                reasons.add("VER_MISSING_FIELD:applicant.fullName");
            }
            if (!hasText(app.applicant().dateOfBirth())) {
                reasons.add("VER_MISSING_FIELD:applicant.dateOfBirth");
            } else if (!isIsoDate(app.applicant().dateOfBirth())) {
                reasons.add("VER_INVALID_FIELD:applicant.dateOfBirth");
            }
        }

        if (app.product() == null) {
            reasons.add("VER_MISSING_FIELD:product");
        } else {
            if (!hasText(app.product().productCode())) {
                reasons.add("VER_MISSING_FIELD:product.productCode");
            }
            if (app.product().requestedCreditLimit() == null) {
                reasons.add("VER_MISSING_FIELD:product.requestedCreditLimit");
            }
        }

        if (app.consents() == null || app.consents().termsAccepted() == null) {
            reasons.add("VER_MISSING_FIELD:consents.termsAccepted");
        } else if (!app.consents().termsAccepted()) {
            reasons.add("VER_TERMS_NOT_ACCEPTED");
        }

        return reasons;
    }

    private LimitEvaluation evaluateRequestedLimit(Application app, ProductConfig config) {
        if (config == null || app == null || app.product() == null || app.product().requestedCreditLimit() == null) {
            return new LimitEvaluation(false, false, List.of());
        }
        Integer requestedLimit = app.product().requestedCreditLimit();
        Integer min = config.getLimitMin();
        Integer max = config.getLimitMax();

        if (min != null && requestedLimit < min) {
            return new LimitEvaluation(true, false, List.of("VER_LIMIT_BELOW_MINIMUM"));
        }
        if (max != null && requestedLimit > max) {
            return new LimitEvaluation(true, false, List.of("VER_LIMIT_ABOVE_MAXIMUM"));
        }
        if (max != null && requestedLimit.equals(max)) {
            return new LimitEvaluation(false, true, List.of("VER_LIMIT_EXACT_MAXIMUM"));
        }
        return new LimitEvaluation(false, false, List.of());
    }

    private AgeEvaluation evaluateAge(Application app, ProductConfig config) {
        if (config == null) {
            return new AgeEvaluation(true, false, List.of("VER_PRODUCT_NOT_FOUND"));
        }
        if (config.getMinAge() == null) {
            return new AgeEvaluation(false, false, List.of());
        }
        if (app == null || app.applicant() == null || !hasText(app.applicant().dateOfBirth())) {
            return new AgeEvaluation(true, false, List.of("VER_MISSING_FIELD:applicant.dateOfBirth"));
        }

        LocalDate dob;
        try {
            dob = LocalDate.parse(app.applicant().dateOfBirth());
        } catch (RuntimeException e) {
            return new AgeEvaluation(true, false, List.of("VER_INVALID_FIELD:applicant.dateOfBirth"));
        }

        int age = Period.between(dob, LocalDate.now(Clock.systemUTC())).getYears();
        if (age < config.getMinAge()) {
            return new AgeEvaluation(true, false, List.of("VER_AGE_BELOW_MINIMUM"));
        }
        if (age == config.getMinAge()) {
            return new AgeEvaluation(false, true, List.of("VER_AGE_EXACT_MINIMUM"));
        }
        return new AgeEvaluation(false, false, List.of());
    }

    private List<String> evaluateProductActive(ProductConfig config) {
        if (config == null) {
            return List.of("VER_PRODUCT_NOT_FOUND");
        }
        if (!Boolean.TRUE.equals(config.getActive())) {
            return List.of("VER_PRODUCT_INACTIVE");
        }
        return List.of();
    }

    private List<String> evaluateChannel(Application app, ProductConfig config) {
        if (config == null) {
            return List.of("VER_PRODUCT_NOT_FOUND");
        }
        if (!hasText(config.getChannels())) {
            return List.of();
        }
        if (app == null || !hasText(app.channel())) {
            return List.of("VER_MISSING_FIELD:channel");
        }

        Set<String> allowed = Stream.of(config.getChannels().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .collect(Collectors.toSet());

        if (!allowed.contains(app.channel().toUpperCase())) {
            return List.of("VER_CHANNEL_NOT_ELIGIBLE");
        }
        return List.of();
    }

    private ObjectNode ruleNode(String ruleName, boolean passed, List<String> reasonCodes) {
        ObjectNode node = JSON.createObjectNode();
        ArrayNode reasons = JSON.createArrayNode();
        for (String reasonCode : reasonCodes) {
            reasons.add(reasonCode);
        }
        node.put("ruleName", ruleName);
        node.put("passed", passed);
        node.set("reasonCodes", reasons);
        return node;
    }

    private List<String> reasonsOrAllPassed(List<String> reasons) {
        if (reasons.isEmpty()) {
            return List.of("VER_ALL_CHECKS_PASSED");
        }
        return reasons;
    }

    private String buildReference(Decision decision, ArrayNode ruleResults) {
        if (decision == Decision.ACCEPTED) {
            return "VER_ALL_CHECKS_PASSED";
        }
        for (JsonNode rule : ruleResults) {
            JsonNode reasonCodes = rule.get("reasonCodes");
            if (reasonCodes != null && reasonCodes.isArray() && reasonCodes.size() > 0) {
                String first = reasonCodes.get(0).asText();
                if (hasText(first) && !"VER_ALL_CHECKS_PASSED".equals(first)) {
                    return first;
                }
            }
        }
        return decision == Decision.REFERRED ? "VER_MANUAL_REVIEW_REQUIRED" : "VER_RULE_FAILED";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isIsoDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private record AgeEvaluation(boolean hardFailure, boolean reviewFlag, List<String> reasons) {
    }

    private record DecisionResult(
            Decision decision,
            String reference,
            Long productConfigId,
            String ruleResultsJson) {
    }

    private record LimitEvaluation(boolean hardFailure, boolean reviewFlag, List<String> reasons) {
    }
}
