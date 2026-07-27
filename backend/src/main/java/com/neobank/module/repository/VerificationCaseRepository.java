package com.neobank.module.repository;

import com.neobank.module.model.VerificationCase;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationCaseRepository extends JpaRepository<VerificationCase, Long> {

    /** All cases, newest first — what the operator UI reads. */
    List<VerificationCase> findAllByOrderByCreatedAtDescIdDesc();
}
