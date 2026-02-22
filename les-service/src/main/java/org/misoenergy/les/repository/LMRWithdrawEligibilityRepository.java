package org.misoenergy.les.repository;

import org.misoenergy.les.domain.LMRWithdrawEligibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LMRWithdrawEligibilityRepository extends JpaRepository<LMRWithdrawEligibility, Long> {

    Optional<LMRWithdrawEligibility> findByPlanningYearAndLmrId(String planningYear, String lmrId);
}
