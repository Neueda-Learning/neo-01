package com.neobank.module.repository;

import com.neobank.module.model.VerificationRecord;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationRecordRepository extends JpaRepository<VerificationRecord, String> {

    /** All decisions, newest first — what the operator UI reads. */
    List<VerificationRecord> findAllByOrderByCreatedAtDesc();

    /** Search by applicationId OR fullName (case-insensitive), newest first — UC-01. */
    @Query("SELECT v FROM VerificationRecord v WHERE "
            + "LOWER(v.applicationId) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(v.fullName) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "ORDER BY v.createdAt DESC")
    List<VerificationRecord> searchByIdOrName(@Param("q") String q, Pageable pageable);

    @Query("SELECT v FROM VerificationRecord v WHERE "
            + "v.outcome = :outcome "
            + "AND (LOWER(v.applicationId) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(v.fullName) LIKE LOWER(CONCAT('%', :q, '%'))) "
            + "ORDER BY v.createdAt DESC")
    List<VerificationRecord> searchByIdOrNameAndOutcome(@Param("q") String q, @Param("outcome") String outcome, Pageable pageable);

    List<VerificationRecord> findByOutcomeOrderByCreatedAtDesc(String outcome, Pageable pageable);

    /**
     * Return all records with non-null {@code ruleResults} within the half-open instant window
     * — UC-04. The full entity is returned so the service can derive {@code kind} from
     * {@code outcome} (REFERRED → review, REJECTED → failure) rather than from a hard-coded
     * code list.
     *
     * @param from inclusive start
     * @param to   exclusive end (caller adds one day to make the {@code to} date inclusive)
     */
    @Query("SELECT v FROM VerificationRecord v "
            + "WHERE v.createdAt >= :from AND v.createdAt < :to "
            + "AND v.ruleResults IS NOT NULL")
    List<VerificationRecord> findRecordsInWindow(@Param("from") Instant from, @Param("to") Instant to);

    long countByOutcome(String outcome);
}
