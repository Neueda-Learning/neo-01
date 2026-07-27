package com.neobank.module.repository;

import com.neobank.module.model.VerificationRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationRecordRepository extends JpaRepository<VerificationRecord, String> {

    /** All decisions, newest first — what the operator UI reads. */
    List<VerificationRecord> findAllByOrderByCreatedAtDesc();
}
