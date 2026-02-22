package org.misoenergy.mect.repository;

import org.misoenergy.mect.domain.LMR;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LMRRepository extends JpaRepository<LMR, Long> {

    Optional<LMR> findByLmrIdAndPlanningYear(String lmrId, String planningYear);

    boolean existsByLmrIdAndPlanningYear(String lmrId, String planningYear);
}
