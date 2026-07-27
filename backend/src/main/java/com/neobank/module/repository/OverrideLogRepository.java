package com.neobank.module.repository;

import com.neobank.module.model.OverrideLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverrideLogRepository extends JpaRepository<OverrideLog, Long> {

    /** All overrides for one application, oldest first — for audit display. */
    List<OverrideLog> findByApplicationIdOrderByOverriddenAtAsc(String applicationId);
}
