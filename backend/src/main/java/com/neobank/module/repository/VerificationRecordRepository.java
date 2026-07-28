package com.neobank.module.repository;

import com.neobank.module.model.VerificationRecord;
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
}
