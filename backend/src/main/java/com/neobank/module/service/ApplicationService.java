package com.neobank.module.service;

import com.neobank.module.dto.VerificationRecordView;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.Decision;
import com.neobank.module.model.VerificationRecord;
import com.neobank.module.repository.VerificationRecordRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
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
 *   <li>{@link #processApplicationAsync} is called on the request thread.</li>
 *   <li>{@link #acceptRequest} inserts an {@code IN_PROGRESS} row <b>synchronously and
 *       durably</b> before this method returns — so the 202 is never sent before the row
 *       exists (AC-2).</li>
 *   <li>If the applicationId already has a row the request is acknowledged but not
 *       re-processed (AC-4 idempotency).</li>
 *   <li>The async decision worker {@link #processApplication} starts only after the row
 *       is committed (AC-6).</li>
 * </ol>
 */
@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final Executor executor;
    private final VerificationRecordRepository verificationRecords;
    private final OrchestratorClient orchestrator;

    public ApplicationService(@Qualifier("applicationTaskExecutor") Executor executor,
                              VerificationRecordRepository verificationRecords,
                              OrchestratorClient orchestrator) {
        this.executor = executor;
        this.verificationRecords = verificationRecords;
        this.orchestrator = orchestrator;
    }

    /**
     * Entry point from the controller.
     *
     * <p>Inserts the {@code IN_PROGRESS} row <b>synchronously</b> so the commit has
     * happened before the calling thread returns and the 202 is sent. The decision
     * worker is then submitted to the thread pool and runs independently.</p>
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
     * <p>Idempotent by applicationId:</p>
     * <ul>
     *   <li>No existing row — inserts IN_PROGRESS and returns {@code true}.</li>
     *   <li>Existing row still IN_PROGRESS — returns {@code false} (worker is already running).</li>
     *   <li>Existing row already decided — replays the stored callback async and returns
     *       {@code false} (no re-processing, AC-4).</li>
     * </ul>
     */
    boolean acceptRequest(ApplicationRequest request) {
        String id = request.applicationId();
        String fullName = extractFullName(request);
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
                id, Decision.IN_PROGRESS, "pending verification", null, null, fullName));
        log.info("Accepted — {}", request.summary());
        return true;
    }

    /**
     * The async decision worker — runs off the request thread after the IN_PROGRESS row
     * is committed.
     *
     * <p>Package-private so unit tests can call it directly on the test thread without
     * a thread pool. Always reports back to the orchestrator, even on failure, so the
     * journey never silently times out.</p>
     */
    void processApplication(ApplicationRequest request) {
        String applicationId = request.applicationId();
        String fullName = extractFullName(request);
        try {
            log.info("Deciding — {}", request.summary());

            // Replace the two lines below with your rules.
            Decision decision = Decision.ACCEPTED;
            String reference = "hello world from processApplication";

            verificationRecords.save(new VerificationRecord(
                    applicationId, decision, reference, null, null, fullName));
            orchestrator.applicationStatusUpdate(applicationId, decision, reference);
        } catch (RuntimeException e) {
            log.error("processApplication failed for {} — referring", applicationId, e);
            orchestrator.applicationStatusUpdate(applicationId, Decision.REFERRED,
                    "module error: " + e);
        }
    }

    /**
     * Extract fullName from the application request.
     * Returns null if the applicant or fullName is not present.
     */
    private String extractFullName(ApplicationRequest request) {
        if (request == null || request.application() == null) {
            return null;
        }
        Application.Applicant applicant = request.application().applicant();
        if (applicant == null || applicant.fullName() == null) {
            return null;
        }
        return applicant.fullName();
    }

    /** Everything this module has answered, newest first — what its own UI reads. */
    @Transactional(readOnly = true)
    public List<VerificationRecordView> findAll() {
        return verificationRecords.findAllByOrderByCreatedAtDesc().stream()
                .map(VerificationRecordView::of)
                .toList();
    }
}
